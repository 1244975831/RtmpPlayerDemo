package com.example.rtmpplaydemo;

public class RtmpPlayer {

    private static volatile RtmpPlayer mInstance;

    private RtmpPlayer() {
    }

    public static RtmpPlayer getInstance() {
        if (mInstance == null) {
            mInstance = new RtmpPlayer();
        }
        return mInstance;
    }

    public int prepare(String url) {
        // 检查操作等
        return nativePrepare(url);
    }

    public int start() {
        // 检查操作等

        return nativeStart();
    }

    //
    public int setCallback(PlayCallback playCallback) {
        // 检查操作等
        return nativeSetCallback(playCallback);
    }

    public int stop() {
        // 检查操作等
        return nativeStop();
    }

    static {
        System.loadLibrary("native-lib");
    }

    private native int nativePrepare(String url);

    private native int nativeStart();

    private native int nativeSetCallback(PlayCallback playCallback);

    private native int nativeStop();

}
