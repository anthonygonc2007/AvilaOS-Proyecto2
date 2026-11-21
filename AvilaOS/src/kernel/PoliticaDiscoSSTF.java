/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;
import interfaces.IPoliticaDisco;

public class PoliticaDiscoSSTF implements IPoliticaDisco {

    @Override
    public SolicitudIO seleccionarSiguiente(ListaEnlazada<SolicitudIO> colaES, int cabezalActual, boolean haciaArriba) {
        if (colaES == null || colaES.esVacia()) {
            return null;
        }

        SolicitudIO mejor = colaES.obtener(0);
        int menorDistancia = Math.abs(mejor.getBloqueObjetivo() - cabezalActual);

        for (int i = 1; i < colaES.getTamano(); i++) {
            SolicitudIO actual = colaES.obtener(i);
            int distancia = Math.abs(actual.getBloqueObjetivo() - cabezalActual);
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                mejor = actual;
            }
        }

        return mejor;
    }

    @Override
    public String getNombrePolitica() {
        return "SSTF";
    }
}