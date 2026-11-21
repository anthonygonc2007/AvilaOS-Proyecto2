/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import enums.PoliticaDisco;
import estructuras.ListaEnlazada;
import interfaces.IPoliticaDisco;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import kernel.Archivo;
import kernel.Directorio;
import kernel.Disco;
import kernel.NodoFS;
import kernel.PoliticaDiscoCSCAN;
import kernel.PoliticaDiscoFIFO;
import kernel.PoliticaDiscoSCAN;
import kernel.PoliticaDiscoSSTF;
import kernel.SistemaArchivos;

/**
 * 
 * @author Usuario
 */
public class GestorJSON {

    private final Gson gson;
    
    public GestorJSON() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public ConfiguracionSimulacion.ArchivoSistemaArchivosJSON cargarSistemaArchivosJSON(String rutaArchivo) throws IOException {
        try (FileReader lector = new FileReader(rutaArchivo)) {
            ConfiguracionSimulacion.ArchivoSistemaArchivosJSON datos =
                gson.fromJson(lector, ConfiguracionSimulacion.ArchivoSistemaArchivosJSON.class);
            if (datos == null || datos.diskConfig == null) {
                throw new IOException("El archivo JSON no contiene una sección 'diskConfig' válida.");
            }
            return datos;
        }
    }

    public ConfiguracionSimulacion.ArchivoProcesosJSON cargarProcesosJSON(String rutaArchivo) throws IOException {
        try (FileReader lector = new FileReader(rutaArchivo)) {
            ConfiguracionSimulacion.ArchivoProcesosJSON datos =
                gson.fromJson(lector, ConfiguracionSimulacion.ArchivoProcesosJSON.class);
            if (datos == null || datos.globalConfig == null || datos.computer == null) {
                throw new IOException("El archivo JSON no contiene 'globalConfig' o 'computer' válidos.");
            }
            return datos;
        }
    }

    public SistemaArchivos construirSistemaArchivos(ConfiguracionSimulacion config) {
        int totalBloques = config.datosFS.diskConfig.totalBlocks;
        int cabezalInicial = config.datosFS.diskConfig.initialHead;
        int swapBlocks = config.datosProcesos.globalConfig.swapSizeBlocks;
        int velocidad = Math.max(1, config.velocidadCabezal);

        IPoliticaDisco politica = instanciarPoliticaDisco(config.datosProcesos.globalConfig.initialDiskPolicy);
        Disco disco = new Disco(totalBloques, swapBlocks, velocidad, cabezalInicial, politica);
        SistemaArchivos fs = new SistemaArchivos(disco);

        if (config.datosFS.fileSystem != null) {
            for (ConfiguracionSimulacion.NodoFSJSON nodo : config.datosFS.fileSystem) {
                if (nodo != null && "DIRECTORY".equalsIgnoreCase(nodo.type)) {
                    fs.cargarDirectorioJSON(nodo.path, nodo.owner, nodo.permissions);
                }
            }
            for (ConfiguracionSimulacion.NodoFSJSON nodo : config.datosFS.fileSystem) {
                if (nodo != null && "FILE".equalsIgnoreCase(nodo.type)) {
                    int start = (nodo.startBlock != null) ? nodo.startBlock : -1;
                    int blocks = (nodo.blocks != null) ? nodo.blocks : 1;
                    fs.cargarArchivoJSON(nodo.path, start, blocks, nodo.owner, nodo.permissions, "Sistema");
                }
            }
        }
        return fs;
    }

    public void guardarSistemaArchivosJSON(SistemaArchivos fs, String rutaArchivo) throws IOException {
        if (fs == null || fs.getDisco() == null) return;

        Disco disco = fs.getDisco();
        ConfiguracionSimulacion.ArchivoSistemaArchivosJSON salida = new ConfiguracionSimulacion.ArchivoSistemaArchivosJSON();
        salida.diskConfig = new ConfiguracionSimulacion.DiskConfigJSON();
        salida.diskConfig.totalBlocks = disco.getTotalBloques();
        salida.diskConfig.initialHead = disco.getCabezalActual();

        ListaEnlazada<ConfiguracionSimulacion.NodoFSJSON> listaNodos = new ListaEnlazada<>();
        recolectarNodosRecursivo(fs.getRaiz(), listaNodos);

        salida.fileSystem = new ConfiguracionSimulacion.NodoFSJSON[listaNodos.getTamano()];
        for (int i = 0; i < listaNodos.getTamano(); i++) {
            salida.fileSystem[i] = listaNodos.obtener(i);
        }

        try (FileWriter escritor = new FileWriter(rutaArchivo)) {
            gson.toJson(salida, escritor);
        }
    }

    private void recolectarNodosRecursivo(Directorio dir, ListaEnlazada<ConfiguracionSimulacion.NodoFSJSON> acumulador) {
        ListaEnlazada<NodoFS> hijos = dir.getContenido();
        for (int i = 0; i < hijos.getTamano(); i++) {
            NodoFS hijo = hijos.obtener(i);
            ConfiguracionSimulacion.NodoFSJSON dto = new ConfiguracionSimulacion.NodoFSJSON();
            dto.path = hijo.getRutaCompleta();
            dto.owner = hijo.getOwner();
            dto.permissions = hijo.getPermisosTexto();

            if (hijo.isDirectorio()) {
                dto.type = "DIRECTORY";
                acumulador.agregarAlFinal(dto);
                recolectarNodosRecursivo((Directorio) hijo, acumulador);
            } else {
                Archivo arch = (Archivo) hijo;
                dto.type = "FILE";
                dto.startBlock = arch.getPrimerBloque();
                dto.blocks = arch.getTamano();
                acumulador.agregarAlFinal(dto);
            }
        }
    }

    public static IPoliticaDisco instanciarPoliticaDisco(String nombrePolitica) {
        if (nombrePolitica == null) return new PoliticaDiscoFIFO();
        String norm = nombrePolitica.trim().toUpperCase().replace("-", "_");
        try {
            PoliticaDisco pol = PoliticaDisco.valueOf(norm);
            switch (pol) {
                case SSTF: return new PoliticaDiscoSSTF();
                case SCAN: return new PoliticaDiscoSCAN();
                case C_SCAN: return new PoliticaDiscoCSCAN();
                case FIFO:
                default: return new PoliticaDiscoFIFO();
            }
        } catch (IllegalArgumentException e) {
            return new PoliticaDiscoFIFO();
        }
    }
}