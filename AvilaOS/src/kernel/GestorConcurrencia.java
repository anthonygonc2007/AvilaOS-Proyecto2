/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.Cola;
import estructuras.ListaEnlazada;

/**
 * Gestiona la sincronización y exclusión mutua del sistema de almacenamiento:
 * - Semáforo mutex para la cola de E/S del disco.
 * - Semáforos mutex para el árbol de directorios y la tabla de asignación.
 * - Locks de Lectura/Escritura (Readers-Writer) por ruta de archivo.
 */
public class GestorConcurrencia {

    public static class LockArchivo {
        private final String rutaArchivo;
        private int lectoresActivos;
        private String escritorActivoId;
        private final Cola<String> colaEspera;

        public LockArchivo(String rutaArchivo) {
            this.rutaArchivo = rutaArchivo;
            this.lectoresActivos = 0;
            this.escritorActivoId = null;
            this.colaEspera = new Cola<>();
        }

        public String getRutaArchivo() { return rutaArchivo; }
        public int getLectoresActivos() { return lectoresActivos; }
        public String getEscritorActivoId() { return escritorActivoId; }
        public boolean estaLibre() { return lectoresActivos == 0 && escritorActivoId == null; }
    }

    private final Semaforo mutexColaES;
    private final Semaforo mutexArbolDirectorios;
    private final Semaforo mutexTablaAsignacion;
    private final ListaEnlazada<LockArchivo> tablaLocksArchivos;
    private final Pcb pcbControl;

    public GestorConcurrencia() {
        this.mutexColaES = new Semaforo(1);
        this.mutexArbolDirectorios = new Semaforo(1);
        this.mutexTablaAsignacion = new Semaforo(1);
        this.tablaLocksArchivos = new ListaEnlazada<>();
        this.pcbControl = new Pcb("SYS_SYNC", "0", enums.ProcesoTipo.CPU_BOUND, 0, 0, Long.MAX_VALUE, 1);
    }

    // --- Exclusión mutua de estructuras globales ---

    public void entrarColaES(Pcb solicitante) {
        Pcb actor = (solicitante != null) ? solicitante : pcbControl;
        mutexColaES.semWait(actor, "MUTEX_COLA_ES");
    }

    public Pcb salirColaES(Pcb solicitante) {
        Pcb actor = (solicitante != null) ? solicitante : pcbControl;
        return mutexColaES.semSignal(actor);
    }

    public void entrarArbolDirectorios(Pcb solicitante) {
        Pcb actor = (solicitante != null) ? solicitante : pcbControl;
        mutexArbolDirectorios.semWait(actor, "MUTEX_ARBOL_FS");
    }

    public Pcb salirArbolDirectorios(Pcb solicitante) {
        Pcb actor = (solicitante != null) ? solicitante : pcbControl;
        return mutexArbolDirectorios.semSignal(actor);
    }

    public void entrarTablaAsignacion(Pcb solicitante) {
        Pcb actor = (solicitante != null) ? solicitante : pcbControl;
        mutexTablaAsignacion.semWait(actor, "MUTEX_TABLA_ASIG");
    }

    public Pcb salirTablaAsignacion(Pcb solicitante) {
        Pcb actor = (solicitante != null) ? solicitante : pcbControl;
        return mutexTablaAsignacion.semSignal(actor);
    }

    // --- Locks de Lectura / Escritura por Archivo ---

    /**
     * Intenta adquirir acceso de lectura compartida sobre un archivo.
     * Permite múltiples lectores concurrentes siempre que no exista un escritor activo.
     */
    public synchronized boolean adquirirLectura(String rutaArchivo, String idProceso) {
        if (rutaArchivo == null || idProceso == null) return false;
        LockArchivo lock = obtenerOCrearLock(rutaArchivo);

        if (lock.escritorActivoId != null && !lock.escritorActivoId.equals(idProceso)) {
            encolarSiNoExiste(lock.colaEspera, idProceso);
            return false;
        }

        lock.lectoresActivos++;
        return true;
    }

    /**
     * Intenta adquirir acceso de escritura exclusiva sobre un archivo (CREAR, ACTUALIZAR, ELIMINAR).
     * Requiere cero lectores activos y ningún otro escritor activo.
     */
    public synchronized boolean adquirirEscritura(String rutaArchivo, String idProceso) {
        if (rutaArchivo == null || idProceso == null) return false;
        LockArchivo lock = obtenerOCrearLock(rutaArchivo);

        if (lock.lectoresActivos > 0 || (lock.escritorActivoId != null && !lock.escritorActivoId.equals(idProceso))) {
            encolarSiNoExiste(lock.colaEspera, idProceso);
            return false;
        }

        lock.escritorActivoId = idProceso;
        return true;
    }

    /**
     * Libera el lock de lectura o escritura que poseía el proceso sobre la ruta dada.
     */
    public synchronized void liberarArchivo(String rutaArchivo, String idProceso) {
        if (rutaArchivo == null) return;
        LockArchivo lock = buscarLock(rutaArchivo);
        if (lock == null) return;

        if (lock.escritorActivoId != null && (idProceso == null || lock.escritorActivoId.equals(idProceso))) {
            lock.escritorActivoId = null;
        } else if (lock.lectoresActivos > 0) {
            lock.lectoresActivos--;
        }

        if (lock.estaLibre() && lock.colaEspera.esVacia()) {
            tablaLocksArchivos.eliminar(lock);
        }
    }

    public synchronized boolean tieneEscritorActivo(String rutaArchivo) {
        LockArchivo lock = buscarLock(rutaArchivo);
        return lock != null && lock.escritorActivoId != null;
    }

    public synchronized int getLectoresActivos(String rutaArchivo) {
        LockArchivo lock = buscarLock(rutaArchivo);
        return (lock != null) ? lock.lectoresActivos : 0;
    }

    private LockArchivo obtenerOCrearLock(String ruta) {
        LockArchivo existente = buscarLock(ruta);
        if (existente != null) return existente;
        LockArchivo nuevo = new LockArchivo(ruta);
        tablaLocksArchivos.agregarAlFinal(nuevo);
        return nuevo;
    }

    private LockArchivo buscarLock(String ruta) {
        for (int i = 0; i < tablaLocksArchivos.getTamano(); i++) {
            LockArchivo l = tablaLocksArchivos.obtener(i);
            if (l.rutaArchivo.equals(ruta)) {
                return l;
            }
        }
        return null;
    }

    private void encolarSiNoExiste(Cola<String> cola, String idProceso) {
        Cola<String> aux = new Cola<>();
        boolean presente = false;
        while (!cola.esVacia()) {
            String item = cola.desencolar();
            if (item.equals(idProceso)) presente = true;
            aux.encolar(item);
        }
        while (!aux.esVacia()) {
            cola.encolar(aux.desencolar());
        }
        if (!presente) {
            cola.encolar(idProceso);
        }
    }

    public Semaforo getMutexColaES() { return mutexColaES; }
    public Semaforo getMutexArbolDirectorios() { return mutexArbolDirectorios; }
    public Semaforo getMutexTablaAsignacion() { return mutexTablaAsignacion; }
}