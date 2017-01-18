import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private double totalHarvestedEnergy;

    private EnergySource source;
    private Harvester harvester;
    private EnergyStorage storage;
    private EnvironmentalDataProvider enviornmentalDataProvider;
    private double chargeInterval;
    
    private int nodeID;
    private Simulation simulation;

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
     * Relative path converter
     */
    private String relativeToAbsolutePath(String inPath) {    	    	    
		File fi = this.simulation.getCooja().restorePortablePath(new File(inPath));
		return fi.getAbsolutePath(); 
    }
    
    /**
     * Constructor
     * @param nodeID
     * @param simulation
     * @param configFilePath
     */
    public EHSystem(int nodeID, Simulation simulation, String configFilePath){                
        this.nodeID = nodeID;        
        this.simulation = simulation;
        totalHarvestedEnergy = 0;
        source 		= null;
        harvester 	= null;
        storage 	= null;
        enviornmentalDataProvider = null;

        // Load configuration
        Properties config = new Properties();
        try {
            FileInputStream fis = new FileInputStream(configFilePath);

            /** 
             * [iPAS]: Replace "[APPS_DIR]", "[COOJA_DIR]", "CONTIKI_DIR" .. with real paths.             * 
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
            System.err.println("Energy Harvesting System Configuration file " + configFilePath
                             + " could not be read.. Exiting...");
            System.exit(-1);

        } catch (IOException e) {
            System.err.println("Energy Harvesting System Configuration file " + configFilePath
                             + " could not be loaded.. Exiting...");
            System.exit(-1);
        }

        // Initializations
        
        // Initializing energy source
        if (config.getProperty("source.type").equalsIgnoreCase("photovoltaic")) {            
            source = new PhotovoltaicCell(
                    config.getProperty("source.name"), 
                    config.getProperty("source.outputpower.lookuptable"));
            PhotovoltaicCell pvSource = (PhotovoltaicCell) source;
            pvSource.setNumCells(Integer.parseInt(config.getProperty("source.num")));
        }

        // Initializing environment for energy source
        enviornmentalDataProvider = new LightDataProvider(
                config.getProperty("source.environment.tracefile.path") + "/" + (nodeID+1) + ".txt",
                config.getProperty("source.environment.tracefile.format.delimiter"),
                Integer.parseInt(config.getProperty("source.environment.tracefile.format.columnno")));
        chargeInterval = Double.parseDouble(  // in seconds, defines how frequently charge should be updated
                config.getProperty("source.environment.sampleinterval"));

        // Initializing Harvester
        harvester = new Harvester(
                config.getProperty("harvester.name"),
                config.getProperty("harvester.efficiency.lookuptable"));

        //Initializing energy storage
        if (config.getProperty("storage.type").equalsIgnoreCase("battery")) {
            Battery battery = new Battery(
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

    public void harvestCharge(){ // TODO: Check the units of different quantities
        // Read the next value from environmental trace file
        double envValue = enviornmentalDataProvider.getNext(); // average luxs

        // Calculate the output power for the source for given environmental conditions
        // TODO: handle the out of range values of outputpower.
        // If envValue is too large, we should get maximum output power that can be taken from source. 
        // It should not be arbitrary large
        double sourceOutputPower = source.getOutputPower(envValue) / 1000;  // microWatts / 1000 = milliWatts
        //System.out.println ("Power  = "+ sourceOutputPower + " mW");

        // Get current cumulative voltage for all batteries
        double volts = storage.getVoltage() * storage.getNumStorages();
        //System.out.println ("Current Voltage  = "+ volts + " V");

        // Get the efficiency of the harvester at given volts and output power
        double harvEfficiency = harvester.getEfficiency(sourceOutputPower, volts);
        //System.out.println ("harvester efficiency  = "+ (harvEfficiency *100)+ "%");

        // Calculating the charge actually going to the battery in milli Joule
        double energy = source.getOutputEnergy(envValue, chargeInterval) * harvEfficiency / 1000; // mJ

        if (nodeID == 0) {
            System.out.format("%d[%d]: harvestCharge()\n", nodeID, simulation.getSimulationTimeMillis());
            System.out.format("\tHarvested (Lux): %.2f\n", envValue);
            System.out.format("\tActual to Bat.(mJ): %.4f\n", energy);
            System.out.format("\tHarvester eff.(%%): %.1f\n", (harvEfficiency * 100));
        }
        
        // Add the charge to the battery
        storage.charge(energy);
        totalHarvestedEnergy += energy;
        //System.out.println (storage.getVoltage());
    }

    /** To be called by EHNode.dischargeConsumption() periodically 
     *   to drain the power used by PowerConsumption and Leakage Models from Storage.
     *  TODO: However, the Leakage Model class have not implemented yet.
     */
    public void consumeCharge(double energyConsumed) {
        storage.discharge(energyConsumed);
    }
}
