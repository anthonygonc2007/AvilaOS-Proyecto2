/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package core;

import interfaces.IClockObserver;
import estructuras.ListaEnlazada;

public class RelojGlobal implements Runnable {
    private static int cicloActual = 0;
    private static boolean enEjecucion = false;
    private static int duracionCicloMs = 1000;
    private static final ListaEnlazada<IClockObserver> observadores = new ListaEnlazada<>();
    private static Thread hiloReloj;

    public static synchronized void registrarObservador(IClockObserver obs) {
        // Evitar registros duplicados (importante al reiniciar la simulación)
        for (int i = 0; i < observadores.getTamano(); i++) {
            if (observadores.obtener(i) == obs) return;
        }
        observadores.agregarAlFinal(obs);
    }

    // ══════ CAMBIO: nuevo método para desregistrar observadores ══════
    public static synchronized void desregistrarObservador(IClockObserver obs) {
        if (obs == null) return;
        observadores.eliminar(obs);
    }

    // ══════ CAMBIO: limpiar todos los observadores (útil en reinicio total) ══════
    public static synchronized void limpiarObservadores() {
        while (!observadores.esVacia()) {
            observadores.eliminar(observadores.obtener(0));
        }
    }
    // ══════════════════════════════════════════════════════════════════

    public static synchronized void setDuracionCicloMs(int ms) {
        duracionCicloMs = Math.max(50, ms);
    }

    public static synchronized void iniciar() {
        if (!enEjecucion) {
            enEjecucion = true;
            hiloReloj = new Thread(new RelojGlobal(), "Hilo-RelojGlobal");
            hiloReloj.start();
        }
    }

    public static synchronized void pausar() {
        enEjecucion = false;
    }

    public static synchronized void reiniciar() {
        pausar();
        cicloActual = 0;
    }

    @Override
    public void run() {
        while (enEjecucion) {
            try {
                Thread.sleep(duracionCicloMs);
                synchronized (RelojGlobal.class) {
                    cicloActual++;
                    notificarObservadores();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void notificarObservadores() {
        for (int i = 0; i < observadores.getTamano(); i++) {
            IClockObserver obs = observadores.obtener(i);
            if (obs != null) {
                obs.tick(cicloActual);
            }
        }
    }

    public static synchronized int getCicloActual() {
        return cicloActual;
    }

    public static synchronized boolean isEnEjecucion() {
        return enEjecucion;
    }
}