/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;
import interfaces.IPoliticaDisco;

public class PoliticaDiscoSCAN implements IPoliticaDisco {

    @Override
    public SolicitudIO seleccionarSiguiente(ListaEnlazada<SolicitudIO> colaES, int cabezalActual, boolean haciaArriba) {
        if (colaES == null || colaES.esVacia()) {
            return null;
        }

        SolicitudIO candidatoEnDireccion = null;
        int mejorDistanciaDir = Integer.MAX_VALUE;

        SolicitudIO candidatoOpuesto = null;
        int mejorDistanciaOp = Integer.MAX_VALUE;

        for (int i = 0; i < colaES.getTamano(); i++) {
            SolicitudIO actual = colaES.obtener(i);
            int objetivo = actual.getBloqueObjetivo();

            if (haciaArriba) {
                if (objetivo >= cabezalActual) {
                    int dist = objetivo - cabezalActual;
                    if (dist < mejorDistanciaDir) {
                        mejorDistanciaDir = dist;
                        candidatoEnDireccion = actual;
                    }
                } else {
                    int dist = cabezalActual - objetivo;
                    if (dist < mejorDistanciaOp) {
                        mejorDistanciaOp = dist;
                        candidatoOpuesto = actual;
                    }
                }
            } else {
                if (objetivo <= cabezalActual) {
                    int dist = cabezalActual - objetivo;
                    if (dist < mejorDistanciaDir) {
                        mejorDistanciaDir = dist;
                        candidatoEnDireccion = actual;
                    }
                } else {
                    int dist = objetivo - cabezalActual;
                    if (dist < mejorDistanciaOp) {
                        mejorDistanciaOp = dist;
                        candidatoOpuesto = actual;
                    }
                }
            }
        }

        return (candidatoEnDireccion != null) ? candidatoEnDireccion : candidatoOpuesto;
    }

    @Override
    public String getNombrePolitica() {
        return "SCAN";
    }
}
