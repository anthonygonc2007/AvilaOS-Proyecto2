/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import enums.EstadoProceso;
import enums.ProcesoTipo;
import enums.TipoOperacionIO;
import java.util.UUID;

/**
 *
 * @author Gemelos
 */
public class Pcb {
    private final String id;                  // ID único
    private String nombre;                    // Nombre del proceso
    private String computadorId;        
    private EstadoProceso estado;
    private ProcesoTipo tipo;
    private int prioridad;
    private int memoriaAsignada;
    private int tiempoEsperaIO;
    private long deadline;
    private int tiempoRestanteEjecucion;
    
    private int programCounter;               // PC: Dirección de la instrucción actual
    private int memoryAddressRegister;        // MAR: Registro de dirección de memoria
    private String bufferAsociadoId;
    private int ciclosProducirConsumir;
    private int elementosRequeridos;
    private int elementosProcesados;
    private String motivoBloqueo;
    
    // Contador de tiempo invertido en Modo Sistema Operativo
    private int tiempoModoSO;
    
    // Bandera para registrar incumplimiento de deadline
    private boolean deadlineFalladoRegistrado;
    
    // CAMPOS PARA PROYECTO 2 (SISTEMA DE ARCHIVOS Y DISCO)
    private TipoOperacionIO operacionIO;
    private String rutaArchivo;
    private int bloqueObjetivo;
    private int cantidadBloques;

    public Pcb(String nombre, String computadorId, ProcesoTipo tipo, int prioridad, 
               int memoriaAsignada, long deadline, int tiempoRestanteEjecucion) {
        this.id = "PROC-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        this.nombre = nombre;
        this.computadorId = computadorId;
        this.estado = EstadoProceso.READY;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoriaAsignada = memoriaAsignada;
        this.deadline = deadline;
        this.tiempoRestanteEjecucion = tiempoRestanteEjecucion;
        this.programCounter = 0;
        this.memoryAddressRegister = 0;
        this.elementosProcesados = 0;
        this.motivoBloqueo = "";
        this.tiempoModoSO = 0;
        this.tiempoEsperaIO = 0;
        this.deadlineFalladoRegistrado = false;
        
        // Inicializados por defecto para operaciones de E/S
        this.operacionIO = null;
        this.rutaArchivo = "";
        this.bloqueObjetivo = 0;
        this.cantidadBloques = 1;
    }

    public void incrementarPC() {
        this.programCounter++;
        this.memoryAddressRegister++;
    }
    
    public void decrementarTiempoEjecucion() {
        if (tiempoRestanteEjecucion > 0) {
            tiempoRestanteEjecucion--;
        }
    }

    public void decrementarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
    }
    
    public void incrementarTiempoModoSO(int costo) {
        this.tiempoModoSO += costo;
    }
    
    public boolean tieneOperacionIO() {
        return this.operacionIO != null;
    }
    
    public boolean isDeadlineFalladoRegistrado() {
        return deadlineFalladoRegistrado;
    }

    public void setDeadlineFalladoRegistrado(boolean deadlineFalladoRegistrado) {
        this.deadlineFalladoRegistrado = deadlineFalladoRegistrado;
    }
    
    public int getTiempoModoSO() { return tiempoModoSO; }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getComputadorId() { return computadorId; }
    
    public EstadoProceso getEstado() { return estado; }
    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    
    public ProcesoTipo getTipo() { return tipo; }
    public int getPrioridad() { return prioridad; }
    public int getMemoriaAsignada() { return memoriaAsignada; }
    
    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) { this.deadline = deadline; }
    
    public int getTiempoRestanteEjecucion() { return tiempoRestanteEjecucion; }
    public void setTiempoRestanteEjecucion(int tiempoRestanteEjecucion) { this.tiempoRestanteEjecucion = tiempoRestanteEjecucion; }
    
    public int getProgramCounter() { return programCounter; }
    public void setProgramCounter(int programCounter) { this.programCounter = programCounter; }
    
    public int getMemoryAddressRegister() { return memoryAddressRegister; }
    public void setMemoryAddressRegister(int memoryAddressRegister) { this.memoryAddressRegister = memoryAddressRegister; }
    
    public String getBufferAsociadoId() { return bufferAsociadoId; }
    public void setBufferAsociadoId(String bufferAsociadoId) { this.bufferAsociadoId = bufferAsociadoId; }
    
    public int getCiclosProducirConsumir() { return ciclosProducirConsumir; }
    public void setCiclosProducirConsumir(int ciclosProducirConsumir) { this.ciclosProducirConsumir = ciclosProducirConsumir; }
    
    public int getElementosRequeridos() { return elementosRequeridos; }
    public void setElementosRequeridos(int elementosRequeridos) { this.elementosRequeridos = elementosRequeridos; }
    
    public int getElementosProcesados() { return elementosProcesados; }
    public void setElementosProcesados(int elementosProcesados) { this.elementosProcesados = elementosProcesados; }
    
    public String getMotivoBloqueo() { return motivoBloqueo; }
    public void setMotivoBloqueo(String motivoBloqueo) { this.motivoBloqueo = motivoBloqueo; }
    
    public int getTiempoEsperaIO() { return tiempoEsperaIO; }
    public void setTiempoEsperaIO(int tiempoEsperaIO) { this.tiempoEsperaIO = tiempoEsperaIO; }

    public TipoOperacionIO getOperacionIO() { return operacionIO; }
    public void setOperacionIO(TipoOperacionIO operacionIO) { this.operacionIO = operacionIO; }
    
    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public int getBloqueObjetivo() { return bloqueObjetivo; }
    public void setBloqueObjetivo(int bloqueObjetivo) { this.bloqueObjetivo = Math.max(0, bloqueObjetivo); }

    public int getCantidadBloques() { return cantidadBloques; }
    public void setCantidadBloques(int cantidadBloques) { this.cantidadBloques = Math.max(1, cantidadBloques); }

    
private boolean swapOutPendiente = false;
private boolean reactivacionPendiente = false;

public boolean isSwapOutPendiente() { return swapOutPendiente; }
public void setSwapOutPendiente(boolean b) { this.swapOutPendiente = b; }

public boolean isReactivacionPendiente() { return reactivacionPendiente; }
public void setReactivacionPendiente(boolean b) { this.reactivacionPendiente = b; }
}
