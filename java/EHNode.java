import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.mspmote.SkyMote;
import org.contikios.cooja.radiomediums.UDGM;
import se.sics.mspsim.core.MSP430;


/**
 * SensEH Project
 * Originated by
 * @author raza
 * @see http://usmanraza.github.io/SensEH-Contiki/
 *
 * Adopted and adapted by
 * @author ipas
 * @since 2015-05-01
 */
@ClassDescription("A node with Energy Harvesting")
public class EHNode{

    private static final Level LOG_LEVEL = Level.DEBUG;
    private static Logger logger = Logger.getLogger(EHNode.class);

    private Simulation simulation;
    private int nodeID;
    private int nodeLabel;
    private Mote mote;
	
    private EHSystem ehSys;
    private Pin storageMotePin;
    private PowerConsumption consumption;

    private boolean wasDepleted = false;
    
    private double lastEnergyConsumed;
    private double lastTotalEnergyConsumed;

    private String configFilePath;
    private SensEH senseh;

    
    /**
     * Public methods
     */
    public EHSystem getEHSystem() {
        return ehSys;
    }

    public PowerConsumption getPowerConsumption() {
        return consumption;
    }

    public int getNodeID() {
        return nodeID;
    }
    
    public int getNodeLabel() {
        return nodeLabel;
    }

    public double getLastEnergyConsumed() {
        return lastEnergyConsumed;
    }

    public double getLastTotalEnergyConsumed() {
        return lastTotalEnergyConsumed;
    }

    /**
     * Constructor
     * 
     * @param nodeID
     * @param simulation
     * @param configFilePath
     * @param senseh
     */
    public EHNode(SensEH senseh, int nodeID, Simulation simulation, String configFilePath) {

        if (!logger.isEnabledFor(LOG_LEVEL)) 
            logger.setLevel(LOG_LEVEL);
        
        this.nodeID     = nodeID;
        this.nodeLabel  = nodeID+1;
        this.simulation = simulation;
        this.mote       = simulation.getMote(nodeID);
        this.configFilePath = configFilePath;
        this.senseh     = senseh;

        ehSys           = new EHSystem(this, simulation, configFilePath);
        storageMotePin  = new Pin(ehSys.getStorage(), (SkyMote) mote);
        consumption     = new PowerConsumption(simulation, mote, ehSys.getVoltage());        
    }

    public void updateCharge() {  // [iPAS]: the EH system model of the node
        chargeStorage();
        dischargeConsumption();
        consumption.setVoltage(ehSys.getVoltage());  // Assume that it's fixed, and regulated.
        											 // But, in some case, the voltage may be varied after discharged.
        
        /** 
         * TODO [iPAS]:
         *  1. Simulate the node be alive or dead in case of refilled and perished energy respectively.
         *  2. Save historical data into database for off-line analysis 
         *  
         * Information sources about adding/removing a node from simulation:
         *     https://sourceforge.net/p/contiki/mailman/message/25273631/
         *     https://sourceforge.net/p/contiki/mailman/message/27181941/       
         *     https://sourceforge.net/p/contiki/mailman/message/32354187/
         */
        
        
        /**
         * Death of the node
         */
        if (((Battery) ehSys.getStorage()).isDepleted()) {  // Is the node depleted? Yes.            
            if (wasDepleted == false) {
                
                String msg = "node " + nodeLabel + " was depleted!\n";
                senseh.showMessage(msg + " @" + simulation.getSimulationTime());                
                logger.debug(msg);                    
                
                wasDepleted = true;            
                
        	    /**
        	     * Simulate node death 
        	     */                
        	    simulation.removeMote(mote);  // Removing from the Simulation
        	    //RadioMedium rm = simulation.getRadioMedium();
        	    
        	    // Reset node's CPU
        	    //if (cpu.isRunning() == true) {
                //    cpu.reset();
                //    cpu.stop();
        	    //}
        	    
        	    // Function calls that might help to reset the node
    	        //((SkyMote) mote).stopNextInstruction();  // Make no message in the queue
                //((SkyMote) mote).getCPU().reset();
                //((SkyMote) mote).mspNode.stop();
                //((SkyMote) mote).executeCLICommand("stop");
        	    
                //((SkyMote) mote).executeCLICommand("reset");
                //((SkyMote) mote).scheduleNextWakeup(
                //      simulation.getSimulationTime() + (long)(ehSys.getChargeInterval() * 1000000)        
                //      );  // Guess the time to wake up after accumulating energy
            }
        	
        /**
         * Rebirth of the node
         */
        } else if (wasDepleted) {  // Currently, it is not depleted. But it was.
            
            String msg = "node " + nodeLabel + " was refilled!\n";
            senseh.showMessage(msg + " @" + simulation.getSimulationTime());            
            logger.debug(msg);
            
            wasDepleted = false;            

//              simulation.addMote(mote);
              
//              ((SkyMote) mote).getCPU().reset();
//              ((SkyMote) mote).requestImmediateWakeup();

//              ((SkyMote) mote).executeCLICommand("start");
//              ((SkyMote) mote).mspNode.start();
                
                
//                Mote mote = mt.generateMote(sim);
//                
//                //Position at random place for a start
//                // TODO use positioner?
//                double x = (Math.random() * 10000) % 15;
//                double y = (Math.random() * 10000) % 15;
//                mote.getInterfaces().getPosition().setCoordinates(x, y, 0);
//                mote.getInterfaces().getMoteID().setMoteID(id);
//                
//                //Add after everything is configured
//                sim.addMote(mote);                
             
        }
    }

    private void chargeStorage(){
        ehSys.harvestCharge();
    }

    private void dischargeConsumption(){
        double energyConsumed = ehSys.getChargeInterval() /*second*/ * consumption.getAveragePower() /*mW*/;
        consumption.snapStatistics();  // Snap the consumed energy at the time
        ehSys.consumeCharge(energyConsumed);
        consumption.reset();
    }

}
