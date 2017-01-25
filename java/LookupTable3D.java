import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

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
public class LookupTable3D {

    private static final Level LOG_LEVEL = Level.OFF;
    private static Logger logger = Logger.getLogger(LookupTable3D.class);

    private String name;    
    private String file;  // A TAB separated two column flat file (Format:x\ty)
    private String xCoordinate;
    private String yCoordinate;
    private String zCoordinate;
    private double []   xValues;
    private double []   yValues;
    private double [][] zValues;

    private ArrayList<Double> xValuesArray;
    private ArrayList<double[]> zValuesArray;

    
    /**
     * Calculating the bilinear interpolation As described in
     * @see http://en.wikipedia.org/wiki/Bilinear_interpolation
     * @param x
     * @param y
     * @return
     */
    public double getZ(double x, double y) {        
        int [] xi = getPieceWiseLinePoints(xValues, x);        
        int [] yi = getPieceWiseLinePoints(yValues, y);
        
        double x0 = xValues[xi[0]];
        double y0 = yValues[yi[0]];
        double x1 = xValues[xi[1]];
        double y1 = yValues[yi[1]];

        double z00 = zValues[xi[0]][yi[0]];
        double z10 = zValues[xi[1]][yi[0]];
        double z01 = zValues[xi[0]][yi[1]];
        double z11 = zValues[xi[1]][yi[1]];

        // First try double 
        // z = ( z00 * (x1-x) * (y1-y) + z10 * (x-x0) * (y1-y) * + z01 * (x1-x) * (y-y0) + z11 * (x-x0) * (y-y0) ) 
        //         / ( (x1-x0) * (y1-y0) ); 
        // But, the problem, zero in denominator --> NaN
         
        double r1 = z00;
        double r2 = z01;
        // Calculating value at x
        if (x0 != x1) {
            // Calculated r1 and r2
            r1 = ((x1 - x) * z00 + (x - x0) * z10) / (x1 - x0);
            r2 = ((x1 - x) * z01 + (x - x0) * z11) / (x1 - x0);
        }

        double z = r1;
        if (y0 != y1) {
            z = ((y1 - y) * r1 + (y - y0) * r2) / (y1 - y0);
        }

        return z;
    }
    
    /**    
     * Find the two consecutive indeces
     * @param array
     * @param value
     * @return
     */
    private int[] getPieceWiseLinePoints(double[] array, double value) {
        int [] arrayIndices = new int[2];
        
        boolean isInRange = false;
        int i = 0;
        for (; i < array.length; i++) {
            if (value <= array[i]) {
                isInRange = true;
                break;
            }
        }
        
        if (isInRange) {
            if (i == 0) {  // 'value' is less than all others. 
                arrayIndices[0] = arrayIndices[1] = 0;
                
            } else {  // In range.
                arrayIndices[0] = i - 1;
                arrayIndices[1] = i;
            }
            
            return arrayIndices;
        }
        
        // FIXME: value is NOT in range and i == array.length
        arrayIndices[0] = arrayIndices[1] = array.length - 1;
        logger.warn("LookupTable3D: Value Out of Range!");
//        System.exit(-1);

        return arrayIndices;
    }
    
    /**
     * Constructor
     */
    public LookupTable3D(int nodeLabel, String name, String file) {

        //if (!logger.isEnabledFor(LOG_LEVEL))  // Log4J configuration file is in cooja/config/log4j_config.xml 
            logger.setLevel(LOG_LEVEL);
        
        this.name = name;
        this.file = file;
        
        BufferedReader bufReader = null;
        try {
            bufReader = new BufferedReader(new FileReader(file));
        
        } catch (FileNotFoundException e) {
            logger.fatal(name + ": Lookup Table File 3D " + file + " not found! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        // Read the header line
        String header = null;
        try {
            header = bufReader.readLine();
            
        } catch (IOException e) {
            logger.fatal(name + ": Could not read Lookup Table File 3D" + file + "! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        if (header == null) {  // Empty lookup table file
            logger.fatal(name + ": Lookup Table File 3D " + file + " is empty! exiting...");
            System.exit(-1);
        }
        parseXYZCoordinates(header);

        // Add the points
        String yValuesLine = null;
        try {
            yValuesLine = bufReader.readLine();
            
        } catch (IOException e) {
            logger.fatal(name + ": Could not read Lookup Table File 3D" + file + "! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
        
        parseYValues(yValuesLine);
        String dataValues = null;
        xValuesArray = new ArrayList<Double>();
        zValuesArray = new ArrayList<double[]>();
        try {
            while ((dataValues = bufReader.readLine()) != null)
                parseXValue(dataValues);
            
        } catch (IOException e) {
            logger.fatal("Check the LUT format! exiting ...");
            e.printStackTrace();
            System.exit(-1);
        }

        // FIXME: Not the ideal way of doing things !!!! But for now :)
        Object [] xValuesDouble = xValuesArray.toArray();
        xValues = new double[xValuesDouble.length];
        for (int i = 0; i < xValuesDouble.length; i++) {
            xValues[i] = ((Double) (xValuesDouble[i])).doubleValue();
        }

        Object [] zValuesObjArray = zValuesArray.toArray();
        zValues = new double[xValues.length][yValues.length];
        for (int x = 0; x < xValues.length; x++) {
            double [] dValues = (double[]) zValuesObjArray[x];
            for (int y = 0; y < yValues.length; y++) {
                zValues[x][y] = dValues[y];
            }
        }
    }

    private void parseXYZCoordinates(String header) {
        String [] xyCoordinates = header.split("\t");
        xCoordinate = xyCoordinates[0];
        yCoordinate = xyCoordinates[1];
        zCoordinate = xyCoordinates[2];
    }

    private void parseYValues(String yValueLine) {
        String [] yV = yValueLine.split("\t");
        yValues = new double[yV.length - 1];
        for (int i = 0; i < yV.length - 1; i++) {
            yValues[i] = Double.parseDouble( yV[i + 1] );
        }
    }

    private void parseXValue(String dataValues) {
        // Pick up the value of y from the first column
        String [] dV = dataValues.split("\t");
        xValuesArray.add(new Double( Double.parseDouble(dV[0]) ));
        double [] rowDataValues = new double[yValues.length];
        for (int y = 0; y < yValues.length; y++) {
            rowDataValues[y] = Double.parseDouble( dV[y + 1] );
        }
        zValuesArray.add(rowDataValues);
    }


    // -------------------------------------------------------------------------------
    /**
     * @param args
     */
    public static void main(String[] args) {
        LookupTable3D harvesterLUT = new LookupTable3D(0, "Multiharvester", "/home/raza/Senseh/EnergyHarvesters/Multiharvester.lut");
        System.out.println(harvesterLUT.getZ(75.00, 2.25));
        System.out.println(harvesterLUT.getZ(50.00, 2.2));
    }

}
