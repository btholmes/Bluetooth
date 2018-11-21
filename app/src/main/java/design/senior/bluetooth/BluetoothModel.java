package design.senior.bluetooth;

import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableField;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

public class BluetoothModel extends BaseObservable {

    private String heard = "Found";
    private String missed = "Not Found";

    /**
     * in ms
     */
    public static int windowSize = 750;
    /**
     * Default duration is 0.03 m/s
     */
    public static double duration = 0.03;
    public static double defaultTone = 8100.0;
    public static int chirpDelay = 1250;
    public static boolean chirpWindow = false;
    public boolean heardChirp = false;

    public void setHeardChirp(boolean heard){
        this.heardChirp = heard;
        notifyPropertyChanged(BR.heardChirp);
    }

    @Bindable
    public String getHeard(){
        return this.heard;
    }

    @Bindable
    public String getMissed(){
        return this.missed;
    }

    @Bindable
    public boolean getHeardChirp(){
        return this.heardChirp;
    }

    public void setChirpWindow(boolean window){
        this.chirpWindow = window;
        notifyPropertyChanged(BR.chirpWindow);
    }

    @Bindable
    public boolean getChirpWindow(){
        return this.chirpWindow;
    }

    @Bindable
    public String getChirpDelay(){
        return chirpDelay + "";
    }

    public void setChirpDelay(int delay){
        this.chirpDelay = delay;
        notifyPropertyChanged(BR.chirpDelay);
    }

    private TextWatcher chirpWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            try{
                String string = s.toString();
                Integer newDelay = Integer.parseInt(string);
                setChirpDelay(newDelay);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    @Bindable
    public TextWatcher getChirpWatcher(){
        return this.chirpWatcher;
    }

    public double getTone(){
        return this.defaultTone;
    }

    @Bindable
    public String getDefaultTone() {
        return String.format("%.1f", defaultTone);
    }

    public void setDefaultTone(double defaultTone) {
        this.defaultTone = defaultTone;
        notifyPropertyChanged(BR.defaultTone);
    }

    private TextWatcher durationWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            try{
                String string = s.toString();
                Double newDuration = Double.parseDouble(string);
                setDuration(newDuration);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    public void setDuration(double duration){
        this.duration = duration;
        notifyPropertyChanged(BR.duration);
    }

    @Bindable
    public String getDuration() {
        return String.format("%.2f", duration);
    }

    public TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            try{
                double freq = 0;
                String newFreq = s.toString();
                freq = Double.parseDouble(newFreq);
                if(freq >= 1000){
                    setDefaultTone(freq);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };


    @Bindable
    public TextWatcher getWatcher() {
        return this.watcher;
    }

    @Bindable
    public TextWatcher getDurationWatcher() {
        return durationWatcher;
    }

    private boolean changingFrequency = false;
    private String bluetoothMessage;
    private String defaultMessage = "Bluetooth Message";

    private String startText ="Start";
    private String stopText = "Stop";
    private boolean isRunning = false;

    private double temperature;
    /**
     * in meters/second
     */
    private double speedOfSoundInAir = 343;

    private long playDelay = -1;
    private long elapsedCalculationTime = -1;

    private boolean isMyPhone = false;

    private double timeDilationDistance = 0.0;


    private String searchingText;
    private String freqOfTone;

    private BluetoothDevice phone1;
    private BluetoothDevice phone2;
    private int rSSI1;
    private int rSSI2;

    private boolean newSearch;
    private int phone1Distance;

    private long serverTimeHeard = 0;
    private long clientTimeHeard = 0;

    public BluetoothModel(){
        bluetoothMessage = null;
        newSearch = false;
        rSSI1 = 0;
        rSSI2 = 0;
        phone1Distance = -1;
    }



    public void setPhone1(String hardware, String name, int rssi){
        if(name.equalsIgnoreCase("Phone1")){
            phone1 = new BluetoothDevice(hardware, name, rssi);
        }
        setrSSI1(rssi);
    }

    public void setPhone2(String hardware, String name, int rssi){
        if(name.equalsIgnoreCase("Phone2")){
            phone2 = new BluetoothDevice(hardware, name, rssi);
        }
        setrSSI2(rssi);
    }

    private int RSS0 = -63;
    private int p = 2;
    private int d0 = 1;

    public void setrSSI1(int rSSI1) {
        this.rSSI1 = rSSI1;
        setPhone1Distance(getPhoneDistance(rSSI1));
        notifyPropertyChanged(BR.rSSI1);
    }

    public void setPhone1Distance(int value){
        this.phone1Distance = value;
        notifyPropertyChanged(BR.phone1Distance);
    }

    public int getPhoneDistance(int rSSI1){
        if(rSSI1 >= -70 && rSSI1 <= 0)
            return 1;
        else
            return 2;
    }

    public void setrSSI2(int rSSI2) {
        this.rSSI2 = rSSI2;
        notifyPropertyChanged(BR.rSSI2);
    }

    public void setNewSearch(){
        if(this.newSearch)
            this.newSearch = false;
        else
            this.newSearch = true;
        notifyPropertyChanged(BR.newSearch);
    }

    public void setSearchingText(String text){
        this.searchingText = text;
        notifyPropertyChanged(BR.searchingText);
    }

    public void setBluetoothMessage(String message){
        this.bluetoothMessage = message;
        notifyPropertyChanged(BR.bluetoothMessage);
    }

    public void setFreqOfTone(String freqOfTone){
        this.freqOfTone = freqOfTone;
        notifyPropertyChanged(BR.freqOfTone);
    }

    public void setIsMyPhone(boolean myPhone){
        this.isMyPhone = myPhone;
        notifyPropertyChanged(BR.isMyPhone);
    }

    public void setTimeDilationDistance(double distance){
        this.timeDilationDistance = distance;
        notifyPropertyChanged(BR.timeDilationDistance);
    }

    public long getDelay(){
        if(this.serverTimeHeard >= 0 && this.clientTimeHeard >= 0){
            if(this.clientTimeHeard >= this.serverTimeHeard)
                return this.clientTimeHeard - this.serverTimeHeard;
        }
        return -1;
    }

    private float metersPerMiliSecondds = .343f;
    private final int systemDelay = 100;

    /**
     * Get distance as meters/milisecond
     * @return
     */
    public double getDistance(){
        long diff = getDelay();
        if(diff >= 0){
            double meters = (diff)*(speedOfSoundInAir/1000.0);
            return meters;
        }
        return -1;
    }

    public void setPlayDelay(long playDelay){
        this.playDelay = playDelay;
        notifyPropertyChanged(BR.playDelay);
    }

    public void setElapsedCalculationTime(long elapsedCalculationTime){
        this.elapsedCalculationTime = elapsedCalculationTime;
        notifyPropertyChanged(BR.elapsedCalculationTime);
    }

    public void setServerTimeHeard(long serverTimeHeard) {
        this.serverTimeHeard = serverTimeHeard;
        notifyPropertyChanged(BR.serverTimeHeard);
    }

    public void setClientTimeHeard(long clientTimeHeard) {
        this.clientTimeHeard = clientTimeHeard;
        notifyPropertyChanged(BR.clientTimeHeard);
    }

    public void setTemperature(double temp){
        this.temperature = temp;
        setSpeedOfSoundInAir();
        notifyPropertyChanged(BR.temperature);
    }

    public void setSpeedOfSoundInAir(){
        this.speedOfSoundInAir = 331.3 + (0.606 * this.temperature);
        notifyPropertyChanged(BR.speedOfSoundInAir);
    }

    @Bindable
    public String getSpeedOfSoundInAir(){
        return String.format("%.2f m/s", this.speedOfSoundInAir);
    }

    @Bindable
    public String getTemperature(){
        return String.format("%.2f celsius", this.temperature);
    }

    @Bindable
    public String getServerTimeHeard(){
        return serverTimeHeard + " ms";
    }

    @Bindable
    public String getClientTimeHeard(){
        return clientTimeHeard + " ms";
    }

    @Bindable
    public String getPlayDelay(){
        return "Play Delay: " + this.playDelay;
    }

    @Bindable
    public String getElapsedCalculationTime(){
        return "Elapsed Calc Time: " + this.elapsedCalculationTime;
    }

    @Bindable
    public String getTimeDilationDistance(){
        return String.format("%.2f meters", timeDilationDistance);
    }
    @Bindable
    public boolean getIsMyPhone(){
        return this.isMyPhone;
    }

    @Bindable
    public String getFreqOfTone(){
        return freqOfTone;
    }

    @Bindable
    public String getSearchingText(){
        return this.searchingText;
    }

    @Bindable
    public String getBluetoothMessage(){
        return this.bluetoothMessage;
    }

    @Bindable
    public String getDefaultMessage(){
        return this.defaultMessage;
    }

    @Bindable
    public boolean getNewSearch(){
        return this.newSearch;
    }

    @Bindable
    public String getRSSI1(){
        return rSSI1 + "";
    }

    @Bindable
    public String getRSSI2(){
        return rSSI2 + "";
    }

    @Bindable
    public String getPhone1Distance(){
        if(this.phone1Distance == -1)
            return "Out of range";
        else
            return "~" + phone1Distance + "meter";
    }

    public class BluetoothDevice{

        private String hardwareAddress;
        private String name;
        private int rssi;

        public BluetoothDevice(String hardwareAddress, String name, int rssi){
            this.hardwareAddress = hardwareAddress;
            this.name = name;
            this.rssi = rssi;
        }

        public String getHardwareAddress() {
            return hardwareAddress;
        }

        public void setHardwareAddress(String hardwareAddress) {
            this.hardwareAddress = hardwareAddress;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getRssi() {
            return rssi;
        }

        public void setRssi(int rssi) {
            this.rssi = rssi;
        }
    }


    @Bindable
    public String getStartText() {
        return startText;
    }

    public void setStartText(String startText) {
        this.startText = startText;
    }

    @Bindable
    public String getStopText() {
        return stopText;
    }

    public void setStopText(String stopText) {
        this.stopText = stopText;
    }

    @Bindable
    public boolean getIsRunning() {
        return isRunning;
    }

    public void setIsRunning(boolean running) {
        isRunning = running;
        notifyPropertyChanged(BR.isRunning);
    }

    public static int newDefaultTone = -1;
    /**
     * ON click method for server
     * @param view
     */
    public void onServerPauseClick(View view){
        if(isRunning)
            setIsRunning(false);
        else{
            setIsRunning(true);
            /**
             * Broadcast new defaultTone to server
             */
            if(isMyPhone){

                newDefaultTone = 1;
            }
        }
    }


    private boolean exactComparison = true;

    public void setExactComparison(boolean exactComparison){
        this.exactComparison = exactComparison;
    }

    public boolean getExactComparison(){
        return this.exactComparison;
    }

    private SwitchCompat.OnCheckedChangeListener checkedChangeListener = new SwitchCompat.OnCheckedChangeListener(){
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setExactComparison(isChecked);
        }
    };

    @Bindable
    public SwitchCompat.OnCheckedChangeListener getCheckedChangeListener(){
        return this.checkedChangeListener;
    }


    public static boolean usePhoneSpeaker = true;

    private SwitchCompat.OnCheckedChangeListener phoneSpeakerListener = new SwitchCompat.OnCheckedChangeListener(){
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            usePhoneSpeaker = isChecked;
        }
    };


    @Bindable
    public SwitchCompat.OnCheckedChangeListener getPhoneSpeakerListener(){
        return this.phoneSpeakerListener;
    }
}
