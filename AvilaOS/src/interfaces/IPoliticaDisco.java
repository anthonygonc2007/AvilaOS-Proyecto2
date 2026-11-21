/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package interfaces;

import estructuras.ListaEnlazada;
import kernel.SolicitudIO;

public interface IPoliticaDisco {
    SolicitudIO seleccionarSiguiente(ListaEnlazada<SolicitudIO> colaES, int cabezalActual, boolean haciaArriba);
    String getNombrePolitica();
}