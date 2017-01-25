/**
 * SensEH Project
 * Originated by
 *
 * @author raza
 * @see http://usmanraza.github.io/SensEH-Contiki/
 *
 *      Adopted and adapted by
 * @author ipas
 * @since 2015-05-01
 */
public class PhotovoltaicCell extends EnergySource {

    /**
     * Implementation of Sanyo Amorton AM-1816 Photovoltaic Cell
     * Should there also be maximum power you can extract from the solar cell?
     */
    private LookupTable lxPwrLUT;
    private int         numCells;

    
    /**
     * Constructor 
     * 
     * @param model
     * @param lookupTableFile
     */
    public PhotovoltaicCell(int nodeLabel, String model, String lookupTableFile) {
        lxPwrLUT = new LookupTable(nodeLabel, model, lookupTableFile);
        numCells = 1;
    }

    /**
     * Set number of the cell
     * 
     * @param numCells
     */
    public void setNumCells(int numCells) {
        this.numCells = numCells;
    }

    /**
     * Return output power in micro watts
     */
    @Override
    public double getOutputPower(double lux) {
        return numCells * lxPwrLUT.getY(lux);
    }

    /**
     * Returns energy in micro joule
     */
    @Override
    public double getOutputEnergy(double lux, double timeInterval /* in second */) {
        return getOutputPower(lux) * timeInterval;
    }

    
    //--------------------------------------------------------------------------
    // Test function
    public static void main(String[] args) {
        PhotovoltaicCell am1816 = new PhotovoltaicCell(0, "Panasonic-AM1816",
                System.getProperty("user.dir") + "/../config/EnergySources/Panasonic-AM1816.lut");
        for (double lux = 0.0; lux <= 1500; lux = lux + 10.0) {  // >1000 meant to be in extrapolation area
            System.out.println(String.format("Input %.1f lux -> %.2f uW -> %.2f uJ", 
                    lux, am1816.getOutputPower(lux), am1816.getOutputEnergy(lux, 1)));
        }
    }

}
