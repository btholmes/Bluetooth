package design.senior.bluetooth;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Everything below is using phone speakers
 */

//            byte[] time = Longs.toByteArray(System.currentTimeMillis());

//            time = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(System.currentTimeMillis()).array();

//    private final double duration = 0.05; // seconds
//    private final double duration = 1.5; // seconds
/**
 *  		3950 = B7
 *  		5925 = D
 *  		5967 = F#8
 *  		7040 = A
 */
//    private int default_tone = 4100;
//    private int default_tone = 16000;
//    private int b_freq = 3950; // Hz
//    private int d_freq = 5925; // Hz
//    private int f_freq = 5967; // Hz
//    private int a_freq = 7040; // Hz

/**
 * Can generate any tone less than sampleRate/2 Hz
 */
public class SendAudioAsync extends AsyncTask<Double, Void, Void> {

private final int[] sampleRates = new int[]{8000, 11025, 22050, 44100};
private int sampleRate = 44100;

private double current_tone = 8100;
private double duration = 1.0; //seconds

private int b_freq = 3950; // Hz
private int d_freq = 5925; // Hz
private int f_freq = 5967; // Hz
private int a_freq = 7040; // Hz

private long startTime = 0;
private int tone = -1;
private int sequence = 0;

private double dnumSamples;
private int numSamples;
private double[] sample;
private byte[] b;
private byte[] d;
private byte[] f;
byte[] a;

private AudioTrack audioTrack;

    @Override
    protected Void doInBackground(Double... toneAndDuration) {
        current_tone = toneAndDuration[0];
        duration  = toneAndDuration[1];
        init();
        generateTones(-1);
        writeToAudioTrack();
        play();
        audioTrack.flush();
        //        audioTrack.setPlaybackHeadPosition(0);
//        if (audioTrack != null) audioTrack.release();
        return null;
    }

    /**
     * Handle changes in duration here
     */
    private void init(){
        dnumSamples = Math.ceil(duration * sampleRate);
        numSamples = (int) dnumSamples;
        sample = new double[numSamples];
        b = new byte[2 * numSamples];
        d = new byte[2 * numSamples];
        f = new byte[2 * numSamples];
        a = new byte[2 * numSamples];

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, (int)numSamples*2,
                AudioTrack.MODE_STATIC);
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


    private double getFreq(){
        double freqOfTone = 0;
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
                freqOfTone = current_tone;
                break;

        }
        return freqOfTone;
    }

    private void generateTone(byte[] storeHere, int tone){
        double freqOfTone = getFreq();

        // fill out the array
        for (int i = 0; i < numSamples; ++i) {    // Fill the sample array
//            sample[i] = Math.sin(freqOfTone * 2 * Math.PI * i / (sampleRate));
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
    private void writeToAudioTrack(){
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

    /**
     * Send bluetooth signal, then sound signal, the bluetooth signal
     */
    private void play(){
        try {
            audioTrack.stop();
            audioTrack.play();
        }
        catch (Exception e){
            Log.e("Error", "Couldn't create audio track");
            e.printStackTrace();
        }
    }


    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);

    }
}

