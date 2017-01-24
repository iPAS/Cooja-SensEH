import java.awt.List;
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

public class LookupTable {
    
    private static final Level LOG_LEVEL = Level.DEBUG;
    private static Logger logger = Logger.getLogger(LookupTable.class);

    private String name;    
    private String file;  // A TAB separated two column flat file (Format:x\ty)
    private String xCoordinate;
    private String yCoordinate;
    private Point [] points;

    
    /**
     * Public functions
     */
    public String getName() {
        return name;
    }

    public String getFile() {
        return file;
    }

    public String getXCoordinate() {
        return xCoordinate;
    }

    public String getYCoordinate() {
        return yCoordinate;
    }
    
    /** 
     * Returns the value of y based on Piece-wise linear approximation between closed known points
     * @param x
     * @return
     */
    public double getY(double x) {
        double y;
        Point [] piecePoints = getPieceWiseLinePoints(x);        
        double slope = (piecePoints[1].getY() - piecePoints[0].getY()) / (piecePoints[1].getX() - piecePoints[0].getX());
        y = slope * (x - piecePoints[0].getX()) + piecePoints[0].getY();  // Calculating the linear interpolation
        return y;
    }
    
    /**
     * Returns the points for piece wise linear segment.
     * It supports extrapolation along with interpolation.
     * @param x
     * @return
     */
    private Point[] getPieceWiseLinePoints(double x) {
        // TODO: check than the value of x fall within the range of x values of points
        Point [] piecePoints = new Point[2];
        boolean isInRange = false;
        int i = 0;
        for (; i < points.length; i++) {
            if (x <= points[i].getX()) {
                isInRange = true;
                break;
            }
        }
        
        if (isInRange) {
            int prev = i - 1;
            int next = i;
            if (i == 0) {
                prev = 0;
                next = 1;
            }
            piecePoints[0] = points[prev];
            piecePoints[1] = points[next];
            return piecePoints;
        } else {
            return null;
        }
    }
    
    /**
     * Constructor
     * 
     * @param name
     * @param file
     */
    public LookupTable(String name, String file) {
        
        if (!logger.isEnabledFor(LOG_LEVEL)) 
            logger.setLevel(LOG_LEVEL);
        
        this.name = name;
        this.file = file;
        
        BufferedReader bufReader = null;
        try {
            bufReader = new BufferedReader(new FileReader(file));
            
        } catch (FileNotFoundException e) {
            logger.fatal(name + ": Lookup Table File " + file + " not found! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        // Read the header line
        String header = null;
        try {
            header = bufReader.readLine();
            
        } catch (IOException e) {
            logger.fatal(name + ": Could not read Lookup Table File " + file + "! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        if (header == null) {  // Empty lookup table file
            logger.fatal(name + ": Lookup Table File " + file + " is empty! exiting...");
            System.exit(-1);
        }
        parseXYCoordinates(header);

        // Add the points
        String line;
        ArrayList<Point> pointsList = new ArrayList<Point>();
        try {
            while ((line = bufReader.readLine()) != null)
                pointsList.add(parsePoint(line));
            
        } catch (IOException e) {
            logger.fatal(name + ": Could not read Lookup Table File " + file + "! exiting...");
            e.printStackTrace();
            System.exit(-1);
        }
        points = pointsList.toArray(new Point[0]);
    }

    private void parseXYCoordinates(String header) {
        String[] xyCoordinates = header.split("\t");
        xCoordinate = xyCoordinates[0];
        yCoordinate = xyCoordinates[1];
    }

    private Point parsePoint(String line) {
        String[] coordinates = line.split("\t");
        return new Point(Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]));
    }
        

    // ------------------------------------------------------------------------
    /**
     * @param args
     */
    public static void main(String[] args) {
        // Testing the stand alone program
        // LookupTable Harvester new LookupTable(String name, String file);
    }
}
