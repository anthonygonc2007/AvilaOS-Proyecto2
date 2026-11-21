/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;

public class Directorio extends NodoFS {
    private final ListaEnlazada<NodoFS> contenido;

    public Directorio(String nombre, String fechaCreacion) {
        this(nombre, fechaCreacion, "admin", true);
    }

    public Directorio(String nombre, String fechaCreacion, String owner, boolean publico) {
        super(nombre, fechaCreacion, owner, publico);
        this.contenido = new ListaEnlazada<>();
    }

    public ListaEnlazada<NodoFS> getContenido() { return contenido; }

    public void agregarNodo(NodoFS nodo) {
        if (nodo == null || nodo == this) return;
        nodo.setPadre(this);
        this.contenido.agregarAlFinal(nodo);
        recalcularTamano();
    }

    public boolean eliminarNodo(NodoFS nodo) {
        if (nodo == null) return false;
        boolean eliminado = this.contenido.eliminar(nodo);
        if (eliminado) {
            nodo.setPadre(null);
            recalcularTamano();
        }
        return eliminado;
    }

    public NodoFS buscarHijo(String nombreHijo) {
        for (int i = 0; i < contenido.getTamano(); i++) {
            NodoFS hijo = contenido.obtener(i);
            if (hijo.getNombre().equals(nombreHijo)) {
                return hijo;
            }
        }
        return null;
    }

    public void recalcularTamano() {
        int total = 0;
        for (int i = 0; i < contenido.getTamano(); i++) {
            NodoFS hijo = contenido.obtener(i);
            total += hijo.getTamano();
        }
        this.tamano = total;
        if (this.padre != null && this.padre != this) {
            this.padre.recalcularTamano();
        }
    }

    @Override
    public boolean isDirectorio() {
        return true;
    }
}