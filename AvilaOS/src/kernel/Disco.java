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

    private SolicitudIO solicitudActiva;
    private boolean enTransferencia;
    private int ticksTransferenciaRestantes;
    private int desplazamientoTotal;

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
        this.solicitudActiva = null;
        this.enTransferencia = false;
        this.ticksTransferenciaRestantes = 0;
        this.desplazamientoTotal = 0;
    }

    @Override
    public void tick(int cicloActual) {
        // 1. Si no hay solicitud activa, seleccionar la siguiente según la política
        if (solicitudActiva == null) {
            if (colaES.esVacia()) {
                return;
            }
            solicitudActiva = politica.seleccionarSiguiente(colaES, cabezalActual, haciaArriba);
            if (solicitudActiva == null) {
                return;
            }
            colaES.eliminar(solicitudActiva);
            ticksTransferenciaRestantes = Math.max(1, solicitudActiva.getCantidadBloques());
            enTransferencia = (cabezalActual == solicitudActiva.getBloqueObjetivo());
        }

        // 2. Fase 1: Desplazamiento del cabezal
        if (!enTransferencia) {
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

            // Al llegar al bloque objetivo, pasa a fase de transferencia para el siguiente tick
            if (cabezalActual == objetivo) {
                enTransferencia = true;
            }
            return;
        }

        // 3. Fase 2: Transferencia (1 tick por bloque)
        ticksTransferenciaRestantes--;
        if (ticksTransferenciaRestantes <= 0) {
            solicitudesCompletadas.agregarAlFinal(solicitudActiva);
            solicitudActiva = null;
            enTransferencia = false;
        }
    }

    // --- Gestión de cola de E/S ---

    public void agregarSolicitud(SolicitudIO solicitud) {
        if (solicitud != null) {
            colaES.agregarAlFinal(solicitud);
        }
    }

    public SolicitudIO extraerSolicitudCompletada() {
        if (solicitudesCompletadas.esVacia()) {
            return null;
        }
        SolicitudIO completada = solicitudesCompletadas.obtener(0);
        solicitudesCompletadas.eliminar(completada);
        return completada;
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

    public void liberarBloque(int idBloque) {
        if (idBloque >= 0 && idBloque < totalBloques) {
            bloques[idBloque].limpiar();
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
    public void setPolitica(IPoliticaDisco politica) { if (politica != null) this.politica = politica; }
    public ListaEnlazada<SolicitudIO> getColaES() { return colaES; }
    public SolicitudIO getSolicitudActiva() { return solicitudActiva; }
    public boolean isEnTransferencia() { return enTransferencia; }
    public int getDesplazamientoTotal() { return desplazamientoTotal; }
}