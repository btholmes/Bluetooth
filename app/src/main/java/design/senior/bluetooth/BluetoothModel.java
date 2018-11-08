package design.senior.bluetooth;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

public class BluetoothModel extends BaseObservable {

    private String bluetoothMessage;
    private String defaultMessage = "Bluetooth Message";

    private String searchingText;

    private BluetoothDevice phone1;
    private BluetoothDevice phone2;
    private int rSSI1;
    private int rSSI2;

    private boolean newSearch;
    private int phone1Distance;

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
}
