import org.apache.log4j.Logger;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.Mote;
import se.sics.cooja.MoteTimeEvent;
import se.sics.cooja.Simulation;
import se.sics.cooja.mspmote.MspMote;
import se.sics.cooja.mspmote.SkyMote;
import se.sics.cooja.radiomediums.UDGM;
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

    private static Logger logger = Logger.getLogger(EHNode.class);

    private Simulation simulation;
    private int nodeID;
    private Mote mote;
	
    private EHSystem ehSys;
    private Pin storageMotePin;
    private PowerConsumption consumption;

    private boolean wasDepleted = false;
    
    private double lastEnergyConsumed;
    private double lastTotalEnergyConsumed;

    private String configFilePath;
    private SensEHGUI senseh;


    public EHSystem getEHSystem() {
        return ehSys;
    }

    public PowerConsumption getPowerConsumption() {
        return consumption;
    }

    public int getNodeID() {
        return nodeID;
    }

    public double getLastEnergyConsumed() {
        return lastEnergyConsumed;
    }

    public double getLastTotalEnergyConsumed() {
        return lastTotalEnergyConsumed;
    }

    public EHNode(int nodeID, Simulation simulation, String configFilePath, SensEHGUI senseh) {
        this.nodeID     = nodeID;
        this.simulation = simulation;
        this.mote       = simulation.getMote(nodeID);
        this.configFilePath = configFilePath;
        this.senseh     = senseh;

        ehSys           = new EHSystem(nodeID, simulation, configFilePath);
        storageMotePin  = new Pin(ehSys.getStorage(), (SkyMote) mote);
        consumption     = new PowerConsumption(simulation, mote, ehSys.getVoltage());
    }

    public void updateCharge(){  // [iPAS]: the EH system model of the node
        chargeStorage();
        dischargeConsumption();
        consumption.setVoltage(ehSys.getVoltage());  // Assume that it's fixed, and regulated.
        											 // But, in some case, the voltage may be varied after discharged.
        
        // TODO [iPAS]: Save historical data into database for off-line analysis
        
        if (nodeID == 0) 
        	System.out.format("%d[%d]: stored energy (mJ) %.4f\n", 
        	        nodeID, simulation.getSimulationTimeMillis(), getEHSystem().getStorage().getEnergy());
        
        
        if (((Battery) ehSys.getStorage()).isDepleted()) {  // Is the node depleted? Yes.
            
            if (nodeID == 0)
        	if (!SensEHGUI.QUIET && wasDepleted == false) {        		
                String str = String.format("%d[%d]: bat is empty!", mote.getID(),simulation.getSimulationTimeMillis());
                this.senseh.log.addMessage(str);
                logger.info(str);                
            }        	
            
        	wasDepleted = true;
        	
        	
        	
        	/**
        	 * https://sourceforge.net/p/contiki/mailman/message/25273631/
        	 * https://sourceforge.net/p/contiki/mailman/message/27181941/
        	 * 
        	 * https://sourceforge.net/p/contiki/mailman/message/32354187/
        	 */
        	
        	    
        	
        	if (nodeID == 1) {         	    
        	    //simulation.removeMote(mote);  // Hang after all
        	    //UDGM rm = (UDGM) simulation.getRadioMedium();
        	    //rm.dgrm.requestEdgeAnalysis();    
        	    //rm.dgrm.requestEdgeAnalysis();
        	    
        	}
        	
        	
        	/*
            // TODO [iPAS]: Stop mote if drained out.
            if (cpu.isRunning() == true) {
                cpu.reset();
                cpu.stop();

                // TODO [iPAS]: What about the transceiver?
            }
            */
        	
        	//((SkyMote) mote).stopNextInstruction();  // Make no message in the queue
            //((SkyMote) mote).getCPU().reset();
//            ((SkyMote) mote).mspNode.stop();
//            ((SkyMote) mote).executeCLICommand("stop");
        	
//        	try {
//                mote.wait(simulation.getSimulationTimeMillis() + (long)(ehSys.getChargeInterval() * 1000));
//            } catch (InterruptedException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
        	
//            ((SkyMote) mote).executeCLICommand("reset");
//        	((SkyMote) mote).scheduleNextWakeup(
//        			simulation.getSimulationTime() + (long)(ehSys.getChargeInterval() * 1000000)		
//        			);  // Guess the time to wake up after accumulating energy
        	
        	        	
        } else if (wasDepleted) {  // Currently, it is not depleted. But it was.
            
            if (nodeID == 0)
            if (!SensEHGUI.QUIET) {             
                String str = String.format("%d[%d]: bat is refilled", mote.getID(),simulation.getSimulationTimeMillis());
                this.senseh.log.addMessage(str);
                logger.info(str);
            }     
            
            wasDepleted = false;
            
//            simulation.addMote(mote);
        	
//            ((SkyMote) mote).getCPU().reset();
//            ((SkyMote) mote).requestImmediateWakeup();

//            ((SkyMote) mote).executeCLICommand("start");
//            ((SkyMote) mote).mspNode.start();
        }
    }

    private void chargeStorage(){
        ehSys.harvestCharge();
    }

    private void dischargeConsumption(){
        double energyConsumed = ehSys.getChargeInterval()  /*sec*/
                              * consumption.getAveragePower()  /*mW*/;
        consumption.snapStatistics();  // Snap the consumed energy at the time
        ehSys.consumeCharge(energyConsumed);
        consumption.reset();
    }

}
