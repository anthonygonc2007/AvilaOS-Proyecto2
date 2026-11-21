/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package kernel;

import estructuras.ListaEnlazada;
import interfaces.IPoliticaDisco;

public class PoliticaDiscoCSCAN implements IPoliticaDisco {

    @Override
    public SolicitudIO seleccionarSiguiente(ListaEnlazada<SolicitudIO> colaES, int cabezalActual, boolean haciaArriba) {
        if (colaES == null || colaES.esVacia()) {
            return null;
        }

        SolicitudIO candidatoSuperior = null;
        int menorBloqueSuperior = Integer.MAX_VALUE;

        SolicitudIO candidatoMenorGlobal = null;
        int menorBloqueGlobal = Integer.MAX_VALUE;

        for (int i = 0; i < colaES.getTamano(); i++) {
            SolicitudIO actual = colaES.obtener(i);
            int objetivo = actual.getBloqueObjetivo();

            if (objetivo >= cabezalActual && objetivo < menorBloqueSuperior) {
                menorBloqueSuperior = objetivo;
                candidatoSuperior = actual;
            }

            if (objetivo < menorBloqueGlobal) {
                menorBloqueGlobal = objetivo;
                candidatoMenorGlobal = actual;
            }
        }

        return (candidatoSuperior != null) ? candidatoSuperior : candidatoMenorGlobal;
    }

    @Override
    public String getNombrePolitica() {
        return "C-SCAN";
    }
}