/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;

public class Archivo extends NodoFS {
    private String extension;
    private ListaEnlazada<Bloque> bloquesAsignados;

    public Archivo(String nombre, String fechaCreacion, String extension) {
        super(nombre, fechaCreacion);
        this.extension = extension;
        this.bloquesAsignados = new ListaEnlazada<>();
    }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public ListaEnlazada<Bloque> getBloquesAsignados() { return bloquesAsignados; }

    @Override
    public boolean isDirectorio() {
        return false; 
    }
}