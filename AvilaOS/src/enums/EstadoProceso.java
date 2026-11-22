/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package enums;

/**
 *
 * @author Antho
 */
public enum EstadoProceso {
    NEW,
    READY,
    RUNNING,
    BLOCKED,                    // Bloqueo genérico
    BLOQUEADO_MUTEX,            // Esperando exclusión mutua
    BLOQUEADO_PRODUCTOR_LLENO,  // Productor esperando espacio libre en el buffer
    BLOQUEADO_CONSUMIDOR_VACIO, // Consumidor esperando elemento en el buffer
    EXIT,
    LISTO_SUSPENDIDO,           // NUEVO: Proceso en Swap listo para RAM
    BLOQUEADO_SUSPENDIDO        // NUEVO: Proceso en Swap esperando I/O
}