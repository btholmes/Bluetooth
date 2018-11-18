package design.senior.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Used by both phones to manage a socket connection on a separate thread
 */
public class MyBluetoothService {
    private static final String TAG = "MY_APP_DEBUG_TAG";
    private Handler mHandler; // handler that gets info from Bluetooth service

    private boolean server = false;

    private ConnectedThread connectedThread;

    public MyBluetoothService(Handler handler, BluetoothSocket socket, boolean server){
        this.mHandler = handler;
        connectedThread = new ConnectedThread(socket);

        this.server = server;
        /**
         * Client and Server both need to manage their socket connection on a separate thread.
         * Client reads, server writes
         */
        connectedThread.start();
    }

    public void closeSocket(){
        connectedThread.cancel();
    }


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
         * This is the clients methods
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
                /**
                 * This is where server writes data to client
                 */
                long time = System.nanoTime();
                String sendMe = time + "";
                byte[] bytes = sendMe.getBytes();
                write(bytes);
            }
        }



        /**
         * The server's method
         * @param bytes
         */
        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            if(!server)
                return;

            try {
                mmOutStream.write(bytes);
                new sendAudioAsync().execute();

                // Share the sent message with the UI activity.


//                Message writtenMsg = mHandler.obtainMessage(
//                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
//                writtenMsg.sendToTarget();
//                mHandler.sendMessage(writtenMsg);
            } catch (IOException e) {
//                Log.e(TAG, "Error occurred when sending data", e);

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

    /**
     * Everything below is using phone speakers
     */


    private final double duration = 0.5; // seconds
    private int freqOfTone = 4000; // Hz

    private long startTime;

    /**
     * Can generate any tone less than sampleRate/2 Hz
     */
    private final int[] sampleRates = new int[]{8000, 11025, 22050, 44100};
    private int sampleRate = 44100;

    double dnumSamples = Math.ceil(duration * sampleRate);
    int numSamples = (int) dnumSamples;
    double sample[] = new double[numSamples];
    byte generatedSnd[] = new byte[2 * numSamples];

    private AudioTrack audioTrack;

    private boolean isPlaying = false;



    public class sendAudioAsync extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            if(!isPlaying){
                isPlaying = true;
                generateTone();
                play();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

        }
    }


    private void generateTone(){
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {    // Fill the sample array
            sample[i] = Math.sin(freqOfTone * 2 * Math.PI * i / (sampleRate));
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalized.
        int idx = 0;
        int i = 0 ;

        int ramp = numSamples / 20 ;                                     // Amplitude ramp as a percent of sample count

        for (i = 0; i< ramp; ++i) {                                      // Ramp amplitude up (to avoid clicks)
            double dVal = sample[i];
            // Ramp up to maximum
            final short val = (short) ((dVal * 32767 * i/ramp));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }


        for (i = i; i< numSamples - ramp; ++i) {                         // Max amplitude for most of the samples
            double dVal = sample[i];
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        for (i = i; i< numSamples; ++i) {                                // Ramp amplitude down
            double dVal = sample[i];
            // Ramp down to zero
            final short val = (short) ((dVal * 32767 * (numSamples-i)/ramp ));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }


    private void play(){
        audioTrack = null;                                    // Get audio track
        try {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, (int)numSamples*2,
                    AudioTrack.MODE_STATIC);
            audioTrack.write(generatedSnd, 0, generatedSnd.length);        // Load the track
            startTime = System.currentTimeMillis();
            isPlaying = true;
            startTime = System.currentTimeMillis();
            audioTrack.play();                                             // Play the track
        }
        catch (Exception e){
            Log.e("Error", "Couldn't create audio track");
            e.printStackTrace();
        }

        int x =0;
        do{                                                              // Monitor playback to find when done
            if (audioTrack != null)
                x = audioTrack.getPlaybackHeadPosition();
            else
                x = numSamples;
        } while (x<numSamples);

        isPlaying = false;
//        broadcastResult();
        if (audioTrack != null) audioTrack.release();
    }


}