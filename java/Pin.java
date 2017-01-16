import se.sics.cooja.ClassDescription;
import se.sics.cooja.mspmote.SkyMote;
import se.sics.mspsim.core.ADC12;
import se.sics.mspsim.core.ADCInput;
import se.sics.mspsim.core.IOUnit;


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
@ClassDescription("Emulation of PIN connection between EnergyStorage and Mote")
public class Pin implements ADCInput {

    private static final int STORAGE_PIN_ID = 11;
    private SkyMote mote;
    private EnergyStorage storage;
    private ADC12 adc;


    public Pin(EnergyStorage storage, SkyMote mote) {
        this.mote = mote;
        this.storage = storage;
        adc = (ADC12) (mote.getCPU().getIOUnit("ADC12"));
        adc.setADCInput(STORAGE_PIN_ID, this);
    }

    @Override
    public int nextData() { // ADCInput interface
        return analogtoDigital(storage.getVoltage());
    }

    int analogtoDigital(double volts) {
        // simple proof of concept. Actual should be ((volts-min)*2^12/(max-min))
        return (int) (1000 * volts);
    }

}
