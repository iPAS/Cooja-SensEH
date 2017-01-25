import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

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
public class EnvironmentalDataProvider {

    private static final Level LOG_LEVEL = Level.DEBUG;
    private static Logger logger = Logger.getLogger(EnvironmentalDataProvider.class);

    private String traceFile;
    private BufferedReader traceReader;
    private long totalRead;
    private String delimiter;
    private int tokenNo;


    /**
     * Constructor
     * 
     * @param traceFile
     * @param delimiter
     * @param tokenNo
     */
    public EnvironmentalDataProvider(String traceFile, String delimiter, int tokenNo) {
        
        if (!logger.isEnabledFor(LOG_LEVEL)) 
            logger.setLevel(LOG_LEVEL);
        
        this.traceFile = traceFile;
        this.delimiter = delimiter;
        this.tokenNo = tokenNo;
        totalRead = 0;
        this.startReading();
    }

    private void startReading() {
        try {
            traceReader = new BufferedReader(new FileReader(traceFile));
            
        } catch (FileNotFoundException e) {
            logger.fatal("Enviornmental data trace file " + traceFile +  " not found! Exiting.... ");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public double getNext() {
        // Do you really want to read the file sequentially?
        // Make sure that environmental data is fully clean: 1 sample every chargeInterval     
        String line = null;
        double value = 0;

        try {
            if ((line = traceReader.readLine()) != null) {
                String [] tokens = line.split(delimiter);
                value = Double.parseDouble(tokens[tokenNo-1]);
            } else {
                // start reading again from the 1st line
                this.stopReading();
                this.startReading();
                return getNext();
            }
            
        } catch (NumberFormatException e) {
            logger.fatal("Could not parse environmental value! Exiting...");
            e.printStackTrace();
            System.exit(-1);
            
        } catch (IOException e) {
            logger.fatal("Could not read from trace file " +  traceFile + "! Exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
        
        totalRead++;
        return value;
    }

    public void stopReading(){
        try {
            traceReader.close();
            
        } catch (IOException e) {
            logger.fatal("Could not close environmental trace file! Exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
    }

}
