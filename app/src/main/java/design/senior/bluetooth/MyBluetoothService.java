package design.senior.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.icu.util.Output;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.common.primitives.Longs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import design.senior.bluetooth.SoundRecorder.Callback;
import design.senior.bluetooth.SoundRecorder.Recorder;
import design.senior.bluetooth.calculators.AudioCalculator;

/**
 * Used by both phones to manage a socket connection on a separate thread
 */
public class MyBluetoothService {

    private final byte[] genericMessage = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(123l).array();
    private long timeToHearMyOwnChirp = -1;

    private BluetoothModel bluetoothModel;
    private boolean newChirp = false;
    private Recorder recorder;
    private AudioCalculator audioCalculator;

    private static final String TAG = "MY_APP_DEBUG_TAG";
    private Handler mHandler; // handler that gets info from Bluetooth service

    private boolean server = false;

    private ConnectedThread connectedThread;

    public MyBluetoothService(Handler handler, BluetoothSocket socket, boolean server, BluetoothModel bluetoothModel){
        this.mHandler = handler;
        connectedThread = new ConnectedThread(socket);

        this.server = server;
        /**
         * Server will be the device sending the sound frequency to client, so generate tone in advance
         * so that it can be used immediately when needed
         */
        if(server){
            this.bluetoothModel = bluetoothModel;
            recorder = new Recorder(getCallback());
            recorder.start();
            audioCalculator = new AudioCalculator();
            newChirp = false;
        }
        bluetoothModel.setIsRunning(true);

        /**
         * Client and Server both need to manage their socket connection on a separate thread.
         * Client reads, server writes
         */
        connectedThread.start();
    }

    public void setNewChirp(boolean newChirp){
        this.newChirp = newChirp;
    }

    /**
     * Server also listens to see how long it takes them to recognize their own chirp. Technically the difference
     * between the time it takes the client phone to recognize the chirp, and the time it take the emiting source to recognize the chirp
     * should be the delay.
     *
     * @return
     */
    private Callback getCallback(){
        return new Callback() {
            @Override
            public void onBufferAvailable(byte[] buffer) {
                if(newChirp){
                    audioCalculator.setBytes(buffer);
//                int amplitude = audioCalculator.getAmplitude();
//                double decibel = audioCalculator.getDecibel();

                    final double wave = audioCalculator.getFrequency();
                    /**
                     * Accept +- 5hz
                     */
                    boolean condition;
                    if(bluetoothModel.getExactComparison())
                        condition = wave >= (bluetoothModel.getTone()-5) && wave <= (bluetoothModel.getTone() + 5);
                    else
                        condition = wave >= (bluetoothModel.getTone()-5);

                    if(condition){
                        newChirp = false;
                        timeToHearMyOwnChirp = System.currentTimeMillis();
                        /**
                         * Server device hears chirp, and notifies client
                         */
                        connectedThread.sendServerHeard();
                    }
                }
            }
        };
    }

//    public void closeSocket(){
//        connectedThread.cancel();
//    }
//
//    public void write(String text){
//        connectedThread.write();
//    }


    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }


        /**
         * Client and server handle this method differently
         */
        public void run() {
            if(!server){
                mmBuffer = new byte[1024];
                int numBytes; // bytes returned from read()

                /**
                 * Client Keep listening to the InputStream until an exception occurs.
                 * Sends received data to main thread, so that it can display message
                 */
                while (true) {
                    try {
                        // Read from the InputStream.
                        numBytes = mmInStream.read(mmBuffer);
                        // Send the obtained bytes to the UI activity.
                        Message readMsg = mHandler.obtainMessage(
                                MessageConstants.MESSAGE_READ, numBytes, -1,
                                mmBuffer);
                        readMsg.sendToTarget();
                    } catch (IOException e) {
                        Log.d(TAG, "Input stream was disconnected", e);
                        break;
                    }
                }
            }else{
                setRepeatingSoundTask();
            }
        }

        private void setRepeatingSoundTask(){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(bluetoothModel.getIsRunning())
                        write();
                    setRepeatingSoundTask();
                }
            }, bluetoothModel.chirpDelay);
        }

        public void sendServerHeard(){
            try{
                if(timeToHearMyOwnChirp > 0){
                    mmOutStream.write(genericMessage);
                    mmOutStream.flush();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }


        /**
         * The server's method, we could send frequency levels of the chirp over bluetooth, then on the client device, we can
         * estimate our distance by whether or not we hear the broadcasted soundFrequency
         *
         */
        // Call this from the main activity to send data to the remote device.
        public void write() {
            if(!server){
                return;
            }

            try {
                /**
                 * Tell client to listen for sound, then send sound
                 */
                mmOutStream.write(genericMessage);
                mmOutStream.flush();
                bluetoothModel.setChirpWindow(true);
                newChirp = true;
                timeToHearMyOwnChirp = -1;
                new SendAudioAsync().execute(bluetoothModel.getTone(), bluetoothModel.duration);


                /**Timed out handler
                 *
                 * After duration of chirp has completed, set newChirp to false, so the app doesn't continue looking
                 */
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        newChirp = false;
                        bluetoothModel.setChirpWindow(false);

                    }
                }, 400);

            } catch (Exception e) {
                /**
                 * Connection broke for some reason, notify activity
                 */
                Message writeErrorMsg =
                        mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }


}