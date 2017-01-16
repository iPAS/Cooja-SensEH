import java.awt.List;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

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
    /**
     *
     */
    private String name;
    // A tab separated two column flat file (Format:x\ty)
    private String file;
    private String xCoordinate;
    private String yCoordinate;
    private Point[] points;

    public LookupTable(String name, String file) {
        this.name = name;
        this.file = file;
        BufferedReader bufRdr = null;
        try {
            bufRdr = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            System.err.println(name + ": Lookup Table File " + file + " not found\nexiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        // Read the header line
        String header = null;
        try {
            header = bufRdr.readLine();
        } catch (IOException e) {
            System.err.println(name + ": Could not read Lookup Table File " + file + "\nexiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        if (header == null) {// empty lookup table file
            System.err.println(name + ": Lookup Table File " + file + " is empty\nexiting...");
            System.exit(-1);
        }
        parseXYCoordinates(header);

        String line;
        ArrayList<Point> pointsList = new ArrayList<Point>();

        try {
            while ((line = bufRdr.readLine()) != null) {
                pointsList.add(parsePoint(line));
            }
        } catch (IOException e) {
            System.err.println(name + ": Could not read Lookup Table File " + file + "\nexiting...");
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

    // returns the value of y based on Piece-wise linear approximation between
    // closed known points
    public double getY(double x) {
        double y;
        Point[] piecePoints = getPieceWiseLinePoints(x);
        //System.out.println( 
        //        "("     + piecePoints[0].getX() + "," + piecePoints[0].getY() + 
        //        ")---(" + piecePoints[1].getX() + "," + piecePoints[1].getY() + ")");
        
        double slope = (piecePoints[1].getY() - piecePoints[0].getY())
                / (piecePoints[1].getX() - piecePoints[0].getX());
        // System.out.println("Slope="+slope);
        // Calculating the linear interpolation
        y = slope * (x - piecePoints[0].getX()) + piecePoints[0].getY();
        return y;
    }

    // returns the points for piece wise linear segment
    private Point[] getPieceWiseLinePoints(double x) {
        // TODO: check than the value of x fall within the range of x values of
        // points
        // TODO: Can we support extrapolation along with intrapolation
        Point[] piecePoints = new Point[2];
        boolean xInRange = false;
        int i = 0;
        while (i < points.length) {
            if (x <= points[i].getX()) {
                xInRange = true;
                break;
            }
            i++;
        }
        if (xInRange) {
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

    // ------------------------------------------------------------------------
    /**
     * @param args
     */
    public static void main(String[] args) {
        // Testing the stand alone program
        // LookupTable Harvester new LookupTable(String name, String file);
    }
}
