package design.senior.bluetooth.SoundRecorder;

public interface Callback {
    void onBufferAvailable(byte[] buffer);
}