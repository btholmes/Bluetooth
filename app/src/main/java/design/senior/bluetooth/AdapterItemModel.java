package design.senior.bluetooth;

import android.arch.lifecycle.ViewModel;
import android.bluetooth.BluetoothDevice;
import android.databinding.BaseObservable;
import android.view.View;

public class AdapterItemModel extends BaseObservable {

    public BluetoothDevice device;

    public AdapterItemModel(){

    }

//    public String getRSSI(){
//        int  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
//    }


}
