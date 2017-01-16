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
public class Battery extends EnergyStorage {

    private String name;
    private int numBatteries;

    /*
     * CAPACITY, a.k.a maxCharge (mAh)
     * AA Ni-MH battery capacity vary with manufacturer 1100 - 2700
     * ENERGIZER NH15-2300 ------ 2300 mAh http://data.energizer.com/PDFs/nh15-2300.pdf
     * Duracell DX1500---- 2400 mAh http://professional.duracell.com/downloads/datasheets/product/Rechargeable%20Cells/Rechargeable-Cells_Rechargeable_AA.pdf
     * Ansmann -- 2700 http://datasheet.octopart.com/5030852-Ansmann-datasheet-5400527.pdf
     * capacities noted at 0.2c discharge rate = will discharge in 1/0.2 = 5 hours
     */
    private double CAPACITY;

    private double maxEnergy;  //mJ
    private double energy;  //mJ

    private LookupTable chargeVoltageLUT;
    private double NOMINAL_VOLTAGE;
    private double MIN_OPERATING_VOLTAGE;


    public Battery(String name, String chargeVoltageLookupTableFile,
            double capacity, double nominalVoltage, double minVoltage) {
        this.name = name;
        chargeVoltageLUT = new LookupTable(name, chargeVoltageLookupTableFile);
        numBatteries = 1;
        CAPACITY = capacity;  // mAh
        NOMINAL_VOLTAGE = nominalVoltage;
        energy = maxEnergy = CAPACITY * 3600 * NOMINAL_VOLTAGE;  // mA·h x 3600 x V --> mW·s (mJ)
        MIN_OPERATING_VOLTAGE = minVoltage;

        System.out.println(name + " Battery is intialized with:" +
                "\nCharge: "         + CAPACITY + " mAh" +
                "\nVoltage: "        + getVoltage(energy) + " V" +
                "\nNominalVoltage: " + NOMINAL_VOLTAGE + " V" +
                "\nEnergy: "         + energy + " mJ");
    }

    public void setNumBatteries(int numBatteries){
        this.numBatteries = numBatteries;
    }

    @Override
    public int getNumStorages(){
        return numBatteries;
    }

    public void setCapacity(double maxCharge){
        this.CAPACITY = maxCharge;
    }

    double getSoC (){ // return voltage in mV
        return getVoltage();
    }

    /*
     * @see EnergyStorage#getVoltage()
     * in mV?
     */
    @Override
    double getVoltage() {
        return getVoltage(energy);
    }

    double getVoltage (double energy_mj){
        double chrg = getCharge(energy_mj);
        return chargeVoltageLUT.getY(chrg);
    }

    @Override
    public double getEnergy() { // return in mJ
        return energy;
    }

    public double getCharge() {
        return getCharge(energy);
    }

    private double getCharge(double energy_mj) { // Returns the charge in mAh
        return energy_mj / (NOMINAL_VOLTAGE * 3600);
    }

    public double residualBatteryPercentage() { // Residual Battery in Percentage
        return 100 * getCharge() / CAPACITY;
    }

    // --------------------------------------------------------------------------
    /*
     * @see EnergyStorage#charge()
     */
    @Override
    void charge(double energy_mj) {
        energy += (energy_mj / numBatteries); // Drain an equally divided energy from every battery
        if (energy > maxEnergy) {
            energy = maxEnergy;
        }
    }

    /*
     * @see EnergyStorage#discharge()
     */
    @Override
    void discharge(double energy_mj) {
        energy -= (energy_mj / numBatteries); // Drain an equally divided energy from every battery
        if (energy < 0) {
            energy = 0;
        }
    }

    public boolean isDepleted() { // 'true' if the voltage is less than the minimum operating voltage.
        if (getVoltage() <= MIN_OPERATING_VOLTAGE) {
            return true;
        }
        return false;
    }

    public double getDischarge() {
        return CAPACITY - this.getCharge();
    }


    // --------------------------------------------------------------------------
    // Test: Checking the voltage drop for the node in transition/ Depletion =  88 J/day  without energy harvester
    public static void main(String[] args) {
        Battery nimh = new Battery(
                "Ni-Mh",
                "/home/raza/raza@murphysvn/code/java/eclipseIndigo/Senseh/EnergyStorages/Ni-Mh.lut",
                2500.0, 1.2, 1.0);
        //System.out.println("Initial Charge: "+ nimh.getCharge() + " mAh");
        //System.out.println("Initial Voltage: "+ nimh.getVoltage());
        //nimh.setMaxEnergy(9000000);
        nimh.setNumBatteries(2);
        for (int week = 1; week <= 100; week++) {
            nimh.discharge(88 * 1000 * 7);
            System.out.println(
                    nimh.getCharge() + "\t" +
                            nimh.getVoltage() + "\t" +
                            nimh.residualBatteryPercentage() + "\t" +
                            nimh.isDepleted());
        }
    }

}
