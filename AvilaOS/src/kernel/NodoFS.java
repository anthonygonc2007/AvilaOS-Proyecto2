/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

public abstract class NodoFS {
    protected String nombre;
    protected int tamano;
    protected String fechaCreacion;

    public NodoFS(String nombre, String fechaCreacion) {
        this.nombre = nombre;
        this.fechaCreacion = fechaCreacion;
        this.tamano = 0;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    
    public int getTamano() { return tamano; }
    protected void setTamano(int tamano) { this.tamano = tamano; }
    
    public String getFechaCreacion() { return fechaCreacion; }

    // Método abstracto para saber si es archivo o carpeta
    public abstract boolean isDirectorio();
}