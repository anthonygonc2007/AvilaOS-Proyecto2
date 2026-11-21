/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import enums.TipoOperacionIO;

public class SolicitudIO {
    private final String idProceso;
    private final TipoOperacionIO operacion;
    private final String rutaArchivo;
    private final int bloqueObjetivo;
    private final int cantidadBloques;
    private final int cicloLlegada;

    public SolicitudIO(String idProceso, TipoOperacionIO operacion, String rutaArchivo, int bloqueObjetivo, int cantidadBloques, int cicloLlegada) {
        this.idProceso = idProceso;
        this.operacion = operacion;
        this.rutaArchivo = rutaArchivo;
        this.bloqueObjetivo = bloqueObjetivo;
        this.cantidadBloques = cantidadBloques;
        this.cicloLlegada = cicloLlegada;
    }

    public String getIdProceso() { return idProceso; }
    public TipoOperacionIO getOperacion() { return operacion; }
    public String getRutaArchivo() { return rutaArchivo; }
    public int getBloqueObjetivo() { return bloqueObjetivo; }
    public int getCantidadBloques() { return cantidadBloques; }
    public int getCicloLlegada() { return cicloLlegada; }
}
