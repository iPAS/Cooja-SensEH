import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JScrollPane;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

/**
 * SensEH Project
 * Originated by
 * @author raza
 * @see http://usmanraza.github.io/SensEH-Contiki/
 *
 * 'SensEHGUI' is a main module which has been run before the simulation begin.
 *
 * Adopted and adapted by
 * @author iPAS
 * @see https://github.com/iPAS
 * @since 2015-05-01
 */
@ClassDescription("SensEH")
@PluginType(PluginType.SIM_PLUGIN)
public class SensEH extends VisPlugin {
    
    private static final Level LOG_LEVEL = Level.DEBUG;
    private static Logger logger = Logger.getLogger(SensEH.class);

    private Simulation simulation;
    private EHNode [] ehNodes;

    private long startTime; // uS
    private long lastUpdateTime; // uS
    private ChargeUpdateEvent chargeUpdateEvent;
    private long totalUpdates;

    private MessageListUI messageShowUI = new MessageListUI();
    private File ehConfigFile = null;
    
    
    /**
     * Public methods
     */
    public void showMessage(String msg) {
        messageShowUI.addMessage(msg);
    }
        
    /**
     * Constructor 
     * 
     * @param simulation
     * @param gui
     */
    public SensEH(Simulation simulation, final Cooja gui) {
        super("SensEH Plugin", gui, false);
        
        if (!logger.isEnabledFor(LOG_LEVEL)) 
            logger.setLevel(LOG_LEVEL);
        
        this.simulation = simulation;
        
        messageShowUI.addPopupMenuItem(null, true);  // Create message list popup
        add(new JScrollPane(messageShowUI));

        setSize(500, 200);
        
        logger.info("SensEH plugin created!");
        showMessage("SensEH plugin created!");
    }

    @Override
    public void startPlugin() {
        super.startPlugin();

        if (ehConfigFile != null)
            return;

        JFileChooser fileChooser = new JFileChooser();
        File suggest = new File(Cooja.getExternalToolsSetting("DEFAULT_EH_CONFIG",
                "/home/user/contiki-2.7/tools/cooja/apps/senseh/config/EH.config"));
        fileChooser.setSelectedFile(suggest);
        fileChooser.setDialogTitle("Select configuration file for harvesting system");
        int reply = fileChooser.showOpenDialog(Cooja.getTopParentContainer());

        if (reply == JFileChooser.APPROVE_OPTION) {
            ehConfigFile = fileChooser.getSelectedFile();
            Cooja.setExternalToolsSetting("DEFAULT_EH_CONFIG", ehConfigFile.getAbsolutePath());
        }

        if (ehConfigFile == null) {
            throw new RuntimeException("No configuration file for harvesting system");
        }

        init(ehConfigFile.getAbsolutePath());
    }

    void init(String configFilePath) {
        //setTitle("~~~ TITLE ~~~");
        ehNodes = new EHNode[simulation.getMotesCount()];
        for (int id = 0; id < simulation.getMotesCount(); id++)
            ehNodes[id] = new EHNode(this, id, simulation, configFilePath);
        schedulePeriodicChargeUpdate();  // schedule event to update the charge of all the nodes
    }

    private void schedulePeriodicChargeUpdate() {
        simulation.invokeSimulationThread(new ChargeUpdateTaskScheduler());
    }

    private double getChargeInterval() {  // assume that charge update interval for ALL nodes is equal
        return ehNodes[0].getEHSystem().getChargeInterval();
    }

    @Override
    public void closePlugin() {
        if (chargeUpdateEvent != null) {
            chargeUpdateEvent.remove();

            for (int i = 0; i < ehNodes.length; i++) {
                ehNodes[i].getPowerConsumption().dispose();
            }
        }
    }

    // --------------------------------------------------------------------------
    private class ChargeUpdateTaskScheduler implements Runnable {

        @Override
        public void run() {
            logger.debug("ChargeUpdateTaskScheduler.run() @" + simulation.getSimulationTime());
            
            totalUpdates = 1;
            startTime = simulation.getSimulationTime();
            lastUpdateTime = 0; // It means never updated before.
            chargeUpdateEvent = new ChargeUpdateEvent(0);
            chargeUpdateEvent.execute(startTime + (long)(getChargeInterval() * 1000000));
        }

    }

    // --------------------------------------------------------------------------
    private class ChargeUpdateEvent extends TimeEvent{

        public ChargeUpdateEvent(long t) {
            super(t, "charge update event");
        }

        @Override
        public void execute(long t) {
            logger.debug("ChargeUpdateEvent.execute() @" + simulation.getSimulationTime());
            
            if (simulation.getSimulationTime() < t) {  // Detect early events: reschedule for later
                simulation.scheduleEvent(this, startTime + (long)(totalUpdates * getChargeInterval() * 1000000));
                return;
            }
            lastUpdateTime = simulation.getSimulationTime();
            
            // SensEH does NOT continuously count harvested energies and consumed energies.
            // It updates the charges periodically on every interval.
            for (EHNode node : ehNodes) {
                node.updateCharge();  // charge with harvested energy, and, discharge with consumed energy
            }

            // Now schedule the next event
            totalUpdates++;
            long nextEventTime = startTime + (long)(totalUpdates * getChargeInterval() * 1000000);
            if (simulation.getSimulationTime() <  nextEventTime) {
                simulation.scheduleEvent(this, nextEventTime);
                return;
            }
        }

    }

    // --------------------------------------------------------------------------
    @Override
    public Collection<Element> getConfigXML() {
        ArrayList<Element> configXML = new ArrayList<Element>();
        Element element;

        if (ehConfigFile != null) {
            element = new Element("eh_config_file");
            File file = simulation.getCooja().createPortablePath(ehConfigFile);
            element.setText(file.getPath().replaceAll("\\\\", "/"));
            element.setAttribute("EXPORT", "copy");
            configXML.add(element);
        }

        return configXML;
    }

    @Override
    public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
        for (Element element : configXML) {
            String name = element.getName();

            if (name.equals("eh_config_file")) {
                ehConfigFile = simulation.getCooja().restorePortablePath(new File(element.getText()));
                init(ehConfigFile.getAbsolutePath());
            }
        }

        return true;
    }

    // --------------------------------------------------------------------------
    public void restartStoredEnergyStatistics() {
        for (EHNode node : ehNodes) {
            EnergyStorage storage = node.getEHSystem().getStorage();
            storage.discharge(storage.getEnergy() * storage.getNumStorages()); // Drain all amount
        }
    }

    public void restartHarvestedEnergyStatistics() {
        for (EHNode node : ehNodes) {
            EHSystem ehsys = node.getEHSystem();
            ehsys.setTotalHarvestedEnergy(0);
        }
    }

    public void restartConsumedEnergyStatistics() {
        for (EHNode node : ehNodes) {
            PowerConsumption consumption = node.getPowerConsumption();
            consumption.restart(); // Reset total consumption
        }
    }

    // --------------------------------------------------------------------------
    public String radioStatistics() {
        return radioStatistics(true, true, false);
    }

    public String radioStatistics(boolean radioHW, boolean radioRXTX, boolean onlyAverage) {
        StringBuilder sb = new StringBuilder();

        /* Average */
        PowerConsumption.RadioTimes radioTimesAVG = new PowerConsumption.RadioTimes(0); // omitting tx levels
        for (EHNode node : ehNodes) {
            PowerConsumption consumption = node.getPowerConsumption();
            radioTimesAVG.addMembersWith(consumption.radioTimesTotal);
        }
        radioTimesAVG.divMembersWith(ehNodes.length); // Average it!
        sb.append(PowerConsumption.makeRadioSummaryStatistics(radioHW, radioRXTX,
                false /* not show duration */, true /* not show idle */, radioTimesAVG, "AVG "));

        /* All nodes */
        if (!onlyAverage)
            for (EHNode node : ehNodes) {
                PowerConsumption consumption = node.getPowerConsumption();
                sb.append(consumption.radioStatistics());
            }

        return sb.toString();
    }

    public String radioTxStatistics() {
        StringBuilder sb = new StringBuilder();
        for (EHNode node : ehNodes) {
            PowerConsumption consumption = node.getPowerConsumption();
            sb.append(consumption.radioTxStatistics());
        }
        return sb.toString();
    }

    // --------------------------------------------------------------------------
    public String getStatistics() {
        return getStatistics("");
    }

    public String getStatistics(String prefix) {
        StringBuilder sb = new StringBuilder();

        for (EHNode node : ehNodes) {
            sb.append(prefix);
            sb.append("update:us=" + lastUpdateTime + ", ");

            int nodeLabel = node.getNodeID()+1;
            EnergyStorage storage = node.getEHSystem().getStorage();
            EHSystem ehsys = node.getEHSystem();
            PowerConsumption consumption = node.getPowerConsumption();

            sb.append("node="   + nodeLabel + ", ");
            sb.append("sto:mJ=" + storage.getEnergy() + ", ");
            sb.append("eh:mJ="  + ehsys.getTotalHarvestedEnergy() + ", ");
            sb.append(consumption.getSnappedStatistics());
            sb.append("\n");
        }

        return sb.toString();
    }

}
