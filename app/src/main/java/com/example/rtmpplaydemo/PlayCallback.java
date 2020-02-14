package com.example.rtmpplaydemo;

public interface PlayCallback {
    void onPrepared(int width, int height);

    void onFrameAvailable(byte[] data);

    void onPlayFinished();
}
