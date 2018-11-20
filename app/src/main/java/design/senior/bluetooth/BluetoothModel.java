package design.senior.bluetooth;

import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableField;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

public class BluetoothModel extends BaseObservable {

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
                int freq = 0;
                String newFreq = s.toString();
                freq = Integer.parseInt(newFreq);
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

    private int defaultTone = 8100;
    private int b_freq = 3950; // Hz
    private int d_freq = 5925; // Hz
    private int f_freq = 5967; // Hz
    private int a_freq = 7040; // Hz

    public int getTone(){
        return this.defaultTone;
    }

    @Bindable
    public String getDefaultTone() {
        return defaultTone + " ";
    }

    public void setDefaultTone(int defaultTone) {
        this.defaultTone = defaultTone;
        notifyPropertyChanged(BR.defaultTone);
    }

    public int getB_freq() {
        return b_freq;
    }

    public void setB_freq(int b_freq) {
        this.b_freq = b_freq;
    }

    public int getD_freq() {
        return d_freq;
    }

    public void setD_freq(int d_freq) {
        this.d_freq = d_freq;
    }

    public int getF_freq() {
        return f_freq;
    }

    public void setF_freq(int f_freq) {
        this.f_freq = f_freq;
    }

    public int getA_freq() {
        return a_freq;
    }

    public void setA_freq(int a_freq) {
        this.a_freq = a_freq;
    }
}
