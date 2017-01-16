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
public class LightDataProvider extends EnvironmentalDataProvider {

    final static double CALIBRATION_CONST= 0.596;

    /**
     * @param traceFile
     * @param delimiter
     * @param tokenNo
     */
    public LightDataProvider(String traceFile, String delimiter, int tokenNo) {
        super(traceFile, delimiter, tokenNo);
    }

    @Override
    public double getNext(){
        double lightValue_counts = super.getNext();
        // convert from raw counts to Lux
        //System.out.print ("Light: " + lightValue_counts);
        return CALIBRATION_CONST * lightValue_counts;
    }

}
