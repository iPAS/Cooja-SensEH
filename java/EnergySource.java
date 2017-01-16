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
public abstract class EnergySource {

  // examples of envValue :- wind speed, light intensity
  public abstract double getOutputPower(double envValue);

  public abstract double getOutputEnergy(double envValue, double timeInterval);

}
