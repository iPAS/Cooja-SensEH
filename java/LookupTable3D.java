import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


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
public class LookupTable3D {

    private String name ;
    // A tab separated two column flat file (Format:x\ty)
    private String file;
    private String xCoordinate;
    private String yCoordinate;
    private String zCoordinate;
    private double[] xValues;
    private double[] yValues;
    private double [][] zValues;

    private ArrayList<Double> xValuesArray;
    private ArrayList<double []> zValuesArray;


    /**
     *
     */
    public LookupTable3D(String name, String file) {
        this.name= name;
        this.file=file;
        BufferedReader bufRdr = null;
        try {
            bufRdr = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            System.err.println(name+": Lookup Table File 3D " + file + " not found\nexiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        // Read the header line
        String header=null;
        try {
            header = bufRdr.readLine();
        } catch (IOException e) {
            System.err.println(name+": Could not read Lookup Table File 3D" + file + "\nexiting...");
            e.printStackTrace();
            System.exit(-1);
        }

        if (header==null){//empty lookup table file
            System.err.println(name+": Lookup Table File 3D " + file + " is empty\nexiting...");
            System.exit(-1);
        }
        parseXYZCoordinates(header);

        String yValuesLine = null;

        try {
            yValuesLine = bufRdr.readLine();
        } catch (IOException e) {
            System.err.println(name+": Could not read Lookup Table File 3D" + file + "\nexiting...");
            e.printStackTrace();
            System.exit(-1);
        }
        parseYValues(yValuesLine);
        String dataValues = null;
        xValuesArray= new ArrayList<Double>();
        zValuesArray= new ArrayList<double[]>();

        try {
            while ((dataValues = bufRdr.readLine())!=null)
                parseXValue(dataValues);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.err.println ("Check the LUT format.. exiting ...");
        }

        // Not the ideal way of doing things !!!! But for now :)
        Object[] xValuesDouble = xValuesArray.toArray();
        xValues = new double [xValuesDouble.length];
        for (int i = 0; i < xValuesDouble.length; i++){
            xValues[i]= ((Double)(xValuesDouble[i])).doubleValue();
        }

        Object[] zValuesObjArray = zValuesArray.toArray();

        zValues = new double [xValues.length][yValues.length];
        for(int x =0; x < xValues.length ; x++){
            double[] dValues=(double[])zValuesObjArray[x];
            for(int y =0; y < yValues.length ; y++){
                zValues[x][y]= dValues[y];
                //System.out.print(zValues[x][y]+ "\t");
            }
            //System.out.println();
        }

    }

    private void parseXYZCoordinates(String header){
        String[] xyCoordinates = header.split("\t");
        xCoordinate = xyCoordinates[0];
        yCoordinate = xyCoordinates[1];
        zCoordinate = xyCoordinates[2];
    }

    private void parseYValues(String yValueLine){
        String[] yV = yValueLine.split("\t");
        yValues = new double [yV.length-1];
        for (int i = 0; i<yV.length-1; i++){
            yValues[i]=Double.parseDouble(yV[i+1]);
        }
    }

    private void parseXValue(String dataValues){
        // pick up the value of y from the first column
        //System.out.println (dataValues);
        String[] dV = dataValues.split("\t");
        xValuesArray.add(new Double (Double.parseDouble(dV[0])));
        double[] rowDataValues = new double [yValues.length];
        for (int y=0; y< yValues.length; y++){
            rowDataValues[y]= Double.parseDouble(dV[y+1]);
        }
        zValuesArray.add (rowDataValues);
    }

    // read a known value of z for given x and y indeces
    /*private readZ(int xi, int yi){

		return

	}*/

    // Find the two consecutive indeces
    private int[] getPieceWiseLinePoints(double[] array , double value){
        int[] arrayIndices = new int [2];
        int i=0;
        boolean valueInRange = false;
        while(i<array.length){
            if (value<=array[i]){
                valueInRange=true;
                break;
            }
            i++;
        }
        if (valueInRange){
            if (i==0){
                arrayIndices[0]=arrayIndices[1]=0;
            }
            else{
                arrayIndices[0]=i-1;
                arrayIndices[1]=i;
            }
            return arrayIndices;
        }
        // value is not in range and i == array.length
        arrayIndices[0] = arrayIndices[1] = array.length-1;
        //System.err.println ("LookupTable3D: Value Out of Range");
        //System.exit(-1);

        return arrayIndices;
    }

    // Calculating the bilinear interpolation As described in http://en.wikipedia.org/wiki/Bilinear_interpolation
    public double getZ(double x, double y){
        //System.out.println (xCoordinate);
        int [] xi = getPieceWiseLinePoints(xValues, x);
        //System.out.println (yCoordinate +"\t"+ y);
        int [] yi = getPieceWiseLinePoints(yValues, y);
        //System.out.println (yi[0]+ "\t"+yi[1]);

        double x0 = xValues[xi[0]];
        double y0 = yValues[yi[0]];
        double x1 = xValues[xi[1]];
        double y1 = yValues[yi[1]];


        //System.out.println ("Xs:"+x0+"\t"+x1);
        //System.out.println ("Ys:"+y0+"\t"+y1);

        double z00= zValues[xi[0]][yi[0]];
        double z10= zValues[xi[1]][yi[0]];
        double z01= zValues[xi[0]][yi[1]];
        double z11= zValues[xi[1]][yi[1]];

        //System.out.println ("Z00:"+z00);
        //System.out.println ("Z10:"+z10);
        //System.out.println ("Z01:"+z01);
        //System.out.println ("Z11:"+z11);


        /*
         * First try
		  double z =
					(
						z00 * (x1-x) * (y1-y) +
						z10 * (x-x0) * (y1-y) +
						z01 * (x1-x) * (y-y0) +
						z11 * (x-x0) * (y-y0)
					)
					/
					(
						(x1-x0)*(y1-y0)
					);
			// Problem zero in denominator --> NaN
         */


        double r1 = z00;
        double r2= z01;
        // Calculating value at x
        if (x0 != x1){
            // Calculated r1 and r2
            r1 = ( (x1-x)*z00 + (x-x0)*z10 ) / (x1-x0);
            r2 = ( (x1-x)*z01 + (x-x0)*z11 ) / (x1-x0);
        }

        double z = r1;

        if (y0 != y1){
            z = ( (y1-y)*r1 + (y-y0)*r2 ) / (y1-y0);
        }

        return z;



    }

    void printY (){
        for (int x= 0; x < yValues.length; x++){
            System.out.println (yValues[x]);
        }
    }


    // -------------------------------------------------------------------------------
    /**
     * @param args
     */
    public static void main(String[] args) {
        LookupTable3D harvesterLUT = new LookupTable3D("Multiharvester", "/home/raza/raza@murphysvn/code/java/eclipseIndigo/Senseh/EnergyHarvesters/Multiharvester.lut" );
        //harvesterLUT.printY();
        System.out.println (harvesterLUT.getZ(75.00, 2.25));
        System.out.println (harvesterLUT.getZ(50.00, 2.2 ));
    }

}
