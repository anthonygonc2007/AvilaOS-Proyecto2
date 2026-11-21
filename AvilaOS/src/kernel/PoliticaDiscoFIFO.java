/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;
import interfaces.IPoliticaDisco;

public class PoliticaDiscoFIFO implements IPoliticaDisco {

    @Override
    public SolicitudIO seleccionarSiguiente(ListaEnlazada<SolicitudIO> colaES, int cabezalActual, boolean haciaArriba) {
        if (colaES == null || colaES.esVacia()) {
            return null;
        }
        return colaES.obtener(0);
    }

    @Override
    public String getNombrePolitica() {
        return "FIFO";
    }
}