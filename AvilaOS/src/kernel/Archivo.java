/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;

public class Archivo extends NodoFS {
    private String extension;
    private ListaEnlazada<Bloque> bloquesAsignados;
    private int primerBloque;
    private String procesoCreador;
    private String colorHex;

    public Archivo(String nombre, String fechaCreacion, String extension) {
        this(nombre, fechaCreacion, extension, "admin", true, "Sistema", "#3399FF");
    }

    public Archivo(String nombre, String fechaCreacion, String extension,
                   String owner, boolean publico, String procesoCreador, String colorHex) {
        super(nombre, fechaCreacion, owner, publico);
        this.extension = extension;
        this.bloquesAsignados = new ListaEnlazada<>();
        this.primerBloque = -1;
        this.procesoCreador = (procesoCreador != null) ? procesoCreador : "Sistema";
        this.colorHex = (colorHex != null) ? colorHex : "#3399FF";
    }

    public void agregarBloque(Bloque bloque) {
        if (bloque == null) return;
        if (bloquesAsignados.esVacia()) {
            this.primerBloque = bloque.getId();
        } else {
            Bloque ultimo = bloquesAsignados.obtener(bloquesAsignados.getTamano() - 1);
            ultimo.setSiguienteBloque(bloque.getId());
        }
        bloque.setSiguienteBloque(-1);
        bloquesAsignados.agregarAlFinal(bloque);
        this.tamano = bloquesAsignados.getTamano();
    }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public ListaEnlazada<Bloque> getBloquesAsignados() { return bloquesAsignados; }
    public int getPrimerBloque() { return primerBloque; }
    public void setPrimerBloque(int primerBloque) { this.primerBloque = primerBloque; }
    public String getProcesoCreador() { return procesoCreador; }
    public void setProcesoCreador(String procesoCreador) { this.procesoCreador = procesoCreador; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    @Override
    public boolean isDirectorio() {
        return false;
    }
}