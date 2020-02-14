package com.example.rtmpplaydemo;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.example.rtmpplaydemo.glsurface.GLUtil;
import com.example.rtmpplaydemo.glsurface.RoundCameraGLSurfaceView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private Thread thread;
    private static final String TAG = "MainActivity";
    private RoundCameraGLSurfaceView surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //沉浸式状态栏
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.sv_video);
        //设置着色器
        surfaceView.setFragmentShaderCode(GLUtil.FRAG_SHADER_NORMAL);
        //设置SurfaceHolder回调
        surfaceView.getHolder().addCallback(this);
        //GLSurfaceView 初始化
        surfaceView.init(false, 0, 1024, 576);
        thread = new Thread() {
            @Override
            public void run() {
                super.run();
//                rtmp://58.200.131.2:1935/livetv/hunantv
//                rtmp://202.69.69.180:443/webcast/bshdlive-pc
                RtmpPlayer.getInstance().setCallback(new PlayCallback() {
                    @Override
                    public void onPrepared(int width, int height) {
                        RtmpPlayer.getInstance().start();
                    }

                    @Override
                    public void onFrameAvailable(byte[] data) {
                        Log.i(TAG, "onFrameAvailable: " + Arrays.hashCode(data));
                        surfaceView.refreshFrameNV21(data);
                    }

                    @Override
                    public void onPlayFinished() {

                    }
                });
                RtmpPlayer.getInstance().prepare("rtmp://58.200.131.2:1935/livetv/hunantv");
            }
        };
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceCreated: start ");
        thread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
