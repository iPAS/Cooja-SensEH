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

    private static final Level LOG_LEVEL = Level.OFF;  // ALL > TRACE > DEBUG > INFO > WARN > ERROR > FATAL > OFF
    private static Logger logger = Logger.getLogger(LookupTable3D.class);

    private String name;    
    private String file;  // A TAB separated two column flat file (Format:x\ty)
    private String xCoordinate;
    private String yCoordinate;
    private String zCoordinate;
    private double[]   xValues;
    private double[]   yValues;
    private double[][] zValues;

    private ArrayList<Double> xValuesArray;
    private ArrayList<double[]> zValuesArray;

    
    /**
     * Calculating the bilinear interpolation as described in     
     * @see http://supercomputingblog.com/graphics/coding-bilinear-interpolation/
     * @param x
     * @param y
     * @return
     */
    public double getZ(double x, double y) {        
        int[] xi = getPieceWiseLinePoints(xValues, x);  // Array of two indices to the couple points which x is between.
        int[] yi = getPieceWiseLinePoints(yValues, y);  // Array of two indices to the couple points which y is between.        
        double x0 = xValues[xi[0]];  // Area bounded by x0,x1,y0,y1 axes contains the point (x,y) 
        double x1 = xValues[xi[1]];
        double y0 = yValues[yi[0]];
        double y1 = yValues[yi[1]];
        double z00 = zValues[xi[0]][yi[0]];  // z of all four points
        double z10 = zValues[xi[1]][yi[0]];
        double z01 = zValues[xi[0]][yi[1]];
        double z11 = zValues[xi[1]][yi[1]];        
        // The explanation is in http://supercomputingblog.com/graphics/coding-bilinear-interpolation/.
        double r0 = (x0 == 0)? z00 : z10;  // Weighted average x0 .. x .. x1, z00 .. r0 .. z10 
        double r1 = (x0 == 0)? z01 : z11;  // Weighted average x0 .. x .. x1, z01 .. r1 .. z11
        if (x0 != x1) {
            r0 = ((x1 - x) * z00 + (x - x0) * z10) / (x1 - x0);
            r1 = ((x1 - x) * z01 + (x - x0) * z11) / (x1 - x0);
        }
        double z = (y0 == 0)? r0 : r1;  // Weighted average y0 .. y .. y1, r0 .. z .. r1
        if (y0 != y1) {
            z = ((y1 - y) * r0 + (y - y0) * r1) / (y1 - y0);
        }
        return z;
    }
    
    /**    
     * Find the two consecutive indices to an input array.
     * @param array
     * @param value
     * @return
     */
    private int[] getPieceWiseLinePoints(double[] array, double value) {
        int[] arrayIndices = new int[2];        
        // Does the 'value' fall within 'array' ?
        int i = 0;
        for (; i < array.length; i++) {
            if (value <= array[i])
                break;
        }        
        arrayIndices[0] = i - 1;
        arrayIndices[1] = i;
        if (i == 0) {  // In case, 'value' is less than all others in the 'array'.
            arrayIndices[0] = i;       
            logger.warn("value out of range!");            
        } else
        if (i == array.length) {  // In case, 'value' is greater than the others.
            arrayIndices[1] = i - 1;
            logger.warn("value out of range!");
        }
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
        
        /**
         * Open the LuT file
         */
        BufferedReader bufReader = null;
        try {
            bufReader = new BufferedReader(new FileReader(file));        
        } catch (FileNotFoundException e) {
            logger.fatal(name + ": Lookup Table File 3D " + file + " not found! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        /**
         * Read the header line:
         * > SolarCellOutput_mW  NiMHVoltage_V  HarvesterEfficiency
         */
        String header = null;
        try {
            header = bufReader.readLine();            
        } catch (IOException e) {
            logger.fatal(name + ": Could not read Lookup Table File 3D" + file + "! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
        if (header == null) {  // If it is an empty lookup table file
            logger.fatal(name + ": Lookup Table File 3D " + file + " is empty! exiting...");
            System.exit(-1);
        }
        parseXYZCoordinates(header);  // xCoordinate = "SolarCellOutput_mW"
                                      // yCoordinate = "NiMHVoltage_V"    
                                      // zCoordinate = "HarvesterEfficiency"
        /** 
         * Read the y axis on next line:
         * > X  2.00  2.1  2.2  2.3  2.4  2.5
         */
        String yValuesLine = null;
        try {
            yValuesLine = bufReader.readLine();
        } catch (IOException e) {
            logger.fatal(name + ": Could not read Lookup Table File 3D" + file + "! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }        
        parseYValues(yValuesLine);  // yValues[] = { 2.00, 2.1, 2.2, 2.3, 2.4, 2.5 }
        
        /**
         * Read the x axis on remain lines, and make the z axis:
         * > 5   0.775   0.775   0.775   0.776   0.778   0.779 
         * > 50  0.775   0.775   0.776   0.775   0.778   0.78 
         * > 100 0.777   0.777   0.778   0.782   0.784   0.785
         * > ...
         */
        String dataValues = null;
        xValuesArray = new ArrayList<Double>();
        zValuesArray = new ArrayList<double[]>();
        try {
            while ((dataValues = bufReader.readLine()) != null)  // Continuously read line-by-line
                parseXValue(dataValues);    // 5   0.775   0.775   0.775   0.776   0.778   0.779
                                            // xValuesArray[]   = { 5, 50, 100, ... }
                                            // zValuesArray[][] = 0.775, ... 
        } catch (IOException e) {
            logger.fatal("Check the LUT format! exiting ...");
            e.printStackTrace();
            System.exit(-1);
        } 

        /**
         * Now yValues[], xValuesArray, zValuesArray are ready.
         * Next, create xValues[] and zValues[] from xValuesArray and zValuesArray respectively. 
         * XXX: Not the ideal way of doing things !!!! But for now :)
         */
        // xValuesArray --> xValues
        Object[] xValuesObj = xValuesArray.toArray();
        xValues = new double[xValuesObj.length];
        for (int i = 0; i < xValuesObj.length; i++) {
            xValues[i] = ((Double) (xValuesObj[i])).doubleValue();
        }
        // zValuesArray --> zValues
        Object[] zValuesObj = zValuesArray.toArray();
        zValues = new double[xValues.length][yValues.length];
        for (int x = 0; x < xValues.length; x++) {
            double[] dValues = (double[]) zValuesObj[x];
            for (int y = 0; y < yValues.length; y++) {
                zValues[x][y] = dValues[y];
            }
        }
        
        /*
         * Finally, xValues[], yValues[], and zValues[][] are ready.
         */
    }

    private void parseXYZCoordinates(String header) {
        // SolarCellOutput_mW  NiMHVoltage_V  HarvesterEfficiency
        String [] xyCoordinates = header.split("\t");
        xCoordinate = xyCoordinates[0];  // SolarCellOutput_mW
        yCoordinate = xyCoordinates[1];  // NiMHVoltage_V
        zCoordinate = xyCoordinates[2];  // HarvesterEfficiency
    }

    private void parseYValues(String yValueLine) {
        // X  2.00  2.1  2.2  2.3  2.4  2.5
        String [] yV = yValueLine.split("\t");
        yValues = new double[yV.length - 1];
        for (int i = 0; i < yV.length - 1; i++) {
            yValues[i] = Double.parseDouble( yV[i + 1] );  // yValues[] = { 2.00, 2.1, 2.2, 2.3, 2.4, 2.5 }
        }  
    }

    private void parseXValue(String dataValuesLine) {
        // 5   0.775   0.775   0.775   0.776   0.778   0.779
        // xValuesArray[]   = { 5, 50, 100, ... }
        // zValuesArray[][] = 0.775, ...
        String [] dV = dataValuesLine.split("\t");
        xValuesArray.add(new Double( Double.parseDouble( dV[0] ) ));  // Get x from the first column, e.g., 5        
        double [] rowDataValues = new double[yValues.length];    
        for (int y = 0; y < yValues.length; y++) {               // Remain data for z[][], e.g.,
            rowDataValues[y] = Double.parseDouble( dV[y + 1] );  //  0.775  0.775  0.775  ...
        }
        zValuesArray.add(rowDataValues);
    }


    // -------------------------------------------------------------------------------
    /**
     * @param args
     */
    public static void main(String[] args) {
        LookupTable3D harvesterLUT = new LookupTable3D(0, "Multiharvester", 
                System.getProperty("user.dir") + "/../config/EnergyHarvesters/Multiharvester.lut");

        for (double voltage = 1.80; voltage <= 2.8; voltage += 0.05)
            for (double inputPower = 0.0; inputPower <= 350.0; inputPower += 25.0) 
                System.out.println(String.format("Efficiency(%.2f mW, %.2f V)\t%.4f", 
                        inputPower, voltage, harvesterLUT.getZ(inputPower, voltage)));
            
//        System.out.println(harvesterLUT.getZ(75.00, 2.25));
//        System.out.println(harvesterLUT.getZ(50.00, 2.2));
    }

}
