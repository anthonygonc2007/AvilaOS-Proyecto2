/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

public abstract class NodoFS {
    protected String nombre;
    protected int tamano;
    protected String fechaCreacion;
    protected String owner;
    protected boolean publico;
    protected Directorio padre;

    public NodoFS(String nombre, String fechaCreacion) {
        this(nombre, fechaCreacion, "admin", true);
    }

    public NodoFS(String nombre, String fechaCreacion, String owner, boolean publico) {
        this.nombre = nombre;
        this.fechaCreacion = fechaCreacion;
        this.tamano = 0;
        this.owner = (owner != null && !owner.isEmpty()) ? owner : "admin";
        this.publico = publico;
        this.padre = null;
    }

    public String getRutaCompleta() {
        if (padre == null) {
            return "/";
        }
        String rutaPadre = padre.getRutaCompleta();
        if ("/".equals(rutaPadre)) {
            return "/" + nombre;
        }
        return rutaPadre + "/" + nombre;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getTamano() { return tamano; }
    public void setTamano(int tamano) { this.tamano = tamano; }

    public String getFechaCreacion() { return fechaCreacion; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public boolean isPublico() { return publico; }
    public void setPublico(boolean publico) { this.publico = publico; }
    public String getPermisosTexto() { return publico ? "public" : "private"; }

    public Directorio getPadre() { return padre; }
    public void setPadre(Directorio padre) { this.padre = padre; }

    public abstract boolean isDirectorio();
}