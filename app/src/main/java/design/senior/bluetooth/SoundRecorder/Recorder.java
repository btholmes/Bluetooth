package design.senior.bluetooth.SoundRecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

/**
 * This is a class I found online
 * originally from http://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html
 * and modified by Steve Pomeroy <steve@staticfree.info>
 */
public class Recorder {

    private int audioSource = MediaRecorder.AudioSource.DEFAULT;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private int sampleRate = 44100;
    private Thread thread;
    private Callback callback;


    private final double duration = 1; // seconds
    private double freqOfTone = 1000; // hz
    private final int[] sampleRates = new int[]{8000, 11025, 22050, 44100};
//    private final int[] sampleRates = new int[]{8000};


    /**
     * Rate per second
     */
    private final int maxFrequency = 20000;

    double dnumSamples = Math.ceil(duration * sampleRate);
    int numSamples = (int) dnumSamples;
    double sample[] = new double[numSamples];
    byte generatedSnd[] = new byte[2 * numSamples];

    public Recorder() {
    }

    public Recorder(Callback callback) {
        this.callback = callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        if (thread != null) return;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

                int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding);
                AudioRecord recorder = new AudioRecord(audioSource, sampleRate, channelConfig, audioEncoding, minBufferSize);
//                recorder = findAudioRecord();

                if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
                    Thread.currentThread().interrupt();
                    return;
                } else {
                    Log.i(Recorder.class.getSimpleName(), "Started.");
                    //callback.onStart();
                }
                byte[] buffer = new byte[minBufferSize];
                recorder.startRecording();

                while (thread != null && !thread.isInterrupted() && recorder.read(buffer, 0, minBufferSize) > 0) {
                    callback.onBufferAvailable(buffer);
                }
                recorder.stop();
                recorder.release();
            }
        }, Recorder.class.getName());
        thread.start();
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void setSampleInfo(){
        dnumSamples = Math.ceil(duration * sampleRate);
        numSamples = (int) dnumSamples;
        sample = new double[numSamples];
        generatedSnd = new byte[2 * numSamples];
    }

    private AudioRecord findAudioRecord() {
        for (int rate : sampleRates) {
            for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
                for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
                    try {
//                        Log.d(C.TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
//                                + channelConfig);
                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            // check if we can instantiate and have a success
                            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, channelConfig, audioFormat, bufferSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED){
                                sampleRate = rate;
                                setSampleInfo();
                                return recorder;
                            }
                        }
                    } catch (Exception e) {
                        Log.e("Error", rate + "Exception, keep trying.",e);
                    }
                }
            }
        }
        return null;
    }
}
