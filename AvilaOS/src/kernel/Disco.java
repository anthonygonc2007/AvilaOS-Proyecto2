/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;
import interfaces.IClockObserver;
import interfaces.IPoliticaDisco;

public class Disco implements IClockObserver {

    private final Bloque[] bloques;
    private final int totalBloques;
    private final int swapSizeBlocks;
    private final int limiteArchivos; // [0 .. limiteArchivos - 1] = Archivos | [limiteArchivos .. totalBloques - 1] = Swap

    private int cabezalActual;
    private boolean haciaArriba;
    private int velocidadCabezal;
    private IPoliticaDisco politica;

    private final ListaEnlazada<SolicitudIO> colaES;
    private final ListaEnlazada<SolicitudIO> solicitudesCompletadas;
    private final GestorConcurrencia gestorConcurrencia;

    private SolicitudIO solicitudActiva;
    private boolean enTransferencia;
    private int ticksTransferenciaRestantes;
    private int objetivoExtremo; // -1 si no hay recorrido obligatorio hacia extremo (199 o 0)
    private long esperaSolicitudActiva;

    // --- Métricas globales y por política (0: FIFO, 1: SSTF, 2: SCAN, 3: C-SCAN) ---
    private int desplazamientoTotal;
    private int totalSolicitudesAtendidas;
    private long tiempoEsperaTotal;
    private long tiempoRespuestaTotal;

    private final int[] pistasPorPolitica;
    private final int[] atendidasPorPolitica;
    private final long[] esperaPorPolitica;

    public Disco(int totalBloques, int swapSizeBlocks, int velocidadCabezal, int cabezalInicial, IPoliticaDisco politica) {
        this.totalBloques = Math.max(1, totalBloques);
        this.swapSizeBlocks = Math.max(0, Math.min(swapSizeBlocks, this.totalBloques - 1));
        this.limiteArchivos = this.totalBloques - this.swapSizeBlocks;

        this.bloques = new Bloque[this.totalBloques];
        for (int i = 0; i < this.totalBloques; i++) {
            this.bloques[i] = new Bloque(i);
        }

        this.cabezalActual = Math.max(0, Math.min(cabezalInicial, this.totalBloques - 1));
        this.haciaArriba = true;
        this.velocidadCabezal = Math.max(1, velocidadCabezal);
        this.politica = (politica != null) ? politica : new PoliticaDiscoFIFO();

        this.colaES = new ListaEnlazada<>();
        this.solicitudesCompletadas = new ListaEnlazada<>();
        this.gestorConcurrencia = new GestorConcurrencia();
        this.solicitudActiva = null;
        this.enTransferencia = false;
        this.ticksTransferenciaRestantes = 0;
        this.objetivoExtremo = -1;

        this.desplazamientoTotal = 0;
        this.totalSolicitudesAtendidas = 0;
        this.tiempoEsperaTotal = 0L;
        this.tiempoRespuestaTotal = 0L;

        this.pistasPorPolitica = new int[4];
        this.atendidasPorPolitica = new int[4];
        this.esperaPorPolitica = new long[4];
    }

    @Override
    public synchronized void tick(int cicloActual) {
        int idxPolitica = obtenerIndicePoliticaActual();

        // 1. Si no hay solicitud activa, seleccionar la siguiente según la política con exclusión mutua
        if (solicitudActiva == null) {
            gestorConcurrencia.entrarColaES(null);
            try {
                if (colaES.esVacia()) {
                    return;
                }
                solicitudActiva = politica.seleccionarSiguiente(colaES, cabezalActual, haciaArriba);
                if (solicitudActiva == null) {
                    return;
                }
                colaES.eliminar(solicitudActiva);
            } finally {
                gestorConcurrencia.salirColaES(null);
            }

            // Tiempo que esperó en la cola de E/S desde cicloLlegada hasta que empezó a atenderse
esperaSolicitudActiva = Math.max(0, cicloActual - solicitudActiva.getCicloLlegada());
            tiempoEsperaTotal += esperaSolicitudActiva;
            esperaPorPolitica[idxPolitica] += esperaSolicitudActiva;

            ticksTransferenciaRestantes = Math.max(1, solicitudActiva.getCantidadBloques());
            objetivoExtremo = calcularObjetivoExtremo(idxPolitica, solicitudActiva.getBloqueObjetivo());
            enTransferencia = (objetivoExtremo == -1 && cabezalActual == solicitudActiva.getBloqueObjetivo());
        } else if (!enTransferencia && objetivoExtremo == -1 && idxPolitica != 0) {
            // Reevaluar en SSTF, SCAN y C-SCAN si la CPU encoló una petición mejor mientras el cabezal viaja
            gestorConcurrencia.entrarColaES(null);
            try {
                if (!colaES.esVacia()) {
                    colaES.agregarAlFinal(solicitudActiva);
                    SolicitudIO mejor = politica.seleccionarSiguiente(colaES, cabezalActual, haciaArriba);
                    if (mejor != null && mejor != solicitudActiva) {
                        colaES.eliminar(mejor);
                        tiempoEsperaTotal = Math.max(0L, tiempoEsperaTotal - esperaSolicitudActiva);
                        esperaPorPolitica[idxPolitica] = Math.max(0L, esperaPorPolitica[idxPolitica] - esperaSolicitudActiva);

                        solicitudActiva = mejor;
                        esperaSolicitudActiva = Math.max(0, cicloActual - solicitudActiva.getCicloLlegada());
                        tiempoEsperaTotal += esperaSolicitudActiva;
                        esperaPorPolitica[idxPolitica] += esperaSolicitudActiva;

                        ticksTransferenciaRestantes = Math.max(1, solicitudActiva.getCantidadBloques());
                        objetivoExtremo = calcularObjetivoExtremo(idxPolitica, solicitudActiva.getBloqueObjetivo());
                        enTransferencia = (objetivoExtremo == -1 && cabezalActual == solicitudActiva.getBloqueObjetivo());
                    } else {
                        colaES.eliminar(solicitudActiva);
                    }
                }
            } finally {
                gestorConcurrencia.salirColaES(null);
            }
        }

        // 2. Fase 1: Desplazamiento del cabezal
        if (!enTransferencia) {
            if (objetivoExtremo != -1) {
                int distExtremo = Math.abs(objetivoExtremo - cabezalActual);
                int pasoExtremo = Math.min(distExtremo, velocidadCabezal);

                if (objetivoExtremo > cabezalActual) {
                    cabezalActual += pasoExtremo;
                    haciaArriba = true;
                } else if (objetivoExtremo < cabezalActual) {
                    cabezalActual -= pasoExtremo;
                    haciaArriba = false;
                }

                desplazamientoTotal += pasoExtremo;
                pistasPorPolitica[idxPolitica] += pasoExtremo;

                if (cabezalActual == objetivoExtremo) {
                    if (idxPolitica == 3 && objetivoExtremo > 0) {
                        // C-SCAN: llega a 199 y salta a 0 sin sumar pistas por el salto de regreso
                        cabezalActual = 0;
                        haciaArriba = true;
                    } else if (idxPolitica == 2) {
                        // SCAN: llega a 199 (o 0) e invierte sentido
                        haciaArriba = (objetivoExtremo == 0);
                    }
                    objetivoExtremo = -1;
                    if (cabezalActual == solicitudActiva.getBloqueObjetivo()) {
                        enTransferencia = true;
                    }
                }
                return;
            }
            
            int objetivo = solicitudActiva.getBloqueObjetivo();
            int distancia = Math.abs(objetivo - cabezalActual);
            int paso = Math.min(distancia, velocidadCabezal);

            if (objetivo > cabezalActual) {
                cabezalActual += paso;
                haciaArriba = true;
            } else if (objetivo < cabezalActual) {
                cabezalActual -= paso;
                haciaArriba = false;
            }

            desplazamientoTotal += paso;
            pistasPorPolitica[idxPolitica] += paso;

            // Al llegar al bloque objetivo, pasa a fase de transferencia para el siguiente tick
            if (cabezalActual == objetivo) {
                enTransferencia = true;
            }
            return;
        }

        // 3. Fase 2: Transferencia (1 tick por bloque)
        ticksTransferenciaRestantes--;
        if (ticksTransferenciaRestantes <= 0) {
            long respuesta = Math.max(0, (cicloActual + 1) - solicitudActiva.getCicloLlegada());
            tiempoRespuestaTotal += respuesta;
            totalSolicitudesAtendidas++;
            atendidasPorPolitica[idxPolitica]++;

            solicitudesCompletadas.agregarAlFinal(solicitudActiva);
            solicitudActiva = null;
            enTransferencia = false;
            objetivoExtremo = -1;
            esperaSolicitudActiva = 0L;
        }
    }
    
    private int calcularLimiteSuperiorBarrido() {
        int limiteSuperior = (limiteArchivos > 0) ? (limiteArchivos - 1) : (totalBloques - 1);
        if (solicitudActiva != null && solicitudActiva.getBloqueObjetivo() >= limiteArchivos) {
            return totalBloques - 1;
        }
        for (int i = 0; i < colaES.getTamano(); i++) {
            SolicitudIO s = colaES.obtener(i);
            if (s != null && s.getBloqueObjetivo() >= limiteArchivos) {
                return totalBloques - 1;
            }
        }
        return limiteSuperior;
    }

    private int calcularObjetivoExtremo(int idxPolitica, int objetivo) {
        int limiteSuperior = calcularLimiteSuperiorBarrido();
        if (idxPolitica == 2) { // SCAN
            if (haciaArriba && objetivo < cabezalActual) {
                return limiteSuperior;
            }
            if (!haciaArriba && objetivo > cabezalActual) {
                return 0;
            }
        } else if (idxPolitica == 3) { // C-SCAN
            if (objetivo < cabezalActual) {
                if (cabezalActual == limiteSuperior) {
                    cabezalActual = 0;
                    haciaArriba = true;
                    return -1;
                }
                return limiteSuperior;
            }
        }
        return -1;
    }

    private int obtenerIndicePoliticaActual() {
        if (politica == null) return 0;
        String nombre = politica.getClass().getSimpleName().toUpperCase();
        if (nombre.contains("SSTF")) return 1;
        if (nombre.contains("CSCAN")) return 3;
        if (nombre.contains("SCAN")) return 2;
        return 0; // FIFO por defecto
    }

    private int obtenerIndicePorNombre(String nombrePolitica) {
        if (nombrePolitica == null) return 0;
        String upper = nombrePolitica.trim().toUpperCase();
        if (upper.contains("SSTF")) return 1;
        if (upper.contains("C-SCAN") || upper.contains("CSCAN")) return 3;
        if (upper.contains("SCAN")) return 2;
        return 0;
    }

    // --- Gestión concurrente de cola de E/S ---

    public synchronized void agregarSolicitud(SolicitudIO solicitud) {
        if (solicitud == null) return;
        gestorConcurrencia.entrarColaES(null);
        try {
            colaES.agregarAlFinal(solicitud);
        } finally {
            gestorConcurrencia.salirColaES(null);
        }
    }

    public synchronized SolicitudIO extraerSolicitudCompletada() {
        if (solicitudesCompletadas.esVacia()) {
            return null;
        }
        SolicitudIO completada = solicitudesCompletadas.obtener(0);
        solicitudesCompletadas.eliminar(completada);
        return completada;
    }

    // --- Escritura y Liberación exclusiva en Zona Reservada de Swap ---

    public synchronized int escribirProcesoEnSwap(String idProceso, int cantidadBloques) {
        int requeridos = Math.max(1, cantidadBloques);
        if (idProceso == null || contarBloquesLibresSwap() < requeridos) {
            return -1;
        }

        ListaEnlazada<Bloque> asignados = new ListaEnlazada<>();
        for (int i = 0; i < requeridos; i++) {
            int idLibre = buscarBloqueLibreSwap();
            if (idLibre == -1 || !esBloqueDeSwap(idLibre)) {
                for (int j = 0; j < asignados.getTamano(); j++) {
                    liberarBloqueSwap(asignados.obtener(j).getId());
                }
                return -1;
            }
            Bloque b = bloques[idLibre];
            b.setOcupado(true);
            b.setContenido("SWAP-" + idProceso);
            b.setColorHex("#E74C3C");
            if (!asignados.esVacia()) {
                asignados.obtener(asignados.getTamano() - 1).setSiguienteBloque(b.getId());
            }
            b.setSiguienteBloque(-1);
            asignados.agregarAlFinal(b);
        }
        return asignados.obtener(0).getId();
    }

    public synchronized int liberarProcesoDeSwap(String idProceso) {
        if (idProceso == null) return 0;
        String marca = "SWAP-" + idProceso;
        int liberados = 0;
        for (int i = limiteArchivos; i < totalBloques; i++) {
            if (bloques[i].isOcupado() && marca.equals(bloques[i].getContenido())) {
                bloques[i].limpiar();
                liberados++;
            }
        }
        return liberados;
    }

    public synchronized boolean liberarBloqueSwap(int idBloque) {
        if (!esBloqueDeSwap(idBloque)) {
            return false;
        }
        bloques[idBloque].limpiar();
        return true;
    }

    // --- Partición física estricta (Archivos vs Swap) ---

    public boolean esBloqueDeArchivos(int idBloque) {
        return idBloque >= 0 && idBloque < limiteArchivos;
    }

    public boolean esBloqueDeSwap(int idBloque) {
        return idBloque >= limiteArchivos && idBloque < totalBloques;
    }

    public int buscarBloqueLibreArchivos() {
        for (int i = 0; i < limiteArchivos; i++) {
            if (!bloques[i].isOcupado()) {
                return i;
            }
        }
        return -1;
    }

    public int buscarBloqueLibreSwap() {
        for (int i = limiteArchivos; i < totalBloques; i++) {
            if (!bloques[i].isOcupado()) {
                return i;
            }
        }
        return -1;
    }

    public int contarBloquesLibresArchivos() {
        int libres = 0;
        for (int i = 0; i < limiteArchivos; i++) {
            if (!bloques[i].isOcupado()) {
                libres++;
            }
        }
        return libres;
    }

    public int contarBloquesLibresSwap() {
        int libres = 0;
        for (int i = limiteArchivos; i < totalBloques; i++) {
            if (!bloques[i].isOcupado()) {
                libres++;
            }
        }
        return libres;
    }

    public Bloque getBloque(int idBloque) {
        if (idBloque < 0 || idBloque >= totalBloques) {
            return null;
        }
        return bloques[idBloque];
    }

    public synchronized void liberarBloque(int idBloque) {
        if (idBloque >= 0 && idBloque < totalBloques) {
            bloques[idBloque].limpiar();
        }
    }

    // --- Getters de Métricas de Rendimiento (Listos para JFreeChart) ---

    public synchronized int getDesplazamientoTotal() {
        return desplazamientoTotal;
    }

    public synchronized int getTotalPistasRecorridas() {
        return desplazamientoTotal;
    }

    public synchronized int getTotalSolicitudesAtendidas() {
        return totalSolicitudesAtendidas;
    }

    public synchronized double getTiempoPromedioEsperaES() {
        int consideradas = totalSolicitudesAtendidas + (solicitudActiva != null ? 1 : 0);
        if (consideradas == 0) {
            return 0.0;
        }
        return (double) tiempoEsperaTotal / consideradas;
    }

    public synchronized double getTiempoPromedioRespuestaES() {
        if (totalSolicitudesAtendidas == 0) {
            return 0.0;
        }
        return (double) tiempoRespuestaTotal / totalSolicitudesAtendidas;
    }

    public synchronized int getDesplazamientoPorPolitica(String nombrePolitica) {
        return pistasPorPolitica[obtenerIndicePorNombre(nombrePolitica)];
    }

    public synchronized int getSolicitudesAtendidasPorPolitica(String nombrePolitica) {
        return atendidasPorPolitica[obtenerIndicePorNombre(nombrePolitica)];
    }

    public synchronized double getEsperaPromedioPorPolitica(String nombrePolitica) {
        int idx = obtenerIndicePorNombre(nombrePolitica);
        if (atendidasPorPolitica[idx] == 0) {
            return 0.0;
        }
        return (double) esperaPorPolitica[idx] / atendidasPorPolitica[idx];
    }

    public synchronized void reiniciarMetricas() {
        desplazamientoTotal = 0;
        totalSolicitudesAtendidas = 0;
        tiempoEsperaTotal = 0L;
        tiempoRespuestaTotal = 0L;
        for (int i = 0; i < 4; i++) {
            pistasPorPolitica[i] = 0;
            atendidasPorPolitica[i] = 0;
            esperaPorPolitica[i] = 0L;
        }
    }

    // --- Getters y Setters ---

    public Bloque[] getBloques() { return bloques; }
    public int getTotalBloques() { return totalBloques; }
    public int getSwapSizeBlocks() { return swapSizeBlocks; }
    public int getLimiteArchivos() { return limiteArchivos; }
    public int getCabezalActual() { return cabezalActual; }
    public boolean isHaciaArriba() { return haciaArriba; }
    public int getVelocidadCabezal() { return velocidadCabezal; }
    public void setVelocidadCabezal(int velocidadCabezal) { this.velocidadCabezal = Math.max(1, velocidadCabezal); }
    public IPoliticaDisco getPolitica() { return politica; }
    public void setPolitica(IPoliticaDisco politica) { if (politica != null) this.politica = politica; this.objetivoExtremo = -1; }
    public ListaEnlazada<SolicitudIO> getColaES() { return colaES; }
    public SolicitudIO getSolicitudActiva() { return solicitudActiva; }
    public boolean isEnTransferencia() { return enTransferencia; }
    public GestorConcurrencia getGestorConcurrencia() { return gestorConcurrencia; }
}