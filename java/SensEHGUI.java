import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JScrollPane;

import org.apache.log4j.Logger;
import org.jdom.Element;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.TimeEvent;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.dialogs.MessageList;
import se.sics.cooja.interfaces.Radio;
import se.sics.cooja.util.StringUtils;

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
 * @author ipas
 * @since 2015-05-01
 */
@ClassDescription("SensEH GUI")
@PluginType(PluginType.SIM_PLUGIN)
public class SensEHGUI extends VisPlugin {
    private static Logger logger = Logger.getLogger(SensEHGUI.class);

    private Simulation simulation;
    private EHNode [] ehNodes;

    private long startTime; // uS
    private long lastUpdateTime; // uS
    private ChargeUpdateEvent chargeUpdateEvent;
    private long totalUpdates;

    private File ehConfigFile = null;

    public MessageList log = new MessageList();  // [iPAS]: Changing to public for accessing by EHNode
    public static final boolean QUIET = false;  // [iPAS]: Changing to public for accessing by EHNode


    public SensEHGUI(Simulation simulation, final GUI gui) {
        super("SensEH Plugin", gui, false);
        this.simulation = simulation;
        //consumption = new Consumption(simulation);

        log.addPopupMenuItem(null, true);  // Create message list popup
        add(new JScrollPane(log));

        if (!QUIET) {
            log.addMessage(
                    "Harvesting plugin started at (ms): " + simulation.getSimulationTimeMillis());
            logger.info(
                    "Harvesting plugin started at (ms): " + simulation.getSimulationTimeMillis());
        }
        setSize(500, 200);
    }

    @Override
    public void startPlugin() {
        super.startPlugin();

        if (ehConfigFile != null)
            return;

        JFileChooser fileChooser = new JFileChooser();
        File suggest = new File(GUI.getExternalToolsSetting("DEFAULT_EH_CONFIG",
                "/home/user/contiki-2.7/tools/cooja/apps/senseh/config/EH.config"));
        fileChooser.setSelectedFile(suggest);
        fileChooser.setDialogTitle("Select configuration file for harvesting system");
        int reply = fileChooser.showOpenDialog(GUI.getTopParentContainer());

        if (reply == JFileChooser.APPROVE_OPTION) {
            ehConfigFile = fileChooser.getSelectedFile();
            GUI.setExternalToolsSetting("DEFAULT_EH_CONFIG", ehConfigFile.getAbsolutePath());
        }

        if (ehConfigFile == null) {
            throw new RuntimeException("No configuration file for harvesting system");
        }

        init(ehConfigFile.getAbsolutePath());
    }

    void init(String configFilePath) {
        //setTitle("~~~ TITLE ~~~");
        ehNodes = new EHNode[simulation.getMotesCount()];
        for (int i = 0; i < simulation.getMotesCount(); i++)
            ehNodes[i] = new EHNode(i, simulation, configFilePath, this);
        schedulePeriodicChargeUpdate(); // schedule event to update the charge of all the nodes
    }

    private void schedulePeriodicChargeUpdate() {
        simulation.invokeSimulationThread(new ChargeUpdateTaskScheduler());
    }

    private double getChargeInterval() { // assume that charge update interval for ALL nodes is equal
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
            totalUpdates = 1;
            startTime = simulation.getSimulationTime();
            lastUpdateTime = 0; // It means never updated before.
            //logger.debug("periodStart: " + periodStart);
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
            // Detect early events: reschedule for later
            //System.out.println ("t\t"+t + "\tSimTime\t"+simulation.getSimulationTime());
            if (simulation.getSimulationTime() < t) {
                simulation.scheduleEvent(this, startTime + (long)(totalUpdates * getChargeInterval() * 1000000));
                return;
            }

            lastUpdateTime = simulation.getSimulationTime();
            /**
             * SensEH does NOT continuously count harvested energies and consumed energies.
             * It updates the charges periodically on every interval.
             */
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
            File file = simulation.getGUI().createPortablePath(ehConfigFile);
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
                ehConfigFile = simulation.getGUI().restorePortablePath(new File(element.getText()));
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

            int nodeLabel  = node.getNodeID();
            EnergyStorage storage = node.getEHSystem().getStorage();
            EHSystem ehsys = node.getEHSystem();
            PowerConsumption consumption = node.getPowerConsumption();

            sb.append("node="   + (nodeLabel+1) + ", ");
            sb.append("sto:mJ=" + storage.getEnergy() + ", ");
            sb.append("eh:mJ="  + ehsys.getTotalHarvestedEnergy() + ", ");
            sb.append(consumption.getSnappedStatistics());
            sb.append("\n");
        }

        return sb.toString();
    }

}
