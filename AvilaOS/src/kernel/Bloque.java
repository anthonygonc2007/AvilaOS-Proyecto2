/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

public class Bloque {
    private final int id;
    private boolean ocupado;
    private String contenido;

    public Bloque(int id) {
        this.id = id;
        this.ocupado = false;
        this.contenido = "";
    }

    public int getId() { return id; }
    public boolean isOcupado() { return ocupado; }
    public void setOcupado(boolean ocupado) { this.ocupado = ocupado; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    
    public void limpiar() {
        this.ocupado = false;
        this.contenido = "";
    }
}