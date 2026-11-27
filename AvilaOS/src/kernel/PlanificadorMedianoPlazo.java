package kernel;

import enums.EstadoProceso;
import enums.TipoOperacionIO;
import estructuras.Cola;
import estructuras.ListaEnlazada;

public class PlanificadorMedianoPlazo {

    public static class RegistroSwap {
        public final String idProceso;
        public final int bloqueInicioSwap;
        public final ListaEnlazada<Bloque> bloquesSwap;
        public RegistroSwap(String id, int inicio, ListaEnlazada<Bloque> bloques) {
            this.idProceso = id;
            this.bloqueInicioSwap = inicio;
            this.bloquesSwap = bloques;
        }
    }

    private final ListaEnlazada<RegistroSwap> registrosSwap;
    private int totalSuspensiones;
    private int totalReactivaciones;
    private int memoriaReservadaLiberacion;    // RAM que liberarán swap-outs en vuelo
    private int memoriaReservadaReactivacion;  // RAM que ocuparán swap-ins en vuelo

    public PlanificadorMedianoPlazo() {
        this.registrosSwap = new ListaEnlazada<>();
        this.totalSuspensiones = 0;
        this.totalReactivaciones = 0;
        this.memoriaReservadaLiberacion = 0;
        this.memoriaReservadaReactivacion = 0;
    }

    /** Evalúa si la cola de nuevos puede admitirse; suspende como máximo UNA víctima por ciclo. */
    public synchronized void gestionarAdmisionYSuspension(Computador computador, Disco disco, int cicloActual) {
        if (computador == null || disco == null) return;
        Cola<Pcb> colaNuevos = computador.getColaNuevos();
        MemoriaRAM ram = computador.getMemoriaRAM();

        while (!colaNuevos.esVacia()) {
            Pcb candidato = colaNuevos.verFrente();
            if (candidato == null) break;

            int ramDisponibleProyectada = ram.getMemoriaLibre() + memoriaReservadaLiberacion;
            if (ramDisponibleProyectada >= candidato.getMemoriaAsignada()) {
                if (ram.getMemoriaLibre() >= candidato.getMemoriaAsignada()) {
                    computador.intentarAdmitirNuevos();
                }
                break;
            }
            if (candidato.getMemoriaAsignada() > ram.getCapacidadTotal()) break;

            // Solo una suspensión por ciclo: el resto se reevalúa cuando el disco complete
            if (!suspenderVictima(computador, disco, cicloActual)) break;
            break;
        }
        computador.intentarAdmitirNuevos();
    }

    /** Reserva swap y emite SolicitudIO(SUSPENDER). No libera RAM todavía. */
    public synchronized boolean suspenderVictima(Computador computador, Disco disco, int cicloActual) {
        // PDF: elegir un proceso BLOQUEADO. No se permite caer a colaListos.
        Pcb victima = extraerVictimaDeCola(computador.getColaBloqueados(), disco.contarBloquesLibresSwap());
        if (victima == null) return false;

        int bloquesNecesarios = Math.max(1, victima.getMemoriaAsignada());
        ListaEnlazada<Bloque> cadenaSwap = asignarBloquesSwap(disco, victima.getId(), bloquesNecesarios);
        if (cadenaSwap == null || cadenaSwap.esVacia()) {
            computador.getColaBloqueados().encolar(victima);
            return false;
        }

        int bloqueInicio = cadenaSwap.obtener(0).getId();
        victima.setEstado(EstadoProceso.BLOQUEADO_SUSPENDIDO);
        victima.setSwapOutPendiente(true);
        victima.setMotivoBloqueo("SWAP_OUT_PENDIENTE");
        computador.getColaBloqueadosSuspendidos().encolar(victima);

        registrosSwap.agregarAlFinal(new RegistroSwap(victima.getId(), bloqueInicio, cadenaSwap));
        memoriaReservadaLiberacion += victima.getMemoriaAsignada();
        totalSuspensiones++;

        disco.agregarSolicitud(new SolicitudIO(
            victima.getId(), TipoOperacionIO.SUSPENDER,
            "SWAP:" + victima.getId(), bloqueInicio, bloquesNecesarios, cicloActual
        ));
        return true;
    }

    /** Invocado por Computador cuando el disco completa una SolicitudIO(SUSPENDER). */
    public synchronized void completarSuspension(String idProceso, Computador computador) {
        if (computador == null) return;
        Pcb p = buscarPcbEnColasSuspendidas(idProceso, computador);
        if (p == null) return;

        if (p.isSwapOutPendiente()) {
            memoriaReservadaLiberacion = Math.max(0, memoriaReservadaLiberacion - p.getMemoriaAsignada());
            computador.getMemoriaRAM().liberarProceso(p);
            p.setSwapOutPendiente(false);
            if (p.getEstado() != EstadoProceso.EXIT
                && !"DEADLINE_EXPIRADO".equals(p.getMotivoBloqueo())) {
                p.setMotivoBloqueo("");
            }
        }
    }

    /** Solicita swap-in de procesos Listo/Suspendido si la RAM lo permite (asíncrono). */
    public synchronized int intentarReactivarSuspendidos(Computador computador, Disco disco, int cicloActual) {
        if (computador == null || disco == null) return 0;
        Cola<Pcb> colaListosSusp = computador.getColaListosSuspendidos();
        if (colaListosSusp.esVacia()) return 0;

        int emitidas = 0;
        Cola<Pcb> aux = new Cola<>();
        while (!colaListosSusp.esVacia()) {
            Pcb pcb = colaListosSusp.desencolar();
            boolean puede = !pcb.isReactivacionPendiente()
                         && !pcb.isSwapOutPendiente()
                         && buscarRegistroSwap(pcb.getId()) != null
                         && (computador.getMemoriaRAM().getMemoriaLibre() - memoriaReservadaReactivacion)
                             >= pcb.getMemoriaAsignada();
            if (puede) {
                RegistroSwap reg = buscarRegistroSwap(pcb.getId());
                pcb.setReactivacionPendiente(true);
                memoriaReservadaReactivacion += pcb.getMemoriaAsignada();
                disco.agregarSolicitud(new SolicitudIO(
                    pcb.getId(), TipoOperacionIO.REACTIVAR,
                    "SWAP:" + pcb.getId(), reg.bloqueInicioSwap,
                    Math.max(1, pcb.getMemoriaAsignada()), cicloActual
                ));
                emitidas++;
            }
            aux.encolar(pcb);
        }
        while (!aux.esVacia()) colaListosSusp.encolar(aux.desencolar());
        return emitidas;
    }

    /** Invocado por Computador cuando el disco completa una SolicitudIO(REACTIVAR). */
    public synchronized void completarReactivacion(String idProceso, Computador computador, Disco disco) {
        if (computador == null || disco == null) return;
        RegistroSwap reg = extraerRegistroSwap(idProceso);
        if (reg == null) return;

        Pcb encontrado = extraerDeColaPorId(computador.getColaListosSuspendidos(), idProceso);
        if (encontrado == null) {
            liberarBloquesSwap(disco, reg);
            return;
        }

        memoriaReservadaReactivacion = Math.max(0, memoriaReservadaReactivacion - encontrado.getMemoriaAsignada());

        if (computador.getMemoriaRAM().cargarProceso(encontrado)) {
            liberarBloquesSwap(disco, reg);
            encontrado.setReactivacionPendiente(false);
            encontrado.setMotivoBloqueo("");
            encontrado.setEstado(EstadoProceso.READY);
            computador.getColaListos().encolar(encontrado);
            totalReactivaciones++;
        } else {
            // RAM insuficiente en el momento de completar (raro): devolver a suspendidos
            encontrado.setReactivacionPendiente(false);
            registrosSwap.agregarAlFinal(reg);
            computador.getColaListosSuspendidos().encolar(encontrado);
        }
    }

    /** Llamado desde Computador en rutas de terminación para limpiar swap pendiente. */
    public synchronized void cancelarSwapDeProceso(String idProceso, Pcb pcbLocal,
                                                   Computador computador, Disco disco) {
        RegistroSwap reg = extraerRegistroSwap(idProceso);
        if (reg != null && disco != null) liberarBloquesSwap(disco, reg);

        Pcb objetivo = (pcbLocal != null) ? pcbLocal
                      : (computador != null ? buscarPcbEnColasSuspendidas(idProceso, computador) : null);
        if (objetivo != null && objetivo.isSwapOutPendiente()) {
            memoriaReservadaLiberacion = Math.max(0, memoriaReservadaLiberacion - objetivo.getMemoriaAsignada());
            if (computador != null) computador.getMemoriaRAM().liberarProceso(objetivo);
            objetivo.setSwapOutPendiente(false);
        }
        if (objetivo != null && objetivo.isReactivacionPendiente()) {
            memoriaReservadaReactivacion = Math.max(0, memoriaReservadaReactivacion - objetivo.getMemoriaAsignada());
            objetivo.setReactivacionPendiente(false);
        }
    }

    // ---------- Helpers ----------

    private Pcb buscarPcbEnColasSuspendidas(String id, Computador c) {
        if (c == null || id == null) return null;
        Pcb p = buscarEnCola(c.getColaBloqueadosSuspendidos(), id);
        if (p != null) return p;
        return buscarEnCola(c.getColaListosSuspendidos(), id);
    }

    private Pcb buscarEnCola(Cola<Pcb> cola, String id) {
        Cola<Pcb> aux = new Cola<>();
        Pcb encontrado = null;
        while (!cola.esVacia()) {
            Pcb p = cola.desencolar();
            if (encontrado == null && p.getId().equals(id)) encontrado = p;
            aux.encolar(p);
        }
        while (!aux.esVacia()) cola.encolar(aux.desencolar());
        return encontrado;
    }

    private Pcb extraerDeColaPorId(Cola<Pcb> cola, String id) {
        Cola<Pcb> aux = new Cola<>();
        Pcb encontrado = null;
        while (!cola.esVacia()) {
            Pcb p = cola.desencolar();
            if (encontrado == null && p.getId().equals(id)) encontrado = p;
            else aux.encolar(p);
        }
        while (!aux.esVacia()) cola.encolar(aux.desencolar());
        return encontrado;
    }

    private RegistroSwap buscarRegistroSwap(String id) {
        for (int i = 0; i < registrosSwap.getTamano(); i++) {
            if (registrosSwap.obtener(i).idProceso.equals(id)) return registrosSwap.obtener(i);
        }
        return null;
    }

    private RegistroSwap extraerRegistroSwap(String id) {
        for (int i = 0; i < registrosSwap.getTamano(); i++) {
            RegistroSwap r = registrosSwap.obtener(i);
            if (r.idProceso.equals(id)) { registrosSwap.eliminar(r); return r; }
        }
        return null;
    }

    private Pcb extraerVictimaDeCola(Cola<Pcb> cola, int bloquesLibresSwap) {
        if (cola == null || cola.esVacia()) return null;
        Pcb mejor = null;
        Cola<Pcb> aux = new Cola<>();
        while (!cola.esVacia()) {
            Pcb actual = cola.desencolar();
            int memReq = Math.max(1, actual.getMemoriaAsignada());
            if (memReq <= bloquesLibresSwap && !actual.isSwapOutPendiente()
                && (mejor == null || actual.getPrioridad() > mejor.getPrioridad())) {
                mejor = actual;
            }
            aux.encolar(actual);
        }
        boolean extraida = false;
        while (!aux.esVacia()) {
            Pcb actual = aux.desencolar();
            if (!extraida && actual == mejor) extraida = true;
            else cola.encolar(actual);
        }
        return mejor;
    }

    private ListaEnlazada<Bloque> asignarBloquesSwap(Disco disco, String idProceso, int cantidad) {
        if (disco.contarBloquesLibresSwap() < cantidad) return null;
        ListaEnlazada<Bloque> asignados = new ListaEnlazada<>();
        for (int i = 0; i < cantidad; i++) {
            int idLibre = disco.buscarBloqueLibreSwap();
            if (idLibre == -1) {
                for (int j = 0; j < asignados.getTamano(); j++)
                    disco.liberarBloque(asignados.obtener(j).getId());
                return null;
            }
            Bloque b = disco.getBloque(idLibre);
            b.setOcupado(true);
            b.setContenido("SWAP-" + idProceso);
            b.setColorHex("#E74C3C");
            if (!asignados.esVacia())
                asignados.obtener(asignados.getTamano() - 1).setSiguienteBloque(b.getId());
            b.setSiguienteBloque(-1);
            asignados.agregarAlFinal(b);
        }
        return asignados;
    }

    private void liberarBloquesSwap(Disco disco, RegistroSwap reg) {
        if (reg == null || reg.bloquesSwap == null || disco == null) return;
        for (int i = 0; i < reg.bloquesSwap.getTamano(); i++)
            disco.liberarBloque(reg.bloquesSwap.obtener(i).getId());
    }

    public int getTotalSuspensiones() { return totalSuspensiones; }
    public int getTotalReactivaciones() { return totalReactivaciones; }
    public ListaEnlazada<RegistroSwap> getRegistrosSwap() { return registrosSwap; }
    public int getMemoriaReservadaLiberacion() { return memoriaReservadaLiberacion; }
}