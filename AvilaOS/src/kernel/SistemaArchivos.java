/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import enums.ModoUsuario;
import enums.TipoOperacionIO;
import estructuras.ListaEnlazada;
import java.time.LocalDate;

public class SistemaArchivos {

    private static final String[] PALETA_COLORES = {
        "#3399FF", "#2ECC71", "#E67E22", "#9B59B6",
        "#1ABC9C", "#F1C40F", "#E74C3C", "#3498DB"
    };

    public static class OperacionPendiente {
        public final String idProceso;
        public final TipoOperacionIO operacion;
        public final String rutaObjetivo;
        public final String nuevoNombre;
        public final String owner;
        public final boolean publico;
        public final Archivo archivoPreReservado;

        public OperacionPendiente(String idProceso, TipoOperacionIO operacion, String rutaObjetivo,
                                  String nuevoNombre, String owner, boolean publico, Archivo archivoPreReservado) {
            this.idProceso = idProceso;
            this.operacion = operacion;
            this.rutaObjetivo = rutaObjetivo;
            this.nuevoNombre = nuevoNombre;
            this.owner = owner;
            this.publico = publico;
            this.archivoPreReservado = archivoPreReservado;
        }
    }

    private final Directorio raiz;
    private final Disco disco;
    private final ListaEnlazada<Archivo> tablaAsignacion;
    private final ListaEnlazada<OperacionPendiente> operacionesPendientes;
    private final GestorConcurrencia gestorConcurrencia;
    private final Pcb pcbSistema;
    private int indiceColor;

    public SistemaArchivos(Disco disco) {
        this.disco = disco;
        this.raiz = new Directorio("/", LocalDate.now().toString(), "admin", true);
        this.tablaAsignacion = new ListaEnlazada<>();
        this.operacionesPendientes = new ListaEnlazada<>();
        this.gestorConcurrencia = (disco != null && disco.getGestorConcurrencia() != null)
                ? disco.getGestorConcurrencia()
                : new GestorConcurrencia();
        this.pcbSistema = new Pcb("KERNEL_FS", "0", enums.ProcesoTipo.CPU_BOUND, 0, 0, Long.MAX_VALUE, 1);
        this.indiceColor = 0;
    }

    public synchronized boolean cargarDirectorioJSON(String ruta, String owner, String permissions) {
        String rutaNorm = normalizarRuta(ruta);
        if ("/".equals(rutaNorm)) return true;

        gestorConcurrencia.entrarArbolDirectorios(pcbSistema);
        try {
            String rutaPadre = obtenerRutaPadre(rutaNorm);
            String nombre = obtenerNombreBase(rutaNorm);
            Directorio dirPadre = asegurarRutaDirectorios(rutaPadre, owner, true);
            if (dirPadre == null) return false;

            NodoFS existente = dirPadre.buscarHijo(nombre);
            if (existente != null) return existente.isDirectorio();

            boolean esPublico = !"private".equalsIgnoreCase(permissions);
            Directorio nuevo = new Directorio(nombre, LocalDate.now().toString(), owner, esPublico);
            dirPadre.agregarNodo(nuevo);
            return true;
        } finally {
            gestorConcurrencia.salirArbolDirectorios(pcbSistema);
        }
    }

    public synchronized boolean cargarArchivoJSON(String ruta, int startBlock, int cantidadBloques,
                                                  String owner, String permissions, String procesoCreador) {
        String rutaNorm = normalizarRuta(ruta);
        String rutaPadre = obtenerRutaPadre(rutaNorm);
        String nombre = obtenerNombreBase(rutaNorm);

        gestorConcurrencia.entrarArbolDirectorios(pcbSistema);
        gestorConcurrencia.entrarTablaAsignacion(pcbSistema);
        try {
            Directorio dirPadre = asegurarRutaDirectorios(rutaPadre, owner, true);
            if (dirPadre == null || dirPadre.buscarHijo(nombre) != null) return false;

            boolean esPublico = !"private".equalsIgnoreCase(permissions);
            String color = siguienteColor();
            Archivo archivo = new Archivo(nombre, LocalDate.now().toString(), extraerExtension(nombre),
                                          owner, esPublico, procesoCreador, color);

            int asignados = 0;
            if (startBlock >= 0 && startBlock + cantidadBloques <= disco.getLimiteArchivos()) {
                boolean rangoLibre = true;
                for (int b = startBlock; b < startBlock + cantidadBloques; b++) {
                    if (disco.getBloque(b).isOcupado()) {
                        rangoLibre = false;
                        break;
                    }
                }
                if (rangoLibre) {
                    for (int b = startBlock; b < startBlock + cantidadBloques; b++) {
                        Bloque bloque = disco.getBloque(b);
                        bloque.setOcupado(true);
                        bloque.setContenido(nombre);
                        bloque.setColorHex(color);
                        archivo.agregarBloque(bloque);
                        asignados++;
                    }
                }
            }

            if (asignados == 0 && cantidadBloques > 0) {
                if (disco.contarBloquesLibresArchivos() < cantidadBloques) {
                    return false;
                }
                for (int i = 0; i < cantidadBloques; i++) {
                    int idLibre = disco.buscarBloqueLibreArchivos();
                    if (idLibre == -1) {
                        liberarCadenaBloques(archivo);
                        return false;
                    }
                    Bloque bloque = disco.getBloque(idLibre);
                    bloque.setOcupado(true);
                    bloque.setContenido(nombre);
                    bloque.setColorHex(color);
                    archivo.agregarBloque(bloque);
                }
            }

            dirPadre.agregarNodo(archivo);
            tablaAsignacion.agregarAlFinal(archivo);
            return true;
        } finally {
            gestorConcurrencia.salirTablaAsignacion(pcbSistema);
            gestorConcurrencia.salirArbolDirectorios(pcbSistema);
        }
    }

    public synchronized String prepararOperacionCRUD(Pcb pcb, TipoOperacionIO operacion, String rutaObjetivo,
                                                     int tamanoBloquesCrear, String nuevoNombre,
                                                     ModoUsuario modoUsuario, String usuarioActivo) {
        if (pcb == null || operacion == null || rutaObjetivo == null || rutaObjetivo.trim().isEmpty()) {
            return "Parámetros de operación de E/S inválidos.";
        }

        String rutaNorm = normalizarRuta(rutaObjetivo);
        boolean esAdmin = (modoUsuario == ModoUsuario.ADMINISTRADOR);
        String user = (usuarioActivo != null && !usuarioActivo.isEmpty()) ? usuarioActivo : "user1";

        gestorConcurrencia.entrarArbolDirectorios(pcb);
        gestorConcurrencia.entrarTablaAsignacion(pcb);
        try {
            switch (operacion) {
                case CREAR: {
                    String rutaPadre = obtenerRutaPadre(rutaNorm);
                    String nombre = obtenerNombreBase(rutaNorm);
                    NodoFS nodoPadre = buscarNodo(rutaPadre);
                    if (nodoPadre == null || !nodoPadre.isDirectorio()) {
                        return "El directorio padre '" + rutaPadre + "' no existe.";
                    }
                    Directorio dirPadre = (Directorio) nodoPadre;

                    if (!esAdmin && !dirPadre.getOwner().equals(user)) {
                        return "Permiso denegado: el usuario '" + user + "' no puede crear archivos en '" + rutaPadre + "' (dueño: " + dirPadre.getOwner() + ").";
                    }
                    if (dirPadre.buscarHijo(nombre) != null) {
                        return "Ya existe un elemento con el nombre '" + nombre + "' en '" + rutaPadre + "'.";
                    }
                    if (!gestorConcurrencia.adquirirEscritura(rutaNorm, pcb.getId())) {
                        return "Recurso bloqueado por concurrencia: otro proceso está operando sobre '" + rutaNorm + "'.";
                    }

                    if (tamanoBloquesCrear <= 0) {
                        if (!esAdmin) {
                            gestorConcurrencia.liberarArchivo(rutaNorm, pcb.getId());
                            return "Permiso denegado: solo el Administrador puede crear directorios.";
                        }
                        pcb.setOperacionIO(TipoOperacionIO.CREAR);
                        pcb.setRutaArchivo(rutaNorm);
                        pcb.setBloqueObjetivo(0);
                        pcb.setCantidadBloques(1);
                        operacionesPendientes.agregarAlFinal(
                            new OperacionPendiente(pcb.getId(), TipoOperacionIO.CREAR, rutaNorm, null, user, true, null)
                        );
                        return null;
                    }

                    if (disco.contarBloquesLibresArchivos() < tamanoBloquesCrear) {
                        gestorConcurrencia.liberarArchivo(rutaNorm, pcb.getId());
                        return "Espacio insuficiente en disco: se requieren " + tamanoBloquesCrear +
                               " bloques y solo hay " + disco.contarBloquesLibresArchivos() + " libres.";
                    }

                    String color = siguienteColor();
                    Archivo preArchivo = new Archivo(nombre, LocalDate.now().toString(), extraerExtension(nombre),
                                                     esAdmin ? "admin" : user, esAdmin, pcb.getNombre(), color);
                    for (int i = 0; i < tamanoBloquesCrear; i++) {
                        int idLibre = disco.buscarBloqueLibreArchivos();
                        Bloque b = disco.getBloque(idLibre);
                        b.setOcupado(true);
                        b.setContenido(nombre);
                        b.setColorHex(color);
                        preArchivo.agregarBloque(b);
                    }

                    pcb.setOperacionIO(TipoOperacionIO.CREAR);
                    pcb.setRutaArchivo(rutaNorm);
                    pcb.setBloqueObjetivo(preArchivo.getPrimerBloque());
                    pcb.setCantidadBloques(tamanoBloquesCrear);

                    operacionesPendientes.agregarAlFinal(
                        new OperacionPendiente(pcb.getId(), TipoOperacionIO.CREAR, rutaNorm, null,
                                               esAdmin ? "admin" : user, esAdmin, preArchivo)
                    );
                    return null;
                }

                case LEER: {
                    NodoFS nodo = buscarNodo(rutaNorm);
                    if (nodo == null || nodo.isDirectorio()) {
                        return "El archivo '" + rutaNorm + "' no existe.";
                    }
                    Archivo arch = (Archivo) nodo;
                    if (!esAdmin && !arch.isPublico() && !arch.getOwner().equals(user)) {
                        return "Permiso denegado: el archivo '" + rutaNorm + "' es privado y pertenece a '" + arch.getOwner() + "'.";
                    }
                    if (!gestorConcurrencia.adquirirLectura(rutaNorm, pcb.getId())) {
                        return "Archivo bloqueado para lectura: '" + rutaNorm + "' tiene una escritura exclusiva en curso.";
                    }

                    pcb.setOperacionIO(TipoOperacionIO.LEER);
                    pcb.setRutaArchivo(rutaNorm);
                    pcb.setBloqueObjetivo(Math.max(0, arch.getPrimerBloque()));
                    pcb.setCantidadBloques(Math.max(1, arch.getTamano()));
                    operacionesPendientes.agregarAlFinal(
                        new OperacionPendiente(pcb.getId(), TipoOperacionIO.LEER, rutaNorm, null, user, arch.isPublico(), null)
                    );
                    return null;
                }

                case ACTUALIZAR: {
                    if ("/".equals(rutaNorm)) {
                        return "No se puede renombrar el directorio raíz '/'.";
                    }
                    NodoFS nodo = buscarNodo(rutaNorm);
                    if (nodo == null) {
                        return "La ruta '" + rutaNorm + "' no existe.";
                    }
                    if (!esAdmin && !nodo.getOwner().equals(user)) {
                        return "Permiso denegado: solo el administrador o el dueño ('" + nodo.getOwner() + "') puede modificar '" + rutaNorm + "'.";
                    }
                    if (nuevoNombre == null || nuevoNombre.trim().isEmpty() || nuevoNombre.contains("/")) {
                        return "El nuevo nombre indicado no es válido.";
                    }
                    if (!gestorConcurrencia.adquirirEscritura(rutaNorm, pcb.getId())) {
                        return "Archivo bloqueado para actualización: '" + rutaNorm + "' está siendo leído o modificado.";
                    }

                    int bloqueObj = 0;
                    int cantBloques = 1;
                    if (!nodo.isDirectorio()) {
                        Archivo arch = (Archivo) nodo;
                        bloqueObj = Math.max(0, arch.getPrimerBloque());
                        cantBloques = Math.max(1, arch.getTamano());
                    }

                    pcb.setOperacionIO(TipoOperacionIO.ACTUALIZAR);
                    pcb.setRutaArchivo(rutaNorm);
                    pcb.setBloqueObjetivo(bloqueObj);
                    pcb.setCantidadBloques(cantBloques);
                    operacionesPendientes.agregarAlFinal(
                        new OperacionPendiente(pcb.getId(), TipoOperacionIO.ACTUALIZAR, rutaNorm, nuevoNombre.trim(), user, nodo.isPublico(), null)
                    );
                    return null;
                }

                case ELIMINAR: {
                    if ("/".equals(rutaNorm)) {
                        return "No se puede eliminar el directorio raíz '/'.";
                    }
                    NodoFS nodo = buscarNodo(rutaNorm);
                    if (nodo == null) {
                        return "La ruta '" + rutaNorm + "' no existe.";
                    }
                    if (!esAdmin && !nodo.getOwner().equals(user)) {
                        return "Permiso denegado: solo el administrador o el dueño ('" + nodo.getOwner() + "') puede eliminar '" + rutaNorm + "'.";
                    }
                    if (!gestorConcurrencia.adquirirEscritura(rutaNorm, pcb.getId())) {
                        return "Archivo bloqueado para eliminación: '" + rutaNorm + "' está en uso por otro proceso.";
                    }

                    int bloqueObj = 0;
                    int cantBloques = Math.max(1, nodo.getTamano());
                    if (!nodo.isDirectorio()) {
                        bloqueObj = Math.max(0, ((Archivo) nodo).getPrimerBloque());
                    }

                    pcb.setOperacionIO(TipoOperacionIO.ELIMINAR);
                    pcb.setRutaArchivo(rutaNorm);
                    pcb.setBloqueObjetivo(bloqueObj);
                    pcb.setCantidadBloques(cantBloques);
                    operacionesPendientes.agregarAlFinal(
                        new OperacionPendiente(pcb.getId(), TipoOperacionIO.ELIMINAR, rutaNorm, null, user, nodo.isPublico(), null)
                    );
                    return null;
                }

                default:
                    return "Operación no soportada por el Sistema de Archivos.";
            }
        } finally {
            gestorConcurrencia.salirTablaAsignacion(pcb);
            gestorConcurrencia.salirArbolDirectorios(pcb);
        }
    }

    public synchronized String confirmarOperacionDisco(SolicitudIO solicitud) {
        if (solicitud == null) return "";

        OperacionPendiente op = extraerOperacionPendiente(solicitud.getIdProceso());
        TipoOperacionIO tipo = (op != null) ? op.operacion : solicitud.getOperacion();
        String ruta = (op != null) ? op.rutaObjetivo : normalizarRuta(solicitud.getRutaArchivo());

        gestorConcurrencia.entrarArbolDirectorios(pcbSistema);
        gestorConcurrencia.entrarTablaAsignacion(pcbSistema);
        try {
            switch (tipo) {
                case CREAR: {
                    String rutaPadre = obtenerRutaPadre(ruta);
                    NodoFS padreNodo = buscarNodo(rutaPadre);
                    if (padreNodo == null || !padreNodo.isDirectorio()) {
                        if (op != null && op.archivoPreReservado != null) {
                            liberarCadenaBloques(op.archivoPreReservado);
                        }
                        return "Error al confirmar creación: directorio padre inexistente.";
                    }
                    Directorio dirPadre = (Directorio) padreNodo;

                    if (op != null && op.archivoPreReservado != null) {
                        dirPadre.agregarNodo(op.archivoPreReservado);
                        tablaAsignacion.agregarAlFinal(op.archivoPreReservado);
                        return "Archivo '" + ruta + "' creado (Primer bloque: " + op.archivoPreReservado.getPrimerBloque() +
                               ", Bloques: " + op.archivoPreReservado.getTamano() + ").";
                    } else {
                        String nombreDir = obtenerNombreBase(ruta);
                        String owner = (op != null) ? op.owner : "admin";
                        boolean pub = (op == null) || op.publico;
                        dirPadre.agregarNodo(new Directorio(nombreDir, LocalDate.now().toString(), owner, pub));
                        return "Directorio '" + ruta + "' creado.";
                    }
                }

                case LEER: {
                    return "Lectura completada sobre '" + ruta + "' (" + solicitud.getCantidadBloques() + " bloques).";
                }

                case ACTUALIZAR: {
                    NodoFS nodo = buscarNodo(ruta);
                    if (nodo != null && op != null && op.nuevoNombre != null) {
                        String nombreOriginal = nodo.getNombre();
                        String extension = "";
                        String nombreBase = nombreOriginal;
                        if (!nodo.isDirectorio() && nombreOriginal.contains(".")) {
                            int punto = nombreOriginal.lastIndexOf('.');
                            nombreBase = nombreOriginal.substring(0, punto);
                            extension = nombreOriginal.substring(punto);
                        }
                        if (!nombreBase.endsWith("_mod")) {
                            nombreBase = nombreBase + "_mod";
                        }
                        String nombreFinal = nombreBase + extension;
                        nodo.setNombre(nombreFinal);
                        if (!nodo.isDirectorio()) {
                            Archivo arch = (Archivo) nodo;
                            arch.setExtension(extension.replace(".", "")); // sin el punto
                            ListaEnlazada<Bloque> bloques = arch.getBloquesAsignados();
                            for (int i = 0; i < bloques.getTamano(); i++) {
                                bloques.obtener(i).setContenido(nombreFinal);
                            }
                        }
                        return "Elemento '" + ruta + "' renombrado a '" + nombreFinal + "'.";
                    }
                    return "Actualización completada en '" + ruta + "'.";
                }

                case ELIMINAR: {
                    NodoFS nodo = buscarNodo(ruta);
                    if (nodo != null && nodo.getPadre() != null) {
                        Directorio padre = nodo.getPadre();
                        eliminarRecursivo(nodo);
                        padre.eliminarNodo(nodo);
                        return "Elemento '" + ruta + "' eliminado y bloques liberados en disco.";
                    }
                    return "Eliminación completada en '" + ruta + "'.";
                }

                default:
                    return "";
            }
        } finally {
            gestorConcurrencia.liberarArchivo(ruta, solicitud.getIdProceso());
            gestorConcurrencia.salirTablaAsignacion(pcbSistema);
            gestorConcurrencia.salirArbolDirectorios(pcbSistema);
        }
    }

    private void eliminarRecursivo(NodoFS nodo) {
        if (nodo.isDirectorio()) {
            Directorio dir = (Directorio) nodo;
            ListaEnlazada<NodoFS> hijos = dir.getContenido();
            while (!hijos.esVacia()) {
                NodoFS hijo = hijos.obtener(0);
                eliminarRecursivo(hijo);
                dir.eliminarNodo(hijo);
            }
        } else {
            Archivo arch = (Archivo) nodo;
            liberarCadenaBloques(arch);
            tablaAsignacion.eliminar(arch);
        }
    }

    private void liberarCadenaBloques(Archivo archivo) {
        if (archivo == null) return;
        ListaEnlazada<Bloque> bloques = archivo.getBloquesAsignados();
        for (int i = 0; i < bloques.getTamano(); i++) {
            Bloque b = bloques.obtener(i);
            disco.liberarBloque(b.getId());
        }
    }

    public NodoFS buscarNodo(String ruta) {
        String rutaNorm = normalizarRuta(ruta);
        if ("/".equals(rutaNorm)) return raiz;

        String[] partes = dividirRuta(rutaNorm);
        Directorio actual = raiz;
        for (int i = 0; i < partes.length; i++) {
            NodoFS hijo = actual.buscarHijo(partes[i]);
            if (hijo == null) return null;
            if (i == partes.length - 1) return hijo;
            if (!hijo.isDirectorio()) return null;
            actual = (Directorio) hijo;
        }
        return null;
    }

    public synchronized boolean verificarIntegridadReferencial() {
        int bloquesContadosEnArbol = 0;

        for (int i = 0; i < tablaAsignacion.getTamano(); i++) {
            Archivo arch = tablaAsignacion.obtener(i);
            ListaEnlazada<Bloque> cadena = arch.getBloquesAsignados();
            if (cadena.esVacia() || arch.getPrimerBloque() != cadena.obtener(0).getId()) {
                return false;
            }
            for (int j = 0; j < cadena.getTamano(); j++) {
                Bloque b = cadena.obtener(j);
                if (!disco.esBloqueDeArchivos(b.getId()) || !b.isOcupado()) {
                    return false;
                }
                int esperadoSig = (j + 1 < cadena.getTamano()) ? cadena.obtener(j + 1).getId() : -1;
                if (b.getSiguienteBloque() != esperadoSig) {
                    return false;
                }
                bloquesContadosEnArbol++;
            }
        }

        int bloquesOcupadosEnDisco = 0;
        for (int i = 0; i < disco.getLimiteArchivos(); i++) {
            if (disco.getBloque(i).isOcupado()) {
                bloquesOcupadosEnDisco++;
            }
        }
        return bloquesContadosEnArbol == bloquesOcupadosEnDisco;
    }

    private Directorio asegurarRutaDirectorios(String rutaDir, String owner, boolean publico) {
        String rutaNorm = normalizarRuta(rutaDir);
        if ("/".equals(rutaNorm)) return raiz;

        String[] partes = dividirRuta(rutaNorm);
        Directorio actual = raiz;
        for (String parte : partes) {
            NodoFS hijo = actual.buscarHijo(parte);
            if (hijo == null) {
                Directorio nuevoDir = new Directorio(parte, LocalDate.now().toString(), owner, publico);
                actual.agregarNodo(nuevoDir);
                actual = nuevoDir;
            } else if (hijo.isDirectorio()) {
                actual = (Directorio) hijo;
            } else {
                return null;
            }
        }
        return actual;
    }

    private OperacionPendiente extraerOperacionPendiente(String idProceso) {
        if (idProceso == null) return null;
        for (int i = 0; i < operacionesPendientes.getTamano(); i++) {
            OperacionPendiente op = operacionesPendientes.obtener(i);
            if (op.idProceso.equals(idProceso)) {
                operacionesPendientes.eliminar(op);
                return op;
            }
        }
        return null;
    }

    private String normalizarRuta(String ruta) {
        if (ruta == null || ruta.trim().isEmpty()) return "/";
        String r = ruta.trim();
        if (!r.startsWith("/")) r = "/" + r;
        if (r.length() > 1 && r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }

    private String obtenerRutaPadre(String rutaNorm) {
        int idx = rutaNorm.lastIndexOf('/');
        if (idx <= 0) return "/";
        return rutaNorm.substring(0, idx);
    }

    private String obtenerNombreBase(String rutaNorm) {
        int idx = rutaNorm.lastIndexOf('/');
        return (idx >= 0) ? rutaNorm.substring(idx + 1) : rutaNorm;
    }

    private String[] dividirRuta(String rutaNorm) {
        String sinSlash = rutaNorm.startsWith("/") ? rutaNorm.substring(1) : rutaNorm;
        if (sinSlash.isEmpty()) return new String[0];
        return sinSlash.split("/");
    }

    private String extraerExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return (punto > 0 && punto < nombre.length() - 1) ? nombre.substring(punto + 1) : "";
    }

    private String siguienteColor() {
        String c = PALETA_COLORES[indiceColor % PALETA_COLORES.length];
        indiceColor++;
        return c;
    }

    public Directorio getRaiz() { return raiz; }
    public ListaEnlazada<Archivo> getTablaAsignacion() { return tablaAsignacion; }
    public Disco getDisco() { return disco; }
    public GestorConcurrencia getGestorConcurrencia() { return gestorConcurrencia; }

/**
 * Cancela la OperacionPendiente de un proceso que terminó (por deadline, swap u otro)
 * antes de que el disco completara su E/S. Libera bloques pre-reservados y el RW lock
 * para evitar fugas de recursos y bloqueos permanentes del archivo.
 */
public synchronized void cancelarOperacionPendiente(String idProceso) {
    if (idProceso == null) return;
    OperacionPendiente op = extraerOperacionPendiente(idProceso);
    if (op == null) return;

    gestorConcurrencia.entrarTablaAsignacion(pcbSistema);
    try {
        if (op.archivoPreReservado != null) {
            liberarCadenaBloques(op.archivoPreReservado);
        }
    } finally {
        gestorConcurrencia.salirTablaAsignacion(pcbSistema);
    }
    gestorConcurrencia.liberarArchivo(op.rutaObjetivo, idProceso);
}
}