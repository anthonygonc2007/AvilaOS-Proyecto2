/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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
        public boolean estabaBloqueadoAlSuspender;

        public RegistroSwap(String idProceso, int bloqueInicioSwap, ListaEnlazada<Bloque> bloquesSwap, boolean estabaBloqueado) {
            this.idProceso = idProceso;
            this.bloqueInicioSwap = bloqueInicioSwap;
            this.bloquesSwap = bloquesSwap;
            this.estabaBloqueadoAlSuspender = estabaBloqueado;
        }
    }

    private final ListaEnlazada<RegistroSwap> registrosSwap;
    private int totalSuspensiones;
    private int totalReactivaciones;

    public PlanificadorMedianoPlazo() {
        this.registrosSwap = new ListaEnlazada<>();
        this.totalSuspensiones = 0;
        this.totalReactivaciones = 0;
    }

    /**
     * Evalúa si hay procesos en colaNuevos esperando RAM. Si no caben, suspende procesos
     * hacia el área de intercambio del Disco hasta liberar espacio suficiente.
     */
    public synchronized void gestionarAdmisionYSuspension(Computador computador, Disco disco, int cicloActual) {
        if (computador == null || disco == null) return;

        Cola<Pcb> colaNuevos = computador.getColaNuevos();
        MemoriaRAM ram = computador.getMemoriaRAM();

        while (!colaNuevos.esVacia()) {
            Pcb candidatoNuevo = colaNuevos.verFrente();
            if (candidatoNuevo == null) break;

            // Si ya cabe en RAM, admitirlo directamente
            if (ram.getMemoriaLibre() >= candidatoNuevo.getMemoriaAsignada()) {
                computador.intentarAdmitirNuevos();
                break;
            }

            // Si el proceso requiere más RAM que toda la capacidad física, no es admisible
            if (candidatoNuevo.getMemoriaAsignada() > ram.getCapacidadTotal()) {
                break;
            }

            // Intentar suspender un proceso para hacer espacio
            boolean suspendido = suspenderVictima(computador, disco, cicloActual);
            if (!suspendido) {
                // No quedan víctimas elegibles o el área de Swap está llena
                break;
            }
        }

        // Intentar admitir si se liberó espacio suficiente
        computador.intentarAdmitirNuevos();
    }

    /**
     * Elige una víctima (priorizando procesos en BLOCKED de menor prioridad, i.e., mayor número de prioridad)
     * y la traslada al área reservada de Swap del Disco generando una SolicitudIO de tipo SUSPENDER.
     */
    public synchronized boolean suspenderVictima(Computador computador, Disco disco, int cicloActual) {
        // 1. Buscar primero en colaBloqueados (criterio del enunciado)
        Pcb victima = extraerVictimaDeCola(computador.getColaBloqueados(), disco.contarBloquesLibresSwap());
        boolean desdeBloqueados = (victima != null);

        // 2. Fallback: si no hay bloqueados elegibles, buscar en colaListos
        if (victima == null) {
            victima = extraerVictimaDeCola(computador.getColaListos(), disco.contarBloquesLibresSwap());
        }

        if (victima == null) {
            return false;
        }

        int bloquesNecesarios = Math.max(1, victima.getMemoriaAsignada());
        ListaEnlazada<Bloque> cadenaSwap = asignarBloquesSwap(disco, victima.getId(), bloquesNecesarios);
        if (cadenaSwap == null || cadenaSwap.esVacia()) {
            // Reinsertar en su cola original si falló la reserva en Swap
            if (desdeBloqueados) {
                computador.getColaBloqueados().encolar(victima);
            } else {
                computador.getColaListos().encolar(victima);
            }
            return false;
        }

        int bloqueInicio = cadenaSwap.obtener(0).getId();

        // Liberar RAM y actualizar estado a suspendido
        computador.getMemoriaRAM().liberarProceso(victima);
        if (desdeBloqueados) {
            victima.setEstado(EstadoProceso.BLOQUEADO_SUSPENDIDO);
            computador.getColaBloqueadosSuspendidos().encolar(victima);
        } else {
            victima.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
            computador.getColaListosSuspendidos().encolar(victima);
        }

        registrosSwap.agregarAlFinal(new RegistroSwap(victima.getId(), bloqueInicio, cadenaSwap, desdeBloqueados));
        totalSuspensiones++;

        // Encolar operación de E/S en la cola única del disco
        SolicitudIO solSwapOut = new SolicitudIO(
            victima.getId(),
            TipoOperacionIO.SUSPENDER,
            "SWAP:" + victima.getId(),
            bloqueInicio,
            bloquesNecesarios,
            cicloActual
        );
        disco.agregarSolicitud(solSwapOut);
        return true;
    }

    /**
     * Cuando se libera memoria RAM (por ejemplo al terminar un proceso), revisa si algún
     * proceso en LISTO_SUSPENDIDO puede volver a RAM y genera su SolicitudIO de REACTIVAR.
     */
    public synchronized int intentarReactivarSuspendidos(Computador computador, Disco disco, int cicloActual) {
        if (computador == null || disco == null) return 0;

        Cola<Pcb> colaListosSusp = computador.getColaListosSuspendidos();
        if (colaListosSusp.esVacia()) return 0;

        int reactivados = 0;
        Cola<Pcb> aux = new Cola<>();

        while (!colaListosSusp.esVacia()) {
            Pcb pcb = colaListosSusp.desencolar();
            if (computador.getMemoriaRAM().cargarProceso(pcb)) {
                RegistroSwap reg = extraerRegistroSwap(pcb.getId());
                int bloqueInicio = (reg != null) ? reg.bloqueInicioSwap : disco.getLimiteArchivos();
                if (reg != null) {
                    liberarBloquesSwap(disco, reg);
                }

                pcb.setMotivoBloqueo("");
                pcb.setEstado(EstadoProceso.READY);
                computador.getColaListos().encolar(pcb);
                totalReactivaciones++;
                reactivados++;

                SolicitudIO solSwapIn = new SolicitudIO(
                    pcb.getId(),
                    TipoOperacionIO.REACTIVAR,
                    "SWAP:" + pcb.getId(),
                    bloqueInicio,
                    Math.max(1, pcb.getMemoriaAsignada()),
                    cicloActual
                );
                disco.agregarSolicitud(solSwapIn);
            } else {
                aux.encolar(pcb);
            }
        }

        while (!aux.esVacia()) {
            colaListosSusp.encolar(aux.desencolar());
        }
        return reactivados;
    }

    /**
     * Extrae de la cola el proceso con mayor valor numérico de prioridad (menor urgencia)
     * cuyo tamaño en memoria quepa en los bloques libres de Swap.
     */
    private Pcb extraerVictimaDeCola(Cola<Pcb> cola, int bloquesLibresSwap) {
        if (cola == null || cola.esVacia()) return null;

        Pcb mejorVictima = null;
        Cola<Pcb> aux = new Cola<>();

        // Primera pasada: identificar candidato
        while (!cola.esVacia()) {
            Pcb actual = cola.desencolar();
            int memRequerida = Math.max(1, actual.getMemoriaAsignada());
            if (memRequerida <= bloquesLibresSwap) {
                if (mejorVictima == null || actual.getPrioridad() > mejorVictima.getPrioridad()) {
                    mejorVictima = actual;
                }
            }
            aux.encolar(actual);
        }

        // Segunda pasada: reconstruir cola excluyendo únicamente a la víctima elegida
        boolean extraida = false;
        while (!aux.esVacia()) {
            Pcb actual = aux.desencolar();
            if (!extraida && mejorVictima != null && actual == mejorVictima) {
                extraida = true;
            } else {
                cola.encolar(actual);
            }
        }

        return mejorVictima;
    }

    private ListaEnlazada<Bloque> asignarBloquesSwap(Disco disco, String idProceso, int cantidad) {
        if (disco.contarBloquesLibresSwap() < cantidad) return null;

        ListaEnlazada<Bloque> asignados = new ListaEnlazada<>();
        for (int i = 0; i < cantidad; i++) {
            int idLibre = disco.buscarBloqueLibreSwap();
            if (idLibre == -1) {
                for (int j = 0; j < asignados.getTamano(); j++) {
                    disco.liberarBloque(asignados.obtener(j).getId());
                }
                return null;
            }
            Bloque b = disco.getBloque(idLibre);
            b.setOcupado(true);
            b.setContenido("SWAP-" + idProceso);
            b.setColorHex("#E74C3C");
            if (!asignados.esVacia()) {
                asignados.obtener(asignados.getTamano() - 1).setSiguienteBloque(b.getId());
            }
            b.setSiguienteBloque(-1);
            asignados.agregarAlFinal(b);
        }
        return asignados;
    }

    private void liberarBloquesSwap(Disco disco, RegistroSwap reg) {
        if (reg == null || reg.bloquesSwap == null) return;
        for (int i = 0; i < reg.bloquesSwap.getTamano(); i++) {
            disco.liberarBloque(reg.bloquesSwap.obtener(i).getId());
        }
    }

    private RegistroSwap extraerRegistroSwap(String idProceso) {
        for (int i = 0; i < registrosSwap.getTamano(); i++) {
            RegistroSwap r = registrosSwap.obtener(i);
            if (r.idProceso.equals(idProceso)) {
                registrosSwap.eliminar(r);
                return r;
            }
        }
        return null;
    }

    public int getTotalSuspensiones() { return totalSuspensiones; }
    public int getTotalReactivaciones() { return totalReactivaciones; }
    public ListaEnlazada<RegistroSwap> getRegistrosSwap() { return registrosSwap; }
}