package com.example.rtmpplaydemo;

public class RtmpPlayer {

    private static volatile RtmpPlayer mInstance;

    private RtmpPlayer() {
    }

    public static RtmpPlayer getInstance() {
        if (mInstance == null) {
            synchronized (RtmpPlayer.class) {
                if (mInstance == null) {
                    mInstance = new RtmpPlayer();
                }
            }
        }
        return mInstance;
    }

    public int prepare(String url) {
        // 检查操作等
        return nativePrepare(url);
    }


    static {
        System.loadLibrary("rtmpplayer-lib");
    }

    private native int nativePrepare(String url);

    public native void nativeStart();

    public native void nativeSetCallback(PlayCallback playCallback);

    public native void nativeStop();

}
