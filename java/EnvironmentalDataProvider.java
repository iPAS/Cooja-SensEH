import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


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
public class EnvironmentalDataProvider {

    private String traceFile;
    private BufferedReader traceRdr;
    private long totalRead;
    private String delimiter;
    private int tokenNo;


    public EnvironmentalDataProvider(String traceFile, String delimiter, int tokenNo){
        this.traceFile = traceFile;
        this.delimiter = delimiter;
        this.tokenNo= tokenNo;
        totalRead=0;
        this.startReading();
    }

    private void startReading(){
        try {
            traceRdr = new BufferedReader(new FileReader(traceFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("Enviornmental data trace file " + traceFile +  " not found ... Exiting.... ");
            System.exit(-1);
        }
    }

    // Do you really want to read the file sequentially? 
    // Make sure that environmental data is fully clean: 1 sample every chargeInterval
    public double getNext(){
        String line = null;
        double value = 0;

        try {
            if ((line = traceRdr.readLine()) != null){
                String[] tokens =line.split(delimiter);
                value = Double.parseDouble(tokens[tokenNo-1]);
            }else{
                // start reading again from the 1st line
                this.stopReading();
                this.startReading();
                return getNext();

            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.err.println ("Could not parse environmental value... Exiting...");
            System.exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println ("Could not read from trace file "+  traceFile +" ... Exiting...");
            System.exit(-1);
        }
        totalRead++;
        return value;
    }

    public void stopReading(){
        try {
            traceRdr.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println ("Could not close environmental trace file .. Exiting...");
            System.exit(-1);
        }
    }

}
