/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import enums.EstadoProceso;
import enums.PoliticaPlanificacion;
import enums.TipoOperacionIO;
import estructuras.Cola;
import estructuras.ListaEnlazada;
import interfaces.Planificador;
import metricas.GestorMetricas;
import interfaces.IClockObserver;
import core.RelojGlobal;

public class Computador implements IClockObserver {
    private final String id;
    private final MemoriaRAM memoriaRAM;
    private final Cpu cpu;
    private Disco disco;
    private SistemaArchivos sistemaArchivos;
    private final PlanificadorMedianoPlazo planificadorMedianoPlazo;
    private final Semaforo mutexColaDisco;
    private int cicloRelojActual;
    
    // Colas del ciclo de vida del proceso
    private final Cola<Pcb> colaNuevos;
    private final Cola<Pcb> colaListos;
    private final Cola<Pcb> colaBloqueados;
    private final Cola<Pcb> colaTerminados;
    private final Cola<Pcb> colaListosSuspendidos;
    private final Cola<Pcb> colaBloqueadosSuspendidos;
    private ListaEnlazada<BufferAcotado> listaBuffersGlobales;

    // Componentes de planificación
    private PoliticaPlanificacion politicaActual;
    private Planificador planificador;
    private int quantum;
    private final GestorMetricas gestorMetricas;

    public Computador(String id, int capacidadRAM, GestorMetricas gestorMetricas, ListaEnlazada<BufferAcotado> buffers) {
        this.id = id;
        this.memoriaRAM = new MemoriaRAM(capacidadRAM);
        this.gestorMetricas = gestorMetricas;
        this.listaBuffersGlobales = buffers;
        this.cpu = new Cpu();
        this.disco = null;
        this.sistemaArchivos = null;
        this.planificadorMedianoPlazo = new PlanificadorMedianoPlazo();
        this.mutexColaDisco = new Semaforo(1);
        this.cicloRelojActual = 0;
        
        this.colaNuevos = new Cola<>();
        this.colaListos = new Cola<>();
        this.colaBloqueados = new Cola<>();
        this.colaTerminados = new Cola<>();
        this.colaListosSuspendidos = new Cola<>();
        this.colaBloqueadosSuspendidos = new Cola<>();

        this.quantum = 2;
        setPoliticaPlanificacion(PoliticaPlanificacion.FCFS, this.quantum);
        
        RelojGlobal.registrarObservador(this);
    }

    public void setPoliticaPlanificacion(PoliticaPlanificacion politica, int quantum) {
        this.politicaActual = politica;
        this.quantum = quantum;
        
        switch (politica) {
            case ROUND_ROBIN:
                this.planificador = new PoliticaRoundRobin(quantum);
                break;
            case PRIORIDAD:
                this.planificador = new PoliticaPrioridades();
                break;
            case EDF:
                this.planificador = new PoliticaEDF();
                break;
            case FCFS:
            default:
                this.planificador = new PoliticaFCFS();
                break;
        }
    }

    public boolean admitirProceso(Pcb proceso) {
        if (!this.id.equals(proceso.getComputadorId())) {
            System.out.println("ALERTA DE SEGURIDAD: Intento de migración bloqueado. " +
                               "El proceso " + proceso.getId() + 
                               " pertenece a " + proceso.getComputadorId() + 
                               " y no puede ingresar a " + this.id);
            return false;
        }
        
        proceso.setEstado(EstadoProceso.NEW);
        this.colaNuevos.encolar(proceso);
        
        if (this.disco != null) {
            planificadorMedianoPlazo.gestionarAdmisionYSuspension(this, this.disco, cicloRelojActual);
        } else {
            intentarAdmitirNuevos();
        }
        return true;
    }

    public void intentarAdmitirNuevos() {
        if (colaNuevos.esVacia()) return;

        Cola<Pcb> aux = new Cola<>();
        while (!colaNuevos.esVacia()) {
            Pcb proceso = colaNuevos.desencolar();
            if (memoriaRAM.cargarProceso(proceso)) {
                proceso.setEstado(EstadoProceso.READY);
                colaListos.encolar(proceso);
            } else {
                aux.encolar(proceso);
            }
        }
        
        while (!aux.esVacia()) {
            colaNuevos.encolar(aux.desencolar());
        }
    }

    public void ejecutarCicloReloj(BufferAcotado[] buffers) {
        if (this.disco != null) {
            planificadorMedianoPlazo.gestionarAdmisionYSuspension(this, this.disco, cicloRelojActual);
        } else {
            intentarAdmitirNuevos();
        }

        revisarDesbloqueados(); 
        decrementarDeadlines();
        if (planificador != null) {
            planificador.planificar(colaListos, cpu);
        }

        if (cpu.estaOcupada()) {
            gestorMetricas.registrarCicloCPU();
            Pcb enCpu = cpu.getProcesoEnEjecucion();

            if (enCpu.tieneOperacionIO() && this.disco != null) {
                cpu.setModoSO();
                enCpu.incrementarTiempoModoSO(1);

                if (mutexColaDisco.semWait(enCpu, "MUTEX")) {
                    try {
                        SolicitudIO solicitud = new SolicitudIO(
                            enCpu.getId(),
                            enCpu.getOperacionIO(),
                            enCpu.getRutaArchivo(),
                            enCpu.getBloqueObjetivo(),
                            enCpu.getCantidadBloques(),
                            cicloRelojActual
                        );
                        disco.agregarSolicitud(solicitud);
                    } finally {
                        Pcb despertado = mutexColaDisco.semSignal(enCpu);
                        if (despertado != null) {
                            colaListos.encolar(despertado);
                        }
                    }

                    enCpu.setOperacionIO(null);
                    enCpu.setMotivoBloqueo("E/S");
                    enCpu.setEstado(EstadoProceso.BLOCKED);
                    cpu.setProcesoEnEjecucion(null);
                    colaBloqueados.encolar(enCpu);
                } else {
                    cpu.setProcesoEnEjecucion(null);
                    colaBloqueados.encolar(enCpu);
                }
                return;
            }

            if (enCpu.getTipo() == enums.ProcesoTipo.PRODUCTOR || enCpu.getTipo() == enums.ProcesoTipo.CONSUMIDOR) {
                cpu.setModoSO();
                procesarPcbBuffer(enCpu, buffers);
            } else if (enCpu.getTipo() == enums.ProcesoTipo.IO_BOUND) {
                cpu.ejecutarCiclo();
                if (enCpu.getProgramCounter() % 4 == 0 && enCpu.getEstado() == EstadoProceso.RUNNING) {
                    enCpu.setMotivoBloqueo("ESPERANDO_IO");
                    enCpu.setTiempoEsperaIO(3);
                    cpu.setProcesoEnEjecucion(null);
                    colaBloqueados.encolar(enCpu);
                }
            } else {
                cpu.ejecutarCiclo();
            }

            if (enCpu.getEstado() == EstadoProceso.EXIT) {
                memoriaRAM.liberarProceso(enCpu);
                colaTerminados.encolar(enCpu);
                cpu.setProcesoEnEjecucion(null);
                gestorMetricas.registrarProcesoCompletado(enCpu, 0, enCpu.getTiempoModoSO());

                if (this.disco != null) {
                    planificadorMedianoPlazo.intentarReactivarSuspendidos(this, this.disco, cicloRelojActual);
                    planificadorMedianoPlazo.gestionarAdmisionYSuspension(this, this.disco, cicloRelojActual);
                } else {
                    intentarAdmitirNuevos();
                }
            }
        }
    }

    private void procesarPcbBuffer(Pcb p, BufferAcotado[] buffers) {
        BufferAcotado buf = buscarBuffer(p.getBufferAsociadoId(), buffers);
        if (buf == null) {
            cpu.ejecutarCiclo(); 
            return;
        }

        int cicloActual = p.getTiempoModoSO(); 
        p.incrementarTiempoModoSO(1);

        if (cicloActual % Math.max(1, p.getCiclosProducirConsumir()) == 0) {
            boolean exitoOp = true;
            if (p.getTipo() == enums.ProcesoTipo.PRODUCTOR) {
                exitoOp = buf.producir(p, "Item-" + p.getId());
            } else if (p.getTipo() == enums.ProcesoTipo.CONSUMIDOR) {
                Object item = buf.consumir(p);
                exitoOp = (item != null);
            }

            if (exitoOp) {
                if (p.getElementosProcesados() >= p.getElementosRequeridos()) {
                    p.setEstado(EstadoProceso.EXIT);
                }
            } else {
                cpu.setProcesoEnEjecucion(null);
                colaBloqueados.encolar(p);
            }
        } else {
            cpu.ejecutarCiclo();
        }
    }
 
    private BufferAcotado buscarBuffer(String idBuffer, BufferAcotado[] buffers) {
        if (buffers == null) return null;
        for (BufferAcotado b : buffers) {
            if (b.getIdBuffer().equalsIgnoreCase(idBuffer)) return b;
        }
        return null;
    }

    private void decrementarDeadlines() {
        decrementarDeadlinesCola(colaListos);
        decrementarDeadlinesCola(colaBloqueados);
    }

    private void decrementarDeadlinesCola(Cola<Pcb> cola) {
        if (cola == null || cola.esVacia()) return;
        Cola<Pcb> aux = new Cola<>();
        boolean ramLiberada = false;

        while (!cola.esVacia()) {
            Pcb p = cola.desencolar();
            p.setDeadline(p.getDeadline() - 1);

            if (p.getDeadline() <= 0 && p.getEstado() != EstadoProceso.EXIT) {
                p.setEstado(EstadoProceso.EXIT);
                memoriaRAM.liberarProceso(p);
                colaTerminados.encolar(p);
                ramLiberada = true;
                
                gestorMetricas.registrarDeadlineFallado(); 
                gestorMetricas.registrarProcesoCompletado(p, 0, p.getTiempoModoSO());
            } else {
                aux.encolar(p);
            }
        }
        while (!aux.esVacia()) {
            cola.encolar(aux.desencolar());
        }

        if (ramLiberada && this.disco != null) {
            planificadorMedianoPlazo.intentarReactivarSuspendidos(this, this.disco, cicloRelojActual);
            planificadorMedianoPlazo.gestionarAdmisionYSuspension(this, this.disco, cicloRelojActual);
        }
    }

    private void revisarDesbloqueados() {
        if (this.disco != null) {
            SolicitudIO completada = disco.extraerSolicitudCompletada();
            while (completada != null) {
                TipoOperacionIO op = completada.getOperacion();
                if (op == TipoOperacionIO.CREAR || op == TipoOperacionIO.LEER ||
                    op == TipoOperacionIO.ACTUALIZAR || op == TipoOperacionIO.ELIMINAR) {
                    if (this.sistemaArchivos != null) {
                        this.sistemaArchivos.confirmarOperacionDisco(completada);
                    }
                    desbloquearProcesoPorDisco(completada.getIdProceso());
                }
                completada = disco.extraerSolicitudCompletada();
            }
        }

        if (colaBloqueados.esVacia()) return;
        Cola<Pcb> aux = new Cola<>();
        while (!colaBloqueados.esVacia()) {
            Pcb p = colaBloqueados.desencolar();
            
            if (p.getEstado() == EstadoProceso.READY) {
                colaListos.encolar(p);
            } else if ("ESPERANDO_IO".equals(p.getMotivoBloqueo())) {
                p.setTiempoEsperaIO(p.getTiempoEsperaIO() - 1);
                if (p.getTiempoEsperaIO() <= 0) {
                    p.setMotivoBloqueo("");
                    p.setEstado(EstadoProceso.READY);
                    colaListos.encolar(p);
                } else {
                    aux.encolar(p);
                }
            } else {
                aux.encolar(p);
            }
        }
        while (!aux.esVacia()) {
            colaBloqueados.encolar(aux.desencolar());
        }
    }

    private void desbloquearProcesoPorDisco(String idProceso) {
        if (idProceso == null) return;

        Cola<Pcb> auxBloq = new Cola<>();
        boolean encontrado = false;
        while (!colaBloqueados.esVacia()) {
            Pcb p = colaBloqueados.desencolar();
            if (!encontrado && p.getId().equals(idProceso)) {
                p.setMotivoBloqueo("");
                p.setEstado(EstadoProceso.READY);
                colaListos.encolar(p);
                encontrado = true;
            } else {
                auxBloq.encolar(p);
            }
        }
        while (!auxBloq.esVacia()) {
            colaBloqueados.encolar(auxBloq.desencolar());
        }

        if (encontrado) return;

        Cola<Pcb> auxSusp = new Cola<>();
        while (!colaBloqueadosSuspendidos.esVacia()) {
            Pcb p = colaBloqueadosSuspendidos.desencolar();
            if (!encontrado && p.getId().equals(idProceso)) {
                p.setMotivoBloqueo("");
                p.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
                colaListosSuspendidos.encolar(p);
                encontrado = true;
            } else {
                auxSusp.encolar(p);
            }
        }
        while (!auxSusp.esVacia()) {
            colaBloqueadosSuspendidos.encolar(auxSusp.desencolar());
        }
    }

    @Override
    public void tick(int cicloActual) {
        this.cicloRelojActual = cicloActual;
        BufferAcotado[] arregloBuffers = new BufferAcotado[this.listaBuffersGlobales.getTamano()];
        for (int i = 0; i < this.listaBuffersGlobales.getTamano(); i++) {
            arregloBuffers[i] = this.listaBuffersGlobales.obtener(i);
        }
        ejecutarCicloReloj(arregloBuffers);
    }

    // --- Getters y Setters ---
    public String getId() { return id; }
    public MemoriaRAM getMemoriaRAM() { return memoriaRAM; }
    public Cpu getCpu() { return cpu; }
    public Disco getDisco() { return disco; }
    public void setDisco(Disco disco) { this.disco = disco; }
    public SistemaArchivos getSistemaArchivos() { return sistemaArchivos; }
    public void setSistemaArchivos(SistemaArchivos sistemaArchivos) { this.sistemaArchivos = sistemaArchivos; }
    public PlanificadorMedianoPlazo getPlanificadorMedianoPlazo() { return planificadorMedianoPlazo; }
    public Cola<Pcb> getColaNuevos() { return colaNuevos; }
    public Cola<Pcb> getColaListos() { return colaListos; }
    public Cola<Pcb> getColaBloqueados() { return colaBloqueados; }
    public Cola<Pcb> getColaTerminados() { return colaTerminados; }
    public Cola<Pcb> getColaListosSuspendidos() { return colaListosSuspendidos; }
    public Cola<Pcb> getColaBloqueadosSuspendidos() { return colaBloqueadosSuspendidos; }
    public PoliticaPlanificacion getPoliticaActual() { return politicaActual; }
    public int getQuantum() { return quantum; }
}