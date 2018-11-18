package design.senior.bluetooth;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.bluetooth.BluetoothDevice;
import android.databinding.Bindable;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends ViewModel {

    public MutableLiveData<List<BluetoothDevice>> deviceList;
    public boolean thisIsMe = false;

    public MainViewModel(){
        if(deviceList == null)
            deviceList = new MutableLiveData<>();
    }

    public void addToList(BluetoothDevice device){
        List<BluetoothDevice> list = deviceList.getValue();
        if(list == null)
            list = new ArrayList<>();

        list.add(device);
        deviceList.setValue(list);
    }

    public MutableLiveData<List<BluetoothDevice>> getDeviceList() {
        if(deviceList == null)
            deviceList = new MutableLiveData<>();

        return deviceList;
    }
}
