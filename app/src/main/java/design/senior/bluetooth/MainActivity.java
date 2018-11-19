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
import android.content.SharedPreferences;
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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import android.bluetooth.BluetoothSocket;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.primitives.Longs;

import design.senior.bluetooth.SoundRecorder.Callback;
import design.senior.bluetooth.SoundRecorder.Recorder;
import design.senior.bluetooth.calculators.AudioCalculator;
import design.senior.bluetooth.databinding.ActivityMainBinding;

/**
 * Client controls socket. Client opens and closes RFCOMM channel. In this implementation
 * client does all reading, while server does all writing over socket. Everytime the client receives a
 * message, it closes the socket. Then server starts device discoverability again, and client starts discovery.
 */
public class MainActivity extends AppCompatActivity {


    private static final String PREFS_NAME = "We have one shared pref... and it lives here";
    private static final String REALM_REFRESH_DATE = "string, stores last time Realm was completely deleted from phone";
    private long bluetoothMessageTimeReceived = -1;
    private long soundTimeReceived = -1;
    private boolean chirpHeard = false;

    private boolean MyPhonesIsConnected = false;

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
    private boolean CAN_RECORD_AUDIO = false;

    private BluetoothModel bluetoothModel;
    private Presenter presenter;

    private MyBluetoothService service;

    /**
     * Boolean used to determine if device will be client (myPhone) or
     * server side of Bluetooth socket connections (Phone1 Phone2)
     */
    private Handler mainHandler;
    private Handler defaultHandler;
    private boolean isTrackingPhone = false;
    private Timer timer;
    private TextView searchingText;
    private Recorder recorder;

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
                            timer.cancel();
                            phone1Found = true;
                            bluetoothModel.setPhone1(deviceHardwareAddress, deviceName, rssi);

                            /**
                             * Iam only connecting my phone to 1 device at a time
                             */
                            if(!MyPhonesIsConnected)
                                connectToDevice(device);
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


    /**
     * Day is formattted as dd-MM-yyyy
     *
     * This method doesn't actually belong to this application, but I put it here for now.
     * Check if new day has begun since the last time you opened the app.. If so, wipe
     * old realm data, begin new day for collection.
     */
    private void checkRealmDataOld(){
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

        Date today = Calendar.getInstance().getTime();
        System.out.println("Current time => " + today);

        String realm_refresh_date = prefs.getString(REALM_REFRESH_DATE, null);
        if(realm_refresh_date == null){
            String formatedDate = sdf.format(today);
            prefs.edit().putString(formatedDate, REALM_REFRESH_DATE).apply();
        }else{
            try{
                Date lastRefreshedDate = sdf.parse(realm_refresh_date);
                if(today.after(lastRefreshedDate)){
                    /**
                     * Today is a new, unrecorded day in Realm history so delete all
                     * from realm
                     */
                }
                String dayFromDate= sdf.format(lastRefreshedDate);

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.ctx = this;
        checkRealmDataOld();


        this.bluetoothModel = new BluetoothModel();
        this.presenter = new Presenter();
        mainHandler = new Handler(Looper.getMainLooper());
        defaultHandler = new Handler();

        ActivityMainBinding mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mainBinding.setLifecycleOwner(this);
        mainBinding.setData(bluetoothModel);
        mainBinding.setPresenter(presenter);


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
//        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

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
            isTrackingPhone = true;
            bluetoothModel.setSearchingText("I'm a client, Searching...");
            setThisIsMe();
        }else if(deviceID.equals("9f30a05d0a75ed28") || deviceID.equals("c9be7d62dab9040")){
            isTrackingPhone = false;
            bluetoothModel.setSearchingText("Server");
            setPhone1And2();

            defaultHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    /**
                     * This is a blocking thread, so wait for Page UI to load before starting it.
                     */
                    startAcceptThread();
                }
            }, 1500);

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
     * Also engages microphone service to begin listening for frequencies (TDOA setup)
     */
    private void setThisIsMe(){
        /**
         * Register a receiver for discovery of bluetooth devices on (ThisIsMe)
         */
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
        bluetoothModel.setIsMyPhone(true);
        recorder = new Recorder(getCallback());
        recorder.start();
        bluetoothAdapter.startDiscovery();
        startTimer();
    }

//    private float metersPerNanoSecond = .000000343f;
//    private float metersPerMiliSecondds = .159f;
//    private final int systemDelay = 100;

    /**
     * If soundTimeReceived was not set, then chirp was missed between the two bluetooth calls.
     *
     * @param elapsedCalculationTime
     */
    private void setTimeDilation(long elapsedCalculationTime){
//        if(soundTimeReceived == -1){
//            bluetoothModel.setBluetoothMessage("-1");
//            bluetoothModel.setTimeDilationDistance(-1);
//        }else{
//            /**
//             * Difference between when server first hears frequency, and when client first hears frequency,
//             * the server should be 0m/s, and client should be 0 + delay
//             */
//            if(playDelay > 0 && elapsedCalculationTime >= playDelay){
//                long diff = elapsedCalculationTime-playDelay;
//                bluetoothModel.setBluetoothMessage(diff + " mili seconds");
//                float meters = (diff)*metersPerMiliSecondds;
//                bluetoothModel.setTimeDilationDistance(meters);
//            }
//        }
    }

//    /**
//     * time in m/s or seconds and speed in m/s or m/ms
//     * @param time
//     * @return
//     */
//    private double getDistance(long time){
//        return 0.5*speedOfSound*time;
//    }
//
//    //    private double speedOfSound = 340.29;  //m/s
//    private double speedOfSound = 0.34029;  //m/ms
//    private long time = 0;
//
//    private double getVelocity(double frequency){
//        return ((frequency/freqOfTone)*speedOfSound)-speedOfSound;
//    }

    /**
     * Two waves are equal as long as they are no greater than 1 hz apart
     * @param start
     * @param end
     * @return
     */
    private boolean areEqual(double start, double end){
        double threshold = 1.0;
        double diff = start-end;
        if(end > start)
            diff = end-start;
        if(diff < threshold)
            return true;

        return false;
    }

    private long startCalculationTime = -1;
    long elapsedTime;
    private int frequency = 14100;
    private AudioCalculator audioCalculator = new AudioCalculator();

    private Callback getCallback(){
        return new Callback() {

            /**
             * This method is only useful in the time between when the first bluetooth, and the chirp is heard,
             * it is not useful after the second bluetooth message is received, because that means it was missed.
             *
             * @param buffer
             */
            @Override
            public void onBufferAvailable(byte[] buffer) {
                if (soundTimeReceived == -1 && firstReceived && bluetoothMessageTimeReceived != -1) {
//                    if (startCalculationTime == -1)
//                        startCalculationTime = System.currentTimeMillis();

                    audioCalculator.setBytes(buffer);
//                int amplitude = audioCalculator.getAmplitude();
//                double decibel = audioCalculator.getDecibel();
                    final double wave = audioCalculator.getFrequency();
//                if(areEqual(wave, frequency)){
                    if (wave >= 3000) {
//                        if (startCalculationTime > 0) {
                            chirpHeard = true;
                            soundTimeReceived = System.currentTimeMillis();
//                        /**
//                         * If this delay is greater than soundPlaybackDelay, then chirp was missed
//                         */
//                            elapsedTime = (soundTimeReceived - startCalculationTime);
                            elapsedTime = (soundTimeReceived - bluetoothMessageTimeReceived);
                            bluetoothModel.setElapsedCalculationTime(elapsedTime);
//                            setTimeDilation(elapsedTime);
//                        }

//                    bluetoothModel.setFreqOfTone((int)wave);
                    }

                }
            }
        };
    }

    private void startTimer(){
        timer = new Timer();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
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
                }, 0, 3000);
            }
        });
    }

    private void setPhone1And2(){
        /**
         * Register an advertising receiver for nearby devices to discover me (Phone1, Phone2)
         */
        bluetoothModel.setIsMyPhone(false);
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
//        bluetoothAdapter.startLeScan(leScanCallback);// find match device
    }

    private boolean hasPermissions(){
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED);
    }


    private void getPermissions(){
        CAN_ACCESS_LOCATION = false;
        CAN_RECORD_AUDIO = false;
        if(!hasPermissions())
        {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.RECORD_AUDIO}, REQUEST_LOCATION);
        }else{
            CAN_ACCESS_LOCATION = true;
            CAN_RECORD_AUDIO = true;
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
            if (grantResults.length >= 2) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                     CAN_ACCESS_LOCATION = true;
                    if(grantResults.length == 3){
                        if(grantResults[2] == PackageManager.PERMISSION_GRANTED)
                            CAN_RECORD_AUDIO = true;
                    }
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
//        setThisIsMe();
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
    private void connectToDevice(BluetoothDevice device){
        new ConnectThread(device).run();
    }

    private void startAcceptThread(){
        new AcceptThread().run();
    }

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


    /**
     * Accept thread for bluetooth server (Phone1 Phone2), Server is waiting for a Client
     * to crd
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Name", SERVICE_ID);
            } catch (IOException e) {
                Log.e("Accept socket exception", "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e("Accept", "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket, true);
                    bluetoothModel.setBluetoothMessage("Connection accepted");
                    try{
                        /**
                         * Close unless you want to accept additional connections
                         */
                        mmServerSocket.close();
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e("Accept is canceled", "Could not close the connect socket", e);
            }
        }
    }



    /**
     * Connect thread, used by my phone (client) to connect to server
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(SERVICE_ID);
            } catch (IOException e) {
                Log.e("Couldn't connect", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    /**
                     * Could not connect, so start timer again to try rediscovering device until eventually
                     * a connection is made
                     */
                    MyPhonesIsConnected = false;
                    startTimer();
                } catch (IOException closeException) {
                    Log.e("Client", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            MyPhonesIsConnected = true;
            bluetoothModel.setBluetoothMessage("Connected to device");
            manageMyConnectedSocket(mmSocket, false);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("Couldn't close", "Could not close the client socket", e);
            }
        }
    }


    private void missedChirp(){

    }


    /**
     * This socket holds a connection between a client and server, both client
     * and server need to manage this socket.
     *
     * Client reads, server sends sound and bluetooth messages
     *
     * @param socket
     */
    private void manageMyConnectedSocket(BluetoothSocket socket, boolean server){
        service = new MyBluetoothService(mHandler, socket, server);
    }

    private long soundPlaybackDelay = -1;
    private long playDelay = -1;
    private boolean firstReceived = false;
    private long firstMessageReceivedTime = -1;

    Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                //read
                case 0:
                    /**
                     * If initial bluetooth message was received, then enter if statement:
                     * This next bluetooth message is the inclusive server system duration between
                     * (sending bluetooth message, playing chirp)
                     *
                     * This delay can be used by the client (my phone) to determine the window when
                     * the chirp will be heard.
                     *
                     * Total delay <= time bluetooth message is received + delay
                     *
                     *
                     * If this statement is considered then both bluetooth messages were received the sequence was:
                     *
                     * (1st bluetooth sent, sound sent, 2nd bluetooth sent) -> (1st bluetooth received, soundReceived, 2nd bluetooth received) -> updateTimeDilation
                     *
                     * In this case it is possible soundTimeReceived was not updated, and the chirp was missed
                     * then the sequence would be:
                     *
                     * (1st bluetooth sent, sound sent, 2nd bluetooth sent) -> (1st bluetooth received, sound not received, 2nd bluetooth received) -> updateTimeDilation
                     *
                     * It's also possible for the chirp to reach the mic before this statement is executed,
                     * in that case the sequence followed:
                     *
                     *
                     * (1st bluetooth sent, sound sent, 2nd bluetooth sent) -> (1st bluetooth received, soundReceived, 2nd bluetooth not received) -> updateTimeDilation
                     *
                     *Then the chirp was heard()
                     *
                     */
                    if(firstReceived){
                        byte[] array = (byte[]) msg.obj;
//                        totalDelay = Longs.fromByteArray(array) - firstMessageReceivedTime;
                        long secondReceived = ByteBuffer.wrap(array).getLong();
                        playDelay = secondReceived - firstMessageReceivedTime;
                        bluetoothModel.setPlayDelay(playDelay);
                        if(chirpHeard){
                            /**
                             * Chirp was heard before the 2nd bluetooth message
                             */
                            bluetoothModel.setBluetoothMessage(bluetoothModel.getDelay() + " mili seconds");
                            bluetoothModel.setTimeDilationDistance(bluetoothModel.getDistance());
                            success();
                        }else{
                            /**
                             * Missed the chirp
                             */
                            missedChirp();
//                            setTimeDilation(soundPlaybackDelay);
                        }
                        long startTimeDelay = startCalculationTime - bluetoothMessageTimeReceived;
                        /**
                         * If startCalculation == -1, means chirp was never found, so startTimeDelay
                         * will be less than 0
                         */
                        if(startTimeDelay >= 0){
                            /**
                             * If soundPlaybackDelay takes longer than a second, then sound was missed
                             */
                            if(startTimeDelay > soundPlaybackDelay && soundPlaybackDelay < 1000){
                                missedChirp();
                            }else if(soundPlaybackDelay >= 1000){
                                /**
                                 * Data was messed up, didn't get translated correctly
                                 */
                                badData();
                            }
                        }

                        bluetoothMessageTimeReceived = -1;
                        firstReceived = false;
                    }else{
                        byte[] array = (byte[]) msg.obj;
//                        firstMessageReceivedTime = Longs.fromByteArray(array);
                        firstMessageReceivedTime = ByteBuffer.wrap(array).getLong();
                        bluetoothMessageTimeReceived = System.currentTimeMillis();
                        soundTimeReceived = -1;
                        soundPlaybackDelay = -1;
                        startCalculationTime = -1;
                        playDelay = -1;
//                        bluetoothModel.setBluetoothMessage("-1");
//                        bluetoothModel.setTimeDilationDistance(-1);
                        bluetoothModel.setElapsedCalculationTime(-1);
                        bluetoothModel.setPlayDelay(-1);
                        chirpHeard = false;
                        firstReceived = true;
                    }
                    break;
                //write
                case 1:
//                    byte[] array2 = (byte[]) msg.obj;
                    break;
                //toast
                case 2:
                    break;

            }
        }
    };

    private void badData(){

    }

    private void success(){

    }
}
