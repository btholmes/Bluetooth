package design.senior.bluetooth;

import android.Manifest;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import android.bluetooth.BluetoothSocket;
import android.widget.TextView;
import android.widget.Toast;

import design.senior.bluetooth.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    /**
     * 128 bit format
     */
    private UUID SERVICE_ID = UUID.fromString("795090c7-420d-4048-a24e-18e60180e23c");

    private Context ctx;
    private MainViewModel viewModel;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    private int REQUEST_ENABLE_BT = 1111;
    private int REQUEST_LOCATION = 2222;


    private boolean phone1Found = false;
    private boolean BLUEOOTH_ENABLED = false;
    private boolean CAN_ACCESS_LOCATION = false;

    private BluetoothModel bluetoothModel;

    private TextView searchingText;

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();

                if(deviceName != null){
                    if(deviceName.equalsIgnoreCase("Phone2") || deviceName.equalsIgnoreCase("Phone1") ){
                        /**
                         * Required to make a bluetooth connection with a device
                         */
                        String deviceHardwareAddress = device.getAddress(); // MAC address
                        int  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                        if(deviceName.equalsIgnoreCase("Phone1") ){
                            phone1Found = true;
                            bluetoothModel.setPhone1(deviceHardwareAddress, deviceName, rssi);
                        }else{
                            bluetoothModel.setPhone2(deviceHardwareAddress, deviceName, rssi);
                        }


//                        connectToDevice(device);
                    }
                }

                viewModel.addToList(device);
            }
        }
    };

    private final BroadcastReceiver advertisingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /**
             * Discovery has finished, so restart
             */
            if(action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
                enableDiscoverable();
            }
        }
    };


    Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                //read
                case 0:
                    byte[] array = (byte[]) msg.obj;
                    String s = new String(array);
                    Toast.makeText(ctx, "Message received " + s , Toast.LENGTH_LONG);
                    break;
                //write
                case 1:
                    byte[] array2 = (byte[]) msg.obj;
                    break;
                //toast
                case 2:
                    break;

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.ctx = this;
        this.bluetoothModel = new BluetoothModel();

        ActivityMainBinding mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mainBinding.setLifecycleOwner(this);
        mainBinding.setData(bluetoothModel);


        /**
         * Register viewmodel and observer
         */
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        viewModel.getDeviceList().observe(this, new Observer<List<BluetoothDevice>>() {
            @Override
            public void onChanged(@Nullable List<BluetoothDevice> bluetoothDevices) {
                int a = 0;
                Log.e("ViewModel", "just here");
            }
        });

        enableBluetooth();
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        /**
         * Unique Id to identify to the target device from the beacon devices(Phone1, Phone2)
         */
        String deviceID = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        /**
         * This is my phone, AKA the phone which will be walking a path
         *
         * DEVICE ID's
         * f94ff5d54dcb186b = MyPhone
         *
         * 9f30a05d0a75ed28 = Phone1
         * c9be7d62dab9040 = Phone2
         */
        if(deviceID.equals("f94ff5d54dcb186b")){
            setThisIsMe();
        }else if(deviceID.equals("9f30a05d0a75ed28") || deviceID.equals("c9be7d62dab9040")){
            setPhone1And2();

        }
    }

    /**
     * Basic functionality to enable Bluetooth Discvoery and Discoverability on phone
     */
    private void enableBluetooth(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.e("Bluetooth", "This device does not support bluetooth");
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Timer task gets called twice at each schedule, so this variable checks to see if task has just run, if so, then
     * no need to execute it again.
     */
    boolean run = false;

    /**
     * Creates a timer task to run every 3seconds, and start a rediscovery of bluetooth devices
     */
    private void setThisIsMe(){
        /**
         * Register a receiver for discovery of bluetooth devices on (ThisIsMe)
         */
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
        bluetoothAdapter.startDiscovery();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(run){
                    if(!phone1Found){
                        bluetoothModel.setPhone1Distance(-1);
                    }
                    phone1Found = false;
                    bluetoothModel.setNewSearch();
                    bluetoothAdapter.startDiscovery();
                    run = false;
                }else
                    run = true;
            }
        }, 0, 10000);
    }

    private void setPhone1And2(){
        /**
         * Register an advertising receiver for nearby devices to discover me (Phone1, Phone2)
         */
        IntentFilter advertisingFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(advertisingReceiver, advertisingFilter);
        enableDiscoverable();
    }

    private void setScanCallback(){
        BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {

            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                if (device.getName() == null || !device.getName().equalsIgnoreCase("Phone1")
                        || !device.getName().equalsIgnoreCase("Phone2")) {
                    return;
                }

                if(device.getName().equalsIgnoreCase("Phone1")){
                    bluetoothModel.setPhone1(device.getAddress(), device.getName(), rssi);
                }else{
                    bluetoothModel.setPhone2(device.getAddress(), device.getName(), rssi);
                }
                // here you can get rssi of specified bluetooth device if it's available.
                // and try to connect it programmatically.
//                connect(device.getAddress());
            }
        };
        bluetoothAdapter.startLeScan(leScanCallback);// find match device
    }

    private boolean hasPermissions(){
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED);
    }


    private void getPermissions(){
        CAN_ACCESS_LOCATION = false;
        if(!hasPermissions())
        {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }else{
            CAN_ACCESS_LOCATION = true;
            discoverBluetoothDevices();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                BLUEOOTH_ENABLED = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_LOCATION){
            if (grantResults.length == 2) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                     CAN_ACCESS_LOCATION = true;
                     discoverBluetoothDevices();
                }
            }
        }
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPermissions();
    }

    /**
     * Helper function starts discovery of bluetooth devices after it verifies permissions are granted
     *
     * just call startDiscovery to discover Bluetooth devices
     */
    private void discoverBluetoothDevices(){
//        startAcceptThread();
//        bluetoothAdapter.startDiscovery();
        setThisIsMe();
    }


    /**
     * In order to connect to a device, these two devices must be paired, otherwise a user confirmation
     * is required before the connection can continue.
     *
     * RFCOMM allows only 1 connected client at a time. In this case, the server is the device
     * ThisIsMe, and the clients, are the other devices.
     *
     * @param device
     */
//    private void connectToDevice(BluetoothDevice device){
//        new ConnectThread(device).run();
//    }
//
//    private void startAcceptThread(){
//        new AcceptThread().run();
//    }

    /**
     *
     * TODO Stop advertising once connected, and add a timeout for advertising to conserve battery
     *
     * Bluetooth devices are not discoverable by default, enable general discovery for long term
     * advertising
     *
     * So, android does not allow programmers to make a phone discoverable for bluetooth connections
     * without a user confirmation. This is problematic, and means that at most, every hour, the user
     * will have to confirm the device to again be discoverable.
     *
     */
    private void enableDiscoverable(){
        /**
         * Setting Extra_Discoverable_Duration = 0 eanbles the device to always be discoverable
         * duration is in seconds, so 60 seconds = 1 minute
         */
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
        startActivity(discoverableIntent);
    }

    /**
     * Method looks for devices that are currently paired with this device, if I find the device I'm looking
     * for here, then there is no need to startDiscovery(), also since discovering is very intensive, and
     * uses a lot of battery, it is better to search through paired devices first.
     */
    private void findPairedDevices(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if(deviceName.equalsIgnoreCase("ThisIsMe")){
                    /**
                     * Connect to device
                     */

                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }



//    private class AcceptThread extends Thread {
//        private final BluetoothServerSocket mmServerSocket;
//
//        public AcceptThread() {
//            // Use a temporary object that is later assigned to mmServerSocket
//            // because mmServerSocket is final.
//            BluetoothServerSocket tmp = null;
//            try {
//                // MY_UUID is the app's UUID string, also used by the client code.
//                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Name", SERVICE_ID);
//            } catch (IOException e) {
//                Log.e("Accept socket exception", "Socket's listen() method failed", e);
//            }
//            mmServerSocket = tmp;
//        }
//
//        public void run() {
//            BluetoothSocket socket = null;
//            // Keep listening until exception occurs or a socket is returned.
//            while (true) {
//                try {
//                    socket = mmServerSocket.accept();
//                } catch (IOException e) {
//                    Log.e("Accept", "Socket's accept() method failed", e);
//                    break;
//                }
//
//                if (socket != null) {
//                    // A connection was accepted. Perform work associated with
//                    // the connection in a separate thread.
//                    manageMyConnectedSocket(socket);
//                    try{
//                        mmServerSocket.close();
//                    }catch (Exception e){
//                        e.printStackTrace();
//                    }
//
//                    break;
//                }
//            }
//        }
//
//        // Closes the connect socket and causes the thread to finish.
//        public void cancel() {
//            try {
//                mmServerSocket.close();
//            } catch (IOException e) {
//                Log.e("Accept is canceled", "Could not close the connect socket", e);
//            }
//        }
//    }
//
//
//    /**
//     * Connect thread, is used by all devices except ThisIsMe
//     */
//    private class ConnectThread extends Thread {
//        private final BluetoothSocket mmSocket;
//        private final BluetoothDevice mmDevice;
//
//        public ConnectThread(BluetoothDevice device) {
//            // Use a temporary object that is later assigned to mmSocket
//            // because mmSocket is final.
//            BluetoothSocket tmp = null;
//            mmDevice = device;
//
//            try {
//                // Get a BluetoothSocket to connect with the given BluetoothDevice.
//                // MY_UUID is the app's UUID string, also used in the server code.
//                tmp = device.createRfcommSocketToServiceRecord(SERVICE_ID);
//            } catch (IOException e) {
////                Log.e(TAG, "Socket's create() method failed", e);
//            }
//            mmSocket = tmp;
//        }
//
//        public void run() {
//            // Cancel discovery because it otherwise slows down the connection.
//            bluetoothAdapter.cancelDiscovery();
//
//            try {
//                // Connect to the remote device through the socket. This call blocks
//                // until it succeeds or throws an exception.
//                mmSocket.connect();
//            } catch (IOException connectException) {
//                // Unable to connect; close the socket and return.
//                try {
//                    mmSocket.close();
//                } catch (IOException closeException) {
////                    Log.e(TAG, "Could not close the client socket", closeException);
//                }
//                return;
//            }
//
//            // The connection attempt succeeded. Perform work associated with
//            // the connection in a separate thread.
//            manageMyConnectedSocket(mmSocket);
//        }
//
//        // Closes the client socket and causes the thread to finish.
//        public void cancel() {
//            try {
//                mmSocket.close();
//            } catch (IOException e) {
////                Log.e(TAG, "Could not close the client socket", e);
//            }
//        }
//    }
//
//    /**
//     * Send a message to this connected device
//     * @param socket
//     */
//    private void manageMyConnectedSocket(BluetoothSocket socket){
//        MyBluetoothService service = new MyBluetoothService(mHandler, socket);
//        String s = "Im connected";
//        service.write(s.getBytes());
//    }
}
