import org.apache.log4j.Level;
import org.apache.log4j.Logger;

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

    private static final Level LOG_LEVEL = Level.DEBUG;
    private static Logger logger = Logger.getLogger(Battery.class);
    
    private String name;
    private int numBatteries;
    private LookupTable chargeVoltageLUT;
    
    private double CAPACITY;
    /* CAPACITY, a.k.a maxCharge (mAh)
     *  AA Ni-MH battery capacity vary with manufacturer, i.e., 1100 - 2700 mAh.
     *  ENERGIZER NH15-2300 ------ 2300 mAh http://data.energizer.com/PDFs/nh15-2300.pdf
     *  Duracell DX1500---- 2400 mAh http://professional.duracell.com/downloads/datasheets/product/Rechargeable%20Cells/Rechargeable-Cells_Rechargeable_AA.pdf
     *  Ansmann -- 2700 http://datasheet.octopart.com/5030852-Ansmann-datasheet-5400527.pdf
     *  
     * Note: 
     *  At 0.2C discharge rate = will discharge in 1/0.2 = 5 hours
     */
    
    private double NOMINAL_VOLTAGE;
    private double MIN_OPERATING_VOLTAGE;
    private double maxEnergy;   // mJ
    private double energy;      // mJ
    
    private EHSystem ehs;
    

    /**
     * Constructor
     * 
     * @param storageName
     * @param chargeVoltageLookupTableFile
     * @param capacity
     * @param nominalVoltage
     * @param minVoltage
     */
    public Battery(EHSystem ehs,
            String storageName, String chargeVoltageLookupTableFile, 
            double capacity, double nominalVoltage, double minVoltage) {
    
        if (!logger.isEnabledFor(LOG_LEVEL)) 
            logger.setLevel(LOG_LEVEL);
        
        this.ehs = ehs;
        this.name = storageName;
        numBatteries    = 1;
        chargeVoltageLUT = new LookupTable(storageName, chargeVoltageLookupTableFile);
        
        CAPACITY        = capacity;  // mAh
        NOMINAL_VOLTAGE = nominalVoltage;
        MIN_OPERATING_VOLTAGE   = minVoltage;        
        maxEnergy       = CAPACITY * 3600 * NOMINAL_VOLTAGE;  // mA·h x 3600 x V --> mW·s (mJ)
        energy          = maxEnergy; 
        
        int label = (ehs == null)? -1 : ehs.getEHNode().getNodeLabel();
        logger.debug(String.format("node %d with bat. %s initialized: cap. %f mAh, %.2f V of nominal %.2f V, %f mJ",
                label, storageName, CAPACITY, getVoltage(energy), NOMINAL_VOLTAGE, energy));        
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

    double getSoC() {  // return voltage in mV
        return getVoltage();
    }

    @Override
    double getVoltage() {  // @see EnergyStorage#getVoltage()
        return getVoltage(energy);
    }

    double getVoltage(double energy_mj) {
        double chrg = getCharge(energy_mj);
        return chargeVoltageLUT.getY(chrg);
    }

    @Override
    public double getEnergy() {  // return in mJ
        return energy;
    }

    public double getCharge() {
        return getCharge(energy);
    }

    private double getCharge(double energy_mj) {  // Returns the charge in mAh
        return energy_mj / (NOMINAL_VOLTAGE * 3600);
    }

    public double residualBatteryPercentage() {  // Residue Battery in Percentage
        return 100 * getCharge() / CAPACITY;
    }

    // --------------------------------------------------------------------------
    @Override
    void charge(double energy_mj) {  // @see EnergyStorage#charge()
        energy += (energy_mj / numBatteries); // Drain an equally divided energy from every battery
        if (energy > maxEnergy) {
            energy = maxEnergy;
        }
    }

    @Override
    void discharge(double energy_mj) {  // @see EnergyStorage#discharge()
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
        Battery nimh = new Battery(null,
                "Ni-Mh",
                "/home/raza/raza@murphysvn/code/java/eclipseIndigo/Senseh/EnergyStorages/Ni-Mh.lut",
                2500.0, 1.2, 1.0);
        //System.out.println("Initial Charge: "+ nimh.getCharge() + " mAh");
        //System.out.println("Initial Voltage: "+ nimh.getVoltage());
        //nimh.setMaxEnergy(9000000);
        nimh.setNumBatteries(2);
        for (int week = 1; week <= 100; week++) {
            nimh.discharge(88 * 1000 * 7);
            System.out.println(String.format("%f\t%f\t%f\t%b", 
                    nimh.getCharge(), nimh.getVoltage(), nimh.residualBatteryPercentage(), nimh.isDepleted()));
        }
    }

}
