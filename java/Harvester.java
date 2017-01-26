/**
 * SensEH Project Originated by
 * 
 * @author raza
 * @see http://usmanraza.github.io/SensEH-Contiki/
 *
 *      Adopted and adapted by
 * @author ipas
 * @since 2015-05-01
 */
public class Harvester {

    private LookupTable3D EfficiencyLUT;

    /**
     * Constructor
     * Actually, it is a model of an energy harvester, also called solar charging controller, 
     *  the electronic circuit that converts environmental energy such solar to electrical energy.
     * In case of solar panel, an efficiency depends on both the output voltage and energy drained from it.
     * This answers the question: "Why does MPPT (Maximum Power Point Tracking) technique is important.
     * 
     * @param nodeLabel
     * @param name
     * @param lookupTableFile
     */
    public Harvester(int nodeLabel, String name, String lookupTableFile) {
        EfficiencyLUT = new LookupTable3D(nodeLabel, name, lookupTableFile);
    }

    public double getEfficiency(double inputPower_mW, double batteryVoltage) {
        return EfficiencyLUT.getZ(inputPower_mW, batteryVoltage);
    }

    // --------------------------------------------------------------------------
    /**
     * Main for testing
     * 
     * @param args
     */
    public static void main(String[] args) {
        Harvester multiHarvester = new Harvester(0, "Multi-Harvester",
                System.getProperty("user.dir") + "/../config/EnergyHarvesters/Multiharvester.lut");
        for (double voltage = 2.00; voltage <= 2.5; voltage += 0.05)
            for (double inputPower = 0.0; inputPower <= 300.0; inputPower += 25.0) {
                System.out.println(String.format("Efficiency(%.2f mW, %.2f V)\t%.4f", 
                        inputPower, voltage, multiHarvester.getEfficiency(inputPower, voltage)));
            }
    }

}
