import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import org.apache.log4j.Logger;
//import org.jdom.Element;
import se.sics.cooja.ClassDescription;
import se.sics.cooja.Mote;
//import se.sics.cooja.SimEventCentral.MoteCountListener;
import se.sics.cooja.Simulation;
import se.sics.cooja.interfaces.Radio;
import se.sics.mspsim.core.OperatingModeListener;

// TODO: Comment out the next two imports -- Only for debugging purposes
import se.sics.cooja.emulatedmote.Radio802154;
import se.sics.cooja.mspmote.interfaces.Msp802154Radio;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.Chip;
import se.sics.cooja.mspmote.SkyMote;
import se.sics.mspsim.core.MSP430Constants;

import se.sics.cooja.interfaces.Radio.RadioEvent;


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
@ClassDescription("Power Consumption Model for SensEH")
public class PowerConsumption implements OperatingModeListener, Observer, MSP430Constants{

  private static Logger logger = Logger.getLogger(PowerConsumption.class);

  private Simulation simulation;
  private Mote mote;
  private Chip cpu;
  private Radio radio;

  // cpu modes
  private int lastCPUMode;

  // cpu statistics
  long [] cpuModeTimes;
  long [] cpuModeTimesTotal;
  long [] cpuModeTimesTotalSnapshot;
  private long lastCPUUpdateTime;

  protected void accumulateCPUModeTime(int mode, long t) {
    cpuModeTimes[mode] += t;
    cpuModeTimesTotal[mode] += t;
  }

  //--------------------------------------------------------------------------
  // radio states
  private boolean radioWasOn;
  private RadioState lastRadioState;
  private long lastRadioUpdateTime;

  // radio statistics, timers are recorded in micro-second
  public static class RadioTimes {
    public long duration;
    public long on;
    public long tx;
    public long rx;
    public long interfered;
    public long idle;
    public long off;
    public long [] multiTx;

    public RadioTimes(int multi) {
      multiTx = new long[multi];
      reset();
    }

    public void reset() {
      duration = on = tx = rx = interfered = idle = off = 0;
      for (int i = 0; i < multiTx.length; i++)
        multiTx[i] = 0;
    }

    public void setMembersAs(final RadioTimes rt) {
      duration   = rt.duration;
      on         = rt.on;
      tx         = rt.tx;
      rx         = rt.rx;
      interfered = rt.interfered;
      idle       = rt.idle;
      off        = rt.off;
      for (int i = 0; i < multiTx.length; i++)
        multiTx[i] = rt.multiTx[i];
    }

    public void addMembersWith(final RadioTimes rt) {
      duration   += rt.duration;
      on         += rt.on;
      tx         += rt.tx;
      rx         += rt.rx;
      interfered += rt.interfered;
      idle       += rt.idle;
      off        += rt.off;
      for (int i = 0; i < multiTx.length; i++)
        multiTx[i] += rt.multiTx[i];
    }

    public void divMembersWith(int divisor) {
      duration   /= divisor;
      on         /= divisor;
      tx         /= divisor;
      rx         /= divisor;
      interfered /= divisor;
      idle       /= divisor;
      off        /= divisor;
      for (int i = 0; i < multiTx.length; i++)
        multiTx[i] /= divisor;
    }

    public void accumulateDuration(long t) {
      duration += t;
    }

    public void accumulateRadioOn(long t) {
      on += t;
    }

    public void accumulateRadioTx(long t, int i) {
      tx += t;
      multiTx[i] += t;
    }

    public void accumulateRadioRx(long t) {
      rx += t;
    }

    public void accumulateRadioInterfered(long t) {
      interfered += t;
    }

    public void accumulateRadioIdle(long t) {
      idle += t;
    }

    public void accumulateRadioOff(long t) {
      off += t;
    }

  }

  private RadioTimes radioTimes;
  public RadioTimes radioTimesTotal;
  public RadioTimes radioTimesTotalSnapshot;
  private int lastRadioTxIndicator; // iPAS: states in multiple transmission powers

  //--------------------------------------------------------------------------
  // All modes of CPU, even Radio, are reported in mA, then powers are all in mW

  // Taken from SensorInfo.java, some taken from cooja/apps/powertracker/java/PowerTracker.java
  public static final long TICKS_PER_SECOND = 4096L;
  public double VOLTAGE; // Riccardo =2.4 | we can use our computed current battery voltage!!!!
  public static final double [] CURRENT_CPU = {1.800, 0.0545, /*WRONG! TODO FIXME*/ 0.0500 , 0.0110, 0.0011, 0.0002};

  double getPowerCPU(int mode) {
    return CURRENT_CPU[mode] * VOLTAGE;
  }

  void setVoltage(double v) {
    VOLTAGE = v;
  }

  public enum RadioState {
    IDLE, RECEIVING, TRANSMITTING, INTERFERED
  }

  public static class RadioCurrent { // mA
    public static final double TX = 17.7;
    public static final double RX = 20.0;
    public static final double INTERFERED = 20.0;
    public static final double IDLE = 20.0;
    public static final double OFF = 0.0;
  }

  public static final double[] CURRENT_RADIO_TX = {
      /**
       * The data is referred from
       * Dargie, Waltenegus, and Christian Poellabauer.
       * "Fundamentals of wireless sensor networks: theory and practice."
       * John Wiley & Sons, 2010.
       *
       * Lev. 3, −25 dBm, 8.5 mA
       * Lev. 7, −15 dBm, 9.9 mA
       * Lev. 11, −10 dBm, 11.2 mA
       * Lev. 15, −7 dBm, 12.5 mA
       * Lev. 19, −5 dBm, 13.9 mA
       * Lev. 23, −3 dBm, 15.2 mA
       * Lev. 27, −1 dBm, 16.5 mA
       * Lev. 31, 0 dBm, 17.4 mA
       *
       * Then, be done a curve-fitting on http://mycurvefit.com/
       */
      7.645148, 7.935078, 8.233389, 8.539394, 8.852406,  9.17174, 9.496708, 9.826623, 10.1608, 10.49855,
      10.83919, 11.18203, 11.52639, 11.87158, 12.21691, 12.56169, 12.90524, 13.24688, 13.58591, 13.92165,
      14.25341, 14.58051, 14.90225, 15.21797, 15.52695, 15.82853, 16.12201, 16.40671, 16.68194, 16.94701,
      17.20124, 17.44394,};

  //--------------------------------------------------------------------------
  public PowerConsumption(final Simulation simulation, final Mote mote, double supplyVoltage) {
    this.simulation = simulation;
    this.mote = mote;
    this.VOLTAGE = supplyVoltage;

    cpu = ((SkyMote) mote).getCPU();
    radio = mote.getInterfaces().getRadio();

    // to let cpu and radio inform their state changes to power consumption model
    cpu.addOperatingModeListener(this);
    radio.addObserver(this);
    cpuModeTimes = new long[MSP430Constants.MODE_NAMES.length];
    cpuModeTimesTotal = new long[MSP430Constants.MODE_NAMES.length];
    cpuModeTimesTotalSnapshot = new long[MSP430Constants.MODE_NAMES.length];

    radioTimes = new RadioTimes(radio.getOutputPowerIndicatorMax()+1);
    radioTimesTotal = new RadioTimes(radio.getOutputPowerIndicatorMax()+1);
    radioTimesTotalSnapshot = new RadioTimes(radio.getOutputPowerIndicatorMax()+1);

    initStats();
  }

  private void initStats() {
    lastCPUUpdateTime = lastRadioUpdateTime = simulation.getSimulationTime();

    lastCPUMode = cpu.getMode();
    cpuModeTimes = new long[MSP430Constants.MODE_NAMES.length]; // Reset

    radioWasOn = radio.isRadioOn();
    if (radio.isTransmitting()) {
      lastRadioState = RadioState.TRANSMITTING;
    } else if (radio.isReceiving()) {
      lastRadioState = RadioState.RECEIVING;
    } else if (radio.isInterfered()) {
      lastRadioState = RadioState.INTERFERED;
    } else {
      lastRadioState = RadioState.IDLE;
    }

    lastRadioTxIndicator = radio.getCurrentOutputPowerIndicator();
    radioTimes.reset();
  }

  public void reset(){
    initStats();
  }

  public void restart() {
    cpuModeTimesTotal = new long[MSP430Constants.MODE_NAMES.length];
    cpuModeTimesTotalSnapshot = new long[MSP430Constants.MODE_NAMES.length];

    radioTimesTotal.reset();
    radioTimesTotalSnapshot.reset();
    reset();
  }

  public void dispose() {
    radio.deleteObserver(this);
    radio = null;
    mote = null;
  }

  // --------------------------------------------------------------------------
  @Override
  public void modeChanged(Chip source, int mode) { // Operating mode of the CPU has changed.
    // Some implement of the OperatingModeListener interface.
    if (source instanceof MSP430) {
      updateCPUStats();
    } else {
      System.err.println("PowerConsumption: Wrong CPU!!! Exiting....");
      System.exit(-1);
    }
  }

  public void updateCPUStats() {
    long now = simulation.getSimulationTime();
    accumulateCPUModeTime(lastCPUMode, now - lastCPUUpdateTime);
    /*
    System.out.println( "CPU State changed from " +
        MSP430Constants.MODE_NAMES[lastCPUMode] + " to " +
        MSP430Constants.MODE_NAMES[cpu.getMode()] );
    */
    lastCPUMode = cpu.getMode();
    lastCPUUpdateTime = now;
  }

  // --------------------------------------------------------------------------
  @Override
  public void update(Observable o, Object arg) { // some implement of the Observer interface
    updateRadioStats();
  }

  public void updateRadioStats() {
    RadioEvent radioEv = radio.getLastEvent();
    if (radioEv == RadioEvent.CUSTOM_DATA_TRANSMITTED ||
        radioEv == RadioEvent.PACKET_TRANSMITTED)
      return;

    long now = simulation.getSimulationTime();
    long duration = now - lastRadioUpdateTime;

    radioTimes.accumulateDuration(duration);
    radioTimesTotal.accumulateDuration(duration);

    // Radio On/Off
    if (radioWasOn) { // Since previous time
      radioTimes.accumulateRadioOn(duration);
      radioTimesTotal.accumulateRadioOn(duration);

      // Radio TX/RX
      if (lastRadioState == RadioState.TRANSMITTING) {
        radioTimes.accumulateRadioTx(duration, lastRadioTxIndicator);
        radioTimesTotal.accumulateRadioTx(duration, lastRadioTxIndicator);

      } else if (lastRadioState == RadioState.RECEIVING) {
        radioTimes.accumulateRadioRx(duration);
        radioTimesTotal.accumulateRadioRx(duration);

      } else if (lastRadioState == RadioState.INTERFERED) {
        radioTimes.accumulateRadioInterfered(duration);
        radioTimesTotal.accumulateRadioInterfered(duration);

      } else if (lastRadioState == RadioState.IDLE) {
        radioTimes.accumulateRadioIdle(duration);
        radioTimesTotal.accumulateRadioIdle(duration);

      } else {
        System.err.println("PowerConsumption: Wrong lastRadioState!!! Exiting....");
        System.exit(-1);
      }
    } else {
      radioTimes.accumulateRadioOff(duration);
      radioTimesTotal.accumulateRadioOff(duration);
    }

    if (radio.isRadioOn()){ // Currently
      // Await next radio event
      if (radio.isTransmitting()) {
        lastRadioState = RadioState.TRANSMITTING;

        /*
        System.out.format("TX power of Mote %d: %f %d\n", // iPAS
            mote.getID(),
            radio.getCurrentOutputPower(),
            radio.getCurrentOutputPowerIndicator()
            ); // It's a level of 0-31 where the max, '31', == 0 dBm
        */
        lastRadioTxIndicator = radio.getCurrentOutputPowerIndicator();

      } /*else if (!radio.isRadioOn()) { lastRadioState = RadioState.IDLE; }*/
        else if (radio.isReceiving()) {
        lastRadioState = RadioState.RECEIVING;
      } else if (radio.isInterfered()) {
        lastRadioState = RadioState.INTERFERED;
      } else {
        lastRadioState = RadioState.IDLE;
      }
    }

    radioWasOn = radio.isRadioOn();
    lastRadioUpdateTime = now;
  }

  // --------------------------------------------------------------------------
  private double calculateCPUEnergy(long [] times) { // usec x mW
    return times[MSP430Constants.MODE_ACTIVE] * getPowerCPU(MSP430Constants.MODE_ACTIVE)
         + times[MSP430Constants.MODE_LPM0]   * getPowerCPU(MSP430Constants.MODE_LPM0)
         + times[MSP430Constants.MODE_LPM1]   * getPowerCPU(MSP430Constants.MODE_LPM1)
         + times[MSP430Constants.MODE_LPM2]   * getPowerCPU(MSP430Constants.MODE_LPM2)
         + times[MSP430Constants.MODE_LPM3]   * getPowerCPU(MSP430Constants.MODE_LPM3)
         + times[MSP430Constants.MODE_LPM4]   * getPowerCPU(MSP430Constants.MODE_LPM4);
  }

  private double calculateRadioEnergyTx(RadioTimes rt) { // usec x mW
    /**
     * Multiple transmission levels of CC2420 can be matched with their transmission powers
     *  as results from:
     *   https://www.mail-archive.com/tinyos-help@millennium.berkeley.edu/msg18454.html
     *  where they calculate and fit the data into a square-root curve.
     *  The results are yielded as following:
     *
     *  > PA_LEVEL:
     *  > 31  30
     *  > 29  28  27  26  25  24  23  22  21  20
     *  > 19  18  17  16  15  14  13  12  11  10
     *  >  9   8   7   6   5   4   3   2   1   0
     *
     *  > Output transmission power (dBm):
     *  >   0       -0.0914
     *  >  -0.3008  -0.6099  -1.0000  -1.4526  -1.9492  -2.4711  -3.0000  -3.5201  -4.0275  -4.5212
     *  >  -5.0000  -5.4670  -5.9408  -6.4442  -7.0000  -7.6277  -8.3343  -9.1238 -10.0000 -10.9750
     *  > -12.0970 -13.4200 -15.0000 -16.8930 -19.1530 -21.8370 -25.0000 -28.6970 -32.9840 -37.9170
     *
     *  > Output power (mW) into the air:
     *  > 1.0000  0.9792
     *  > 0.9331  0.8690  0.7943  0.7157  0.6384  0.5661  0.5012  0.4446  0.3956  0.3531
     *  > 0.3162  0.2840  0.2546  0.2268  0.1995  0.1727  0.1467  0.1224  0.1000  0.0799
     *  > 0.0617  0.0455  0.0316  0.0205  0.0122  0.0066  0.0032  0.0013  0.0005  0.0002
     *
     * Some experimental result of transmission power (in dBm) is also shared in:
     *  http://tinyos-help.10906.n7.nabble.com/Output-power-at-each-power-level-from-0-to-31-for-CC2420-td10.html
     *
     * Anyway those aforementioned information do not give directly a current consumption in each level.
     * But, you can find more resources in the book:
     *  title={Fundamentals of wireless sensor networks: theory and practice},
     *  author={Dargie, Waltenegus and Poellabauer, Christian},
     *  year={2010},
     *  publisher={John Wiley \& Sons}
     */

//  return rt.tx * RadioCurrent.TX * VOLTAGE; // Traditional static transmission power consumption model

    double sum_tx_pow = 0;
    for (int i = 0; i < rt.multiTx.length; i ++)
      sum_tx_pow += rt.multiTx[i] * CURRENT_RADIO_TX[i];
    return sum_tx_pow * VOLTAGE;
  }

  private double calculateRadioEnergyRx(RadioTimes rt) { // usec x mW
    return (rt.rx * RadioCurrent.RX) * VOLTAGE;
  }

  private double calculateRadioEnergyInterfered(RadioTimes rt) { // usec x mW
    return (rt.interfered * RadioCurrent.INTERFERED) * VOLTAGE;
  }

  private double calculateRadioEnergyIdle(RadioTimes rt) { // usec x mW
    return (rt.idle * RadioCurrent.IDLE) * VOLTAGE;
  }

  private double calculateRadioEnergySleep(RadioTimes rt) { // usec x mW
    return rt.off * RadioCurrent.OFF * VOLTAGE;
  }

  private double calculateRadioEnergy(RadioTimes rt) { // usec x mW
    return calculateRadioEnergyTx(rt) +
        calculateRadioEnergyRx(rt) +
        calculateRadioEnergyInterfered(rt) +
        calculateRadioEnergyIdle(rt) +
        calculateRadioEnergySleep(rt);
  }

  // --------------------------------------------------------------------------
  public long getCPUTimeActive(long [] times) {
    return times[MSP430Constants.MODE_ACTIVE];
  }

  public long getCPUTimeTotal(long [] times) {
    return times[MSP430Constants.MODE_ACTIVE]
        + times[MSP430Constants.MODE_LPM0]
        + times[MSP430Constants.MODE_LPM1]
        + times[MSP430Constants.MODE_LPM2]
        + times[MSP430Constants.MODE_LPM3]
        + times[MSP430Constants.MODE_LPM4];
  }

  public double getTotalConsumedEnergy() {
    return ( // mJ on micro seconds div by 1e6 to converse
        calculateCPUEnergy(cpuModeTimesTotal) +
        calculateRadioEnergy(radioTimesTotal)) / 1000000.0;
  }

  public double getAveragePower() { // return average power consumption by radio and cpu in mW
    updateCPUStats();
    updateRadioStats();
    return
        calculateCPUEnergy(cpuModeTimes) / getCPUTimeTotal(cpuModeTimes) +
        calculateRadioEnergy(radioTimes) / radioTimes.duration;
  }

  // --------------------------------------------------------------------------
  public static double percentage(long num, long den) {
    return (den > 0)? (double)(100 * num) / den  :  0;
  }

  public double percentageTimeCPUActive(long [] times) {
    return (double)(100 * getCPUTimeActive(times)) / getCPUTimeTotal(times);
  }

  public double percentageTimeRadioTx(RadioTimes rt) {
    return percentage(rt.tx, rt.duration);
  }

  public double percentageTimeRadioRx(RadioTimes rt) {
    return percentage(rt.rx, rt.duration);
  }

  public double percentageTimeRadioInterfered(RadioTimes rt) {
    return percentage(rt.interfered, rt.duration);
  }

  public double percentageTimeRadioIdle(RadioTimes rt) {
    return percentage(rt.idle, rt.duration);
  }

  // --------------------------------------------------------------------------
  public static String makeRadioSummaryStatistics( // All times are shown in uS
      boolean radioHW, boolean radioRXTX,
      boolean showDuration, boolean showIdle,
      RadioTimes rt, String prefix) {

    // the idea is also brought from PowerTracker
    StringBuilder sb = new StringBuilder();
    String fm = "%d us %2.2f %%\n";

    if (showDuration)
      sb.append(prefix + String.format("MONITORED %d us\n", rt.duration));
    if (radioHW)
      sb.append(prefix + String.format("ON " + fm, rt.on, percentage(rt.on, rt.duration)));
    if (radioRXTX) {
      sb.append(prefix + String.format("TX " + fm, rt.tx, percentage(rt.tx, rt.duration)));
      sb.append(prefix + String.format("RX " + fm, rt.rx, percentage(rt.rx, rt.duration)));
      sb.append(prefix + String.format("INT " + fm, rt.interfered, percentage(rt.interfered, rt.duration)));
    }
    if (showIdle)
      sb.append(prefix + String.format("IDLE " + fm, rt.idle, percentage(rt.idle, rt.duration)));

    return sb.toString();
  }

  public static String makeRadioTxStatistics(RadioTimes rt, String prefix) { // Show transmission times, levels, in uS
    StringBuilder sb = new StringBuilder();
    long tx = 0;
    sb.append(prefix + String.format("TX %d >", rt.tx));
    for (int i = 0; i < rt.multiTx.length; i++) {
      long l = rt.multiTx[i];
      sb.append(String.format(" %d:%d(%2.2f %%)", i, l, percentage(l, rt.tx)));
      tx += rt.multiTx[i];
    }
    sb.append(String.format(" =%d(%2.2f %%)", tx, percentage(tx, rt.tx)));
    sb.append("\n");
    return sb.toString();
  }

  //--------------------------------------------------------------------------
  public String radioStatistics() {
    return makeRadioSummaryStatistics(true, true,
        true /* show duration */, true /* show idle time */,
        radioTimesTotal, String.format("Mote %d: ", mote.getID()));
  }

  public String radioTxStatistics() { // Show transmission times, levels, in uS
    return makeRadioTxStatistics(
        radioTimesTotal, String.format("Mote %d: ", mote.getID()));
  }

  //--------------------------------------------------------------------------
  public void snapStatistics() { // Keep snapshorts of CPU and Radio times
    for (int i = 0; i < cpuModeTimesTotal.length; i++)
      cpuModeTimesTotalSnapshot[i] = cpuModeTimesTotal[i];
    radioTimesTotalSnapshot.setMembersAs(radioTimesTotal);
  }

  public String getSnappedStatistics() {
    StringBuilder sb = new StringBuilder();
    double energyCPU = calculateCPUEnergy(cpuModeTimesTotalSnapshot) / 1e6;
    double energyRadioTx   = calculateRadioEnergyTx(radioTimesTotalSnapshot) / 1e6;
    double energyRadioRx   = calculateRadioEnergyRx(radioTimesTotalSnapshot) / 1e6;
    double energyRadioIdle = calculateRadioEnergyIdle(radioTimesTotalSnapshot) / 1e6;
    double energyRadioInterfered = calculateRadioEnergyInterfered(radioTimesTotalSnapshot) / 1e6;

    sb.append("cpu:mJ="  + energyCPU + ", ");
    sb.append("cpu:%="   + percentageTimeCPUActive(cpuModeTimesTotalSnapshot) + ", ");

    sb.append("rx:mJ="   + energyRadioRx + ", ");
    sb.append("rx:%="    + percentageTimeRadioRx(radioTimesTotalSnapshot) + ", ");

    sb.append("int:mJ="  + energyRadioInterfered + ", ");
    sb.append("int:%="   + percentageTimeRadioInterfered(radioTimesTotalSnapshot) + ", ");

    sb.append("idle:mJ=" + energyRadioIdle + ", ");
    sb.append("idle:%="  + percentageTimeRadioIdle(radioTimesTotalSnapshot) + ", ");

    sb.append("tx:mJ="   + energyRadioTx + ", ");
    sb.append("tx:%="    + percentageTimeRadioTx(radioTimesTotalSnapshot) ); //+ ", ");

    for (int i = 0; i < radioTimesTotalSnapshot.multiTx.length; i++) {
      sb.append(String.format(", tx%d:%%=%2.2f", i,
          percentage(radioTimesTotalSnapshot.multiTx[i], radioTimesTotalSnapshot.tx)));
    }

    return sb.toString();
  }

}
