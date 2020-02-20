package com.example.rtmpplaydemo;

//Rtmp回调
public interface PlayCallback {
    //数据准备回调
    void onPrepared(int width, int height);
    //数据回调
    void onFrameAvailable(byte[] data);
    //播放结束回调
    void onPlayFinished();
}
