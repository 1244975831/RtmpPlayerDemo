package com.example.rtmpplaydemo;

import android.util.Log;

public class RtmpPlayer {

    private static volatile RtmpPlayer mInstance;
    private static final int PREPARE_ERROR = -1;

    private RtmpPlayer() {
    }

    //双重锁定防止多线程操作导致的创建多个实例
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

    //数据准备操作
    public int prepare(String url) {
        if(nativePrepare(url) == PREPARE_ERROR){
            Log.i("rtmpPlayer", "PREPARE_ERROR ");
        }
        return nativePrepare(url);
    }


    //加载库
    static {
        System.loadLibrary("rtmpplayer-lib");
    }

    //数据准备
    private native int nativePrepare(String url);
    //开始播放
    public native void nativeStart();
    //设置回调
    public native void nativeSetCallback(PlayCallback playCallback);
    //停止播放
    public native void nativeStop();

}
