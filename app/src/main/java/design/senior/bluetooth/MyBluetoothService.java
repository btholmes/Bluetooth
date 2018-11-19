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

    private boolean newChirp = false;
    private Recorder recorder;
    private AudioCalculator audioCalculator;
    private long startTime = 0;
    private int tone = -1;
    private int sequence = 0;
    private boolean run = false;
    private Timer timer;
    private static final String TAG = "MY_APP_DEBUG_TAG";
    private Handler mHandler; // handler that gets info from Bluetooth service

    private boolean server = false;

    private ConnectedThread connectedThread;

    public MyBluetoothService(Handler handler, BluetoothSocket socket, boolean server){
        this.mHandler = handler;
        connectedThread = new ConnectedThread(socket);

        this.server = server;
        /**
         * Server will be the device sending the sound frequency to client, so generate tone in advance
         * so that it can be used immediately when needed
         */
        if(server){
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, (int)numSamples*2,
                    AudioTrack.MODE_STATIC);
            timer = new Timer();
            generateTones(-1);
            setUpAudioTrack();
            recorder = new Recorder(getCallback());
            recorder.start();
            audioCalculator = new AudioCalculator();
            newChirp = false;
        }

        /**
         * Client and Server both need to manage their socket connection on a separate thread.
         * Client reads, server writes
         */
        connectedThread.start();
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
                    if(wave >= 3000){
                        newChirp = false;
                        timeToHearMyOwnChirp = System.currentTimeMillis();
                    }
                }
            }
        };
    }

    public void closeSocket(){
        connectedThread.cancel();
    }

    public void write(String text){
        connectedThread.write();
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
            timer = new Timer();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if(run){
                                /**
                                 * This is where server writes data to client
                                 */
//                                long time = System.currentTimeMillis();
//                                String sendMe = time + "";
//                                byte[] bytes = sendMe.getBytes();
                                write();
                                run = false;
                            }else
                                run = true;
                        }
                    }, 0, 500);
                }
            });
        }

        /**
         * The server's method, we should send decibel levels of the chirp over bluetooth, then on the client device, we can
         * guage our distance by whether or not we hear the broadcasted soundFrequency
         *
         */
        // Call this from the main activity to send data to the remote device.
        public void write() {
            if(!server)
                return;

            try {
                play(mmOutStream);
//                Message writtenMsg = mHandler.obtainMessage(
//                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
//                writtenMsg.sendToTarget();
//                mHandler.sendMessage(writtenMsg);
            } catch (Exception e) {
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


    private final double duration = 0.1; // seconds
//    private final double duration = 1.5; // seconds
    /**
     *  		3950 = B7
     *  		5925 = D
     *  		5967 = F#8
     *  		7040 = A
     */
    private int default_tone = 4100;
    private int b_freq = 3950; // Hz
    private int d_freq = 5925; // Hz
    private int f_freq = 5967; // Hz
    private int a_freq = 7040; // Hz

    /**
     * Can generate any tone less than sampleRate/2 Hz
     */
    private final int[] sampleRates = new int[]{8000, 11025, 22050, 44100};
    private int sampleRate = 44100;

    double dnumSamples = Math.ceil(duration * sampleRate);
    int numSamples = (int) dnumSamples;
    double sample[] = new double[numSamples];
    byte b[] = new byte[2 * numSamples];
    byte d[] = new byte[2 * numSamples];
    byte f[] = new byte[2 * numSamples];
    byte a[] = new byte[2 * numSamples];

    private AudioTrack audioTrack;

    private boolean isPlaying = false;



    public class sendAudioAsync extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
//            generateTone();
//            play();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

        }
    }

    private void generateTones(int val){
        tone = val;
        if(val == -1)
            generateTone(b, -1);
        else{
            generateTone(b, 0);
            generateTone(d, 1);
            generateTone(f, 2);
            generateTone(a, 3);
        }
    }

    private void generateTone(byte[] storeHere, int tone){
        int freqOfTone = 0;
        switch (tone){
            case 0:
                freqOfTone = b_freq;
                break;
            case 1:
                freqOfTone = d_freq;
                break;
            case 2:
                freqOfTone = f_freq;
                break;
            case 3:
                freqOfTone = a_freq;
                break;
            default:
                freqOfTone = default_tone;
                break;

        }
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
            storeHere[idx++] = (byte) (val & 0x00ff);
            storeHere[idx++] = (byte) ((val & 0xff00) >>> 8);
        }


        for (i = i; i< numSamples - ramp; ++i) {                         // Max amplitude for most of the samples
            double dVal = sample[i];
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            storeHere[idx++] = (byte) (val & 0x00ff);
            storeHere[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        for (i = i; i< numSamples; ++i) {                                // Ramp amplitude down
            double dVal = sample[i];
            // Ramp down to zero
            final short val = (short) ((dVal * 32767 * (numSamples-i)/ramp ));
            // in 16 bit wav PCM, first byte is the low order byte
            storeHere[idx++] = (byte) (val & 0x00ff);
            storeHere[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }

    /**
     * 0 = b
     * 1 = d
     * 2 = f
     * 3 = a
     */
    private void setUpAudioTrack(){
        try{
            switch (sequence%4){
                case 0:
                    audioTrack.write(b, 0, b.length);
                    break;
                case 1:
                    audioTrack.write(d, 0, d.length);
                    break;
                case 2:
                    audioTrack.write(f, 0, f.length);
                    break;
                case 3:
                    audioTrack.write(a, 0, a.length);
                    break;
                default:
                    break;
            }

            if(tone != -1)
                sequence++;
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private long timeToHearMyOwnChirp = -1;
    /**
     * Send bluetooth signal, then sound signal, the bluetooth signal
     * @param outputStream
     */
    private void play(final OutputStream outputStream){
        byte[] time = null;
        try {
            audioTrack.stop();
            if(tone != -1)
                setUpAudioTrack();
            else
                audioTrack.reloadStaticData();
//            byte[] time = Longs.toByteArray(System.currentTimeMillis());
            newChirp = true;
            timeToHearMyOwnChirp = -1;
            time = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(System.currentTimeMillis()).array();
            outputStream.write(time);
            outputStream.flush();
            audioTrack.setPlaybackHeadPosition(0);
            audioTrack.play();

            /**
             * Wait for sound to finish
             */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try{
                        if(timeToHearMyOwnChirp > 0){
                            byte[] currentTime = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(timeToHearMyOwnChirp).array();
                            outputStream.write(currentTime);
                            outputStream.flush();
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }, 250);

        }
        catch (Exception e){
            Log.e("Error", "Couldn't create audio track");
            e.printStackTrace();
        }

//        audioTrack.setPlaybackHeadPosition(0);
//        if (audioTrack != null) audioTrack.release();
        audioTrack.flush();
    }


}