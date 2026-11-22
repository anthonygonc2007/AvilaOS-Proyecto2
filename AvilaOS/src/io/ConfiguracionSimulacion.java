/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io;

/**
 * Representa la configuración inicial de la simulación
 * @author Usuario
 */
public class ConfiguracionSimulacion {

public static class ArchivoSistemaArchivosJSON {
        public DiskConfigJSON diskConfig;
        public NodoFSJSON[] fileSystem;
    }

    public static class DiskConfigJSON {
        public int totalBlocks;
        public int initialHead;
    }

    public static class NodoFSJSON {
        public String path;
        public String type;        // "FILE" o "DIRECTORY"
        public Integer startBlock; // Opcional en directorios
        public Integer blocks;     // Opcional en directorios
        public String owner;
        public String permissions; // "public" o "private"
    }

    public static class ArchivoProcesosJSON {
        public GlobalConfigJSON globalConfig;
        public ComputerConfigJSON computer;
        public ProcessJSON[] processes;
    }

    public static class GlobalConfigJSON {
        public int cycleDurationMs;
        public int swapSizeBlocks;
        public String initialDiskPolicy;
        public Integer headSpeed;
    }

    public static class ComputerConfigJSON {
        public int ramCapacity;
        public String policy;
        public int quantum;
    }

    public static class ProcessJSON {
        public String name;
        public int memory;
        public long deadline;
        public int instructions;
        public String targetPath;
        public String operation; // "READ", "CREATE", "UPDATE", "DELETE"
        public Integer priority;
    }

    public ArchivoSistemaArchivosJSON datosFS;
    public ArchivoProcesosJSON datosProcesos;
    public int velocidadCabezal = 1;
}
