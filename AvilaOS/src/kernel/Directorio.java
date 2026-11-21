/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;

public class Directorio extends NodoFS {
    private ListaEnlazada<NodoFS> contenido;

    public Directorio(String nombre, String fechaCreacion) {
        super(nombre, fechaCreacion);
        this.contenido = new ListaEnlazada<>();
    }

    public ListaEnlazada<NodoFS> getContenido() { return contenido; }

    public void agregarNodo(NodoFS nodo) {
        this.contenido.agregarAlFinal(nodo);
        this.tamano += nodo.getTamano();
    }

    @Override
    public boolean isDirectorio() {
        return true; 
    }
}