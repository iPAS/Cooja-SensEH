import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
//import se.sics.cooja.GUI;  // Changed to be org.contikios.cooja.Cooja on Contiki 3.x
import org.contikios.cooja.Simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;


/**
 * SensEH Project
 * Originated by
 * @author raza
 * @see http://usmanraza.github.io/SensEH-Contiki/
 *
 * 'EHSystem' reads the configuration of the full energy harvesting system
 *   and initializes the system.
 *
 * Adopted and adapted by
 * @author ipas
 * @since 2015-05-01
 */
public class EHSystem { // EHSystem put all the pieces together

    private static final Level LOG_LEVEL = Level.INFO;  // ALL > TRACE > DEBUG > INFO > WARN > ERROR > FATAL > OFF
    private static Logger logger = Logger.getLogger(EHSystem.class);
    
    private double totalHarvestedEnergy = 0;

    private EnergySource source     = null;
    private Harvester harvester     = null;
    private EnergyStorage storage   = null;
    private EnvironmentalDataProvider envDataProvider = null;
    private double chargeInterval;  // in second
    
    private Simulation simulation;
    private EHNode ehNode;
    
    
    /**
     * Public methods
     */
    public EHNode getEHNode() {
        return ehNode;
    }

    public double getChargeInterval() {
        return chargeInterval;
    }

    public EnergyStorage getStorage() {
        return storage;
    }

    public Harvester getHarvester() {
        return harvester;
    }

    public double getVoltage() {
        return storage.getVoltage() * storage.getNumStorages();
    }

    public double getTotalHarvestedEnergy() {
        return totalHarvestedEnergy;
    }

    public void setTotalHarvestedEnergy(double energy_mj) {  // [iPAS]: for hacking only
        totalHarvestedEnergy = energy_mj;
    }
    
    /**
     * Constructor
     * @param nodeID
     * @param simulation
     * @param configFilePath
     */
    public EHSystem(EHNode ehNode, Simulation simulation, String configFilePath){

        //if (!logger.isEnabledFor(LOG_LEVEL))  // Log4J configuration file is in cooja/config/log4j_config.xml 
            logger.setLevel(LOG_LEVEL);
        
        this.simulation = simulation;
        this.ehNode = ehNode;

        /** 
         * Load configuration
         */
        Properties config = new Properties();
        try {
            FileInputStream fis = new FileInputStream(configFilePath);

            /** 
             * Replace "[APPS_DIR]", "[COOJA_DIR]", "CONTIKI_DIR" .. with real paths.             * 
             */            
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuffer sbuff = new StringBuffer();
            
            Pattern pattern = Pattern.compile("\\[[a-zA-Z_]+\\]");            
            String line;            
            while ((line = reader.readLine()) != null) {                
                Matcher matcher = pattern.matcher(line);  // Find
                if (matcher.find() == true) {
                    line = line.split("=")[0] + "=" + relativeToAbsolutePath(line.split("=")[1]);
                } 
                sbuff.append(line + "\n");
            }
            
            reader.close();
            fis.close();            
            config.load(new StringReader(sbuff.toString()));

        } catch (FileNotFoundException e) {
            logger.fatal("EHS Configuration file " + configFilePath + " could not be read! Exiting...");
            e.printStackTrace();
            System.exit(-1);

        } catch (IOException e) {
            logger.fatal("EHS Configuration file " + configFilePath + " could not be loaded! Exiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        /**
         * Initializations
         */
        
        // Initializing environment for energy source
        envDataProvider = new LightDataProvider(
                config.getProperty("source.environment.tracefile.path") + "/" + ehNode.getNodeLabel() + ".txt",
                config.getProperty("source.environment.tracefile.format.delimiter"),
                Integer.parseInt(config.getProperty("source.environment.tracefile.format.columnno")));
        chargeInterval = Double.parseDouble(  // in seconds, defines how frequently charge should be updated
                config.getProperty("source.environment.sampleinterval"));

        // Initializing energy source
        if (config.getProperty("source.type").equalsIgnoreCase("photovoltaic")) {            
            source = new PhotovoltaicCell(ehNode.getNodeLabel(),
                    config.getProperty("source.name"), 
                    config.getProperty("source.outputpower.lookuptable"));
            PhotovoltaicCell pvSource = (PhotovoltaicCell) source;
            pvSource.setNumCells(Integer.parseInt(config.getProperty("source.num")));
        }

        // Initializing Harvester
        harvester = new Harvester(ehNode.getNodeLabel(), 
                config.getProperty("harvester.name"),
                config.getProperty("harvester.efficiency.lookuptable"));

        //Initializing energy storage
        if (config.getProperty("storage.type").equalsIgnoreCase("battery")) {
            Battery battery = new Battery(ehNode.getNodeLabel(), 
                    config.getProperty("storage.name"), 
                    config.getProperty("storage.soc.lookuptable"),
                    Double.parseDouble(config.getProperty("battery.capacity")),
                    Double.parseDouble(config.getProperty("battery.nominalvoltage")),
                    Double.parseDouble(config.getProperty("battery.minoperatingvoltage")));
            battery.setNumBatteries(Integer.parseInt(config.getProperty("storage.num")));
            storage = battery;
        }
        //else if (config.getProperty("storage.type").equalsIgnoreCase("capacitor")){} //TODO        
    }
    
    /**
     * Relative path converter
     */
    private String relativeToAbsolutePath(String inPath) {                  
        File fi = this.simulation.getCooja().restorePortablePath(new File(inPath));
        return fi.getAbsolutePath(); 
    }
    
    /**
     * 
     */
    public void harvestCharge(){  
        /**
         * TODO: Check the units of different quantities 
         */
        
        // Read the next value from environmental trace file. 
        double envData = envDataProvider.getNext();  // Average luxs.  
        
        // Calculate the output power for the source for given environmental conditions
        /**
         * TODO: Do handle the out of range values of outputpower.
         * If envValue is too large, we should get maximum output power that can be taken from the source.
         * It should not be arbitrary large.
         */
        double sourceOutputPower =  // Source power in microWatts / 1000 = milliWatts
                source.getOutputPower(envData) / 1000;  
        double volts =              // Current cumulative voltage for all batteries
                storage.getVoltage() * storage.getNumStorages();  
        double harvEfficiency =     // Efficiency of the harvester at given volts and output power
                harvester.getEfficiency(sourceOutputPower, volts);
        double energy =             // The actual charge going into the battery in milli Joule 
                source.getOutputEnergy(envData, chargeInterval) * harvEfficiency / 1000; 

        storage.charge(energy);         // Add the charge to the battery
        totalHarvestedEnergy += energy; // Accumulate count
        
        if (ehNode.getNodeLabel() == 2)  // [iPAS] If be the overhearer 
        logger.debug(String.format("node %d harvested %.1f luxs, to bat. %.2f mJ (%.1f V), eff. %.1f %%.", 
                ehNode.getNodeLabel(), envData, energy, storage.getVoltage(), harvEfficiency*100));
    }

    /** To be called by EHNode.dischargeConsumption() periodically 
     *   to drain the power used by PowerConsumption and Leakage Models from Storage.
     *  TODO: However, the Leakage Model class have not implemented yet.
     */
    public void consumeCharge(double energyConsumed) {
        storage.discharge(energyConsumed);
    }
    
}
