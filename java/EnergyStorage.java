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
public abstract class EnergyStorage {
  
  abstract double getVoltage();

  abstract void charge(double energy_mj);

  abstract void discharge(double energy_mj);

  abstract double getEnergy();

  abstract int getNumStorages();

}
