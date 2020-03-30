package com.example.rtmpplaydemo;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.arcsoft.face.AgeInfo;
import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.GenderInfo;
import com.arcsoft.face.LivenessInfo;
import com.arcsoft.face.enums.DetectFaceOrientPriority;
import com.arcsoft.face.enums.DetectMode;
import com.example.rtmpplaydemo.draw.DrawHelper;
import com.example.rtmpplaydemo.draw.DrawInfo;
import com.example.rtmpplaydemo.draw.FaceRectView;
import com.example.rtmpplaydemo.glsurface.GLUtil;
import com.example.rtmpplaydemo.glsurface.RtmpGLSurfaceView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int ACTION_REQUEST_PERMISSIONS = 0x001;
    private RtmpGLSurfaceView surfaceView;
    private FaceEngine faceEngine;
    private int frameWidth;
    private int frameHeight;
    private DrawHelper drawHelper;
    private FaceRectView faceRectView;
    private FrameLayout mainLayout;

    private static final String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
    };

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

        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
            return;
        }
        int code = FaceEngine.active(this, Constants.APP_ID, Constants.SDK_KEY);
        if (code != ErrorInfo.MOK && code != ErrorInfo.MERR_ASF_ALREADY_ACTIVATED) {
            Toast.makeText(this, "Error Code :" + code, Toast.LENGTH_LONG).show();
        }
        faceEngineInit();
        faceRectView = findViewById(R.id.face_rect_view);
        mainLayout = findViewById(R.id.fl_main_layout);
        //设置着色器

        Thread thread = new Thread() {
            @Override
            public void run() {
                super.run();
//                rtmp://58.200.131.2:1935/livetv/hunantv
//                rtmp://202.69.69.180:443/webcast/bshdlive-pc
                //为RtmpPlayer 设置回调
                RtmpPlayer.getInstance().nativeSetCallback(new PlayCallback() {
                    @Override
                    public void onPrepared(int width, int height) {
                        //获得数据流的宽高
                        frameWidth = width;
                        frameHeight = height;
                        addSurfaceView(width, height);
                        /**
                         * DrawHelper初始化
                         *
                         * @param previewWidth             预览宽度
                         * @param previewHeight            预览高度
                         * @param canvasWidth              绘制控件的宽度
                         * @param canvasHeight             绘制控件的高度
                         * @param cameraDisplayOrientation 旋转角度
                         * @param cameraId                 相机ID
                         * @param isMirror                 是否水平镜像显示（若相机是镜像显示的，设为true，用于纠正）
                         * @param mirrorHorizontal         为兼容部分设备使用，水平再次镜像
                         * @param mirrorVertical           为兼容部分设备使用，垂直再次镜像
                         */
                        drawHelper = new DrawHelper(width, height, faceRectView.getWidth(), faceRectView.getHeight(), 0, 0, false, false, false);
                        //start循环调运会阻塞主线程 需要在子线程里运行
                        RtmpPlayer.getInstance().nativeStart();
                    }

                    @Override
                    public void onFrameAvailable(byte[] data) {
                        //获得裸数据，裸数据的格式为NV21
                        Log.i(TAG, "onFrameAvailable: " + Arrays.hashCode(data));
                        surfaceView.refreshFrameNV21(data);
                        List<DrawInfo> drawInfoList = new ArrayList<>();
                        List<FaceInfo> faceInfos = new ArrayList<>();
                        //人脸识别
                        int code = faceEngine.detectFaces(data, frameWidth, frameHeight, FaceEngine.CP_PAF_NV21, faceInfos);
                        if (code != ErrorInfo.MOK) {
                            //引擎调用有问题则打log出来
                            Log.i(TAG, "onFrameAvailable:  detect Error");
                            return;
                        }
                        //得到人脸数据后 将需要绘制的信息记录在drawInfoList内
                        for (int i = 0; i < faceInfos.size(); i++) {
                            drawInfoList.add(new DrawInfo(drawHelper.adjustRect(faceInfos.get(i).getRect()),
                                    GenderInfo.UNKNOWN, AgeInfo.UNKNOWN_AGE, LivenessInfo.UNKNOWN, Color.YELLOW,
                                    String.valueOf(faceInfos.get(i).getFaceId())));
                        }
                        drawHelper.draw(faceRectView, drawInfoList);
                    }

                    @Override
                    public void onPlayFinished() {
                        //播放结束的回调
                    }
                });
                //数据准备
                int code = RtmpPlayer.getInstance().prepare("rtmp://58.200.131.2:1935/livetv/hunantv");
                if (code == -1) {
                    //code为-1则证明Rtmp的prepare有问题
                    Toast.makeText(MainActivity.this, "prepare Error", Toast.LENGTH_LONG).show();
                }
            }
        };
        thread.start();
    }

    /**
     * 初始化SurfaceView
     *
     * @param width  数据帧的宽
     * @param height 数据帧的高
     */
    private void addSurfaceView(final int width, final int height) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                surfaceView = new RtmpGLSurfaceView(MainActivity.this);
                surfaceView.setFragmentShaderCode(GLUtil.FRAG_SHADER_NORMAL);
                //GLSurfaceView 初始化
                surfaceView.init(false, 0, width, height);
                mainLayout.addView(surfaceView);
            }
        });
    }

    /**
     * 虹软引擎初始化
     */
    private void faceEngineInit() {
        faceEngine = new FaceEngine();
        int code = faceEngine.init(this, DetectMode.ASF_DETECT_MODE_VIDEO, DetectFaceOrientPriority.ASF_OP_ALL_OUT,
                16, 5, FaceEngine.ASF_FACE_DETECT);
        if (code != ErrorInfo.MOK) {
            Toast.makeText(MainActivity.this, "faceEngineInit Error", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 权限检查、申请需要的权限
     *
     * @param neededPermissions 需要动态申请的权限
     * @return 是否所有申请的权限都同意了
     */
    protected boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(this, neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RtmpPlayer.getInstance().nativeStop();
        if (faceEngine != null) {
            faceEngine.unInit();
        }
    }
}
