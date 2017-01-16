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
public class Harvester {

    private LookupTable3D EfficiencyLUT;

    public Harvester(String name, String lookupTableFile) {
        EfficiencyLUT = new LookupTable3D(name, lookupTableFile );
    }

    public double getEfficiency(double inputPower, double batteryVoltage){
        return EfficiencyLUT.getZ(inputPower, batteryVoltage);
    }


    // --------------------------------------------------------------------------
    /**
     * Main for testing
     * @param args
     */
    public static void main(String[] args) {
        Harvester multiHarvester = new Harvester(
                "Multi-Harvester",
                "/home/raza/raza@murphysvn/code/java/eclipseIndigo/Senseh/EnergyHarvesters/Multiharvester.lut");
        for (double voltage = 2.00; voltage <= 2.5; voltage += 0.05)
            for (double inputPower = 0.00; inputPower <= 300.0; inputPower += 25.0) {
                System.out.print("Efficiency(" + voltage + "," + inputPower + ")\t");
                System.out.println(multiHarvester.getEfficiency(voltage, inputPower));
            }
    }

}
