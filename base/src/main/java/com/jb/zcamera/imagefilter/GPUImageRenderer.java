/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jb.zcamera.imagefilter;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Location;
import android.media.CamcorderProfile;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView.Renderer;
import android.preference.PreferenceManager;
import android.util.Log;

import com.gomo.minivideo.CameraApp;
import com.google.android.instantapps.InstantApps;
import com.jb.zcamera.av.AVRecorder;
import com.jb.zcamera.av.RenderAdapter;
import com.jb.zcamera.av.SessionConfig;
import com.jb.zcamera.camera.CameraController;
import com.jb.zcamera.camera.Preview;
import com.jb.zcamera.imagefilter.filter.GPUImageFilter;
import com.jb.zcamera.imagefilter.filter.GPUImageFilterGroup;
import com.jb.zcamera.imagefilter.filter.GPUImageHDROESFilter;
import com.jb.zcamera.imagefilter.filter.GPUImageOESFilter;
import com.jb.zcamera.imagefilter.util.ImageFilterTools;
import com.jb.zcamera.imagefilter.util.NativeLibrary;
import com.jb.zcamera.imagefilter.util.OpenGlUtils;
import com.jb.zcamera.imagefilter.util.Rotation;
import com.jb.zcamera.imagefilter.util.TextureRotationUtil;
import com.jb.zcamera.utils.PhoneInfo;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static com.jb.zcamera.camera.SettingsManager.PRE_KEY_MAX_TEXTURE_SIZE;
import static com.jb.zcamera.imagefilter.util.OpenGlUtils.NO_TEXTURE;
import static com.jb.zcamera.imagefilter.util.TextureRotationUtil.TEXTURE_NO_ROTATION;
import static javax.microedition.khronos.opengles.GL10.GL_RGBA;
import static javax.microedition.khronos.opengles.GL10.GL_UNSIGNED_BYTE;

@SuppressLint("WrongCall")
@TargetApi(11)
public class GPUImageRenderer implements Renderer, Camera.PreviewCallback {
    private static final String TAG = "GPUImageRenderer";

    public static final int NO_IMAGE = -1;
    public static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    public static final float CUBE_90[] = {
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, 1.0f,
            1.0f, -1.0f
    };

    private GPUImageFilter mFilter;

    public final Object mSurfaceChangedWaiter = new Object();

//    private int mPboIndex = 0;
//    private int mPboNewIndex = 0;
//    private IntBuffer mPboIds;
//    private int mPboSize;
//
//    private final int mPixelStride = 4;//RGBA 4字节
//    private int mRowStride;//对齐4字节

    private int mGLTextureId = NO_IMAGE;
    private int mSurfaceTextureID = -1;
    private SurfaceTexture mSurfaceTexture = null;
    private final FloatBuffer mGLCubeBuffer;
    private final FloatBuffer mGLRotatedCubeBuffer;
    private final FloatBuffer mGLTextureBuffer;
    private IntBuffer mGLRgbBuffer;

    private ByteBuffer mPreviewBuffer;

    private int mOutputWidth;
    private int mOutputHeight;
    private int mImageWidth;
    private int mImageHeight;
    private int mAddedPadding;

    private final Queue<Runnable> mRunOnDraw;
    private final Queue<Runnable> mRunOnDrawEnd;
    private Rotation mRotation;
    private boolean mFlipHorizontal;
    private boolean mFlipVertical;
    private GPUImage.ScaleType mScaleType = GPUImage.ScaleType.CENTER_CROP;
    
    private FiltFrameListener mListener;

    private IRenderCallback mFrameCallback;

    private boolean mSizeChanging = false;
    private boolean mRotationChanging = false;
    private boolean mFilterChanging = false;
    private Object mChangeingLock = new Object();

    private boolean mIsCamera = false;

    private AVRecorder mRecorder;
    private int mRecordingRotation;
    private final FloatBuffer mVideoTextureBuffer;
    private Runnable mVideoStartRunnable;
    private boolean mIsRecording;

    public GPUImageRenderer(final GPUImageFilter filter, IRenderCallback frameCallback, boolean isCamera) {
        mFilter = filter;
        mFrameCallback = frameCallback;
        mIsCamera = isCamera;
        mRunOnDraw = new LinkedList<Runnable>();
        mRunOnDrawEnd = new LinkedList<Runnable>();

        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);

        mGLRotatedCubeBuffer = ByteBuffer.allocateDirect(CUBE_90.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLRotatedCubeBuffer.put(CUBE_90).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVideoTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        setRotation(Rotation.NORMAL, false, false);
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
//    	int color = Color.parseColor("#262827");
//    	float red = Color.red(color)*1.0f/255;
//    	float g = Color.green(color)*1.0f/255;
//    	float b = Color.blue(color)*1.0f/255;

        mSurfaceTextureID = createTextureID();
        Log.e("Check", "mSurfaceTextureID = " + mSurfaceTextureID);
        mSurfaceTexture = new SurfaceTexture(mSurfaceTextureID);
        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                if (isOESFilter()) {
                    mFilterChanging = false;
                    if (isRecording() && mRecorder != null) {
                        mRecorder.onFrameAvailable(null);
                    } else {
                        if(mFrameCallback != null) {
                            mFrameCallback.onFrameAvaliable(surfaceTexture.getTimestamp());
                        }
                    }
                }
            }
        });
        GLES20.glClearColor(0f, 0f, 0f, 1); //此背景与编辑界面背景色一致
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        mFilter.init();
        if(mFrameCallback != null) {
            mFrameCallback.onSurfaceTextureCreated(mSurfaceTexture);
        }
        initMaxTextureSize();
    }

    private int mTestTextureId = NO_IMAGE;

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        mOutputWidth = width;
        mOutputHeight = height;

//        if (InstantApps.isInstantApp(CameraApp.getApplication())) {
//            final int align = 128;//128字节对齐
//            mRowStride = (width * mPixelStride + (align - 1)) & ~(align - 1);
//
//            mPboSize = mRowStride * height;
//            mPboIds = IntBuffer.allocate(2);
//            GLES30.glGenBuffers(2, mPboIds);
//            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds.get(0));
//            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, mPboSize, null, GLES30.GL_STATIC_READ);
//            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds.get(1));
//            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, mPboSize, null, GLES30.GL_STATIC_READ);
//            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
//        }

        mPreviewBuffer = ByteBuffer.allocateDirect(mOutputWidth * mOutputHeight * 4).order(ByteOrder.nativeOrder());
        mPreviewBuffer.position(0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(mFilter.getProgram());
        mFilter.onOutputSizeChanged(width, height);
        adjustImageScaling();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.RED);
        canvas.drawCircle(400, 400, 100, new Paint(Paint.ANTI_ALIAS_FLAG));
        mTestTextureId = OpenGlUtils.loadTexture(bitmap, mTestTextureId);
        Log.e("Check", "mTestTextureId = " + mTestTextureId);

        synchronized (mChangeingLock) {
            mSizeChanging = mIsCamera;
        }
        synchronized (mSurfaceChangedWaiter) {
            mSurfaceChangedWaiter.notifyAll();
        }
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        runAll(mRunOnDraw);
        synchronized (mChangeingLock) {
            if (mRotationChanging) {
                mRotationChanging = false;
                try {
                    if (mSurfaceTexture != null) {
                        mSurfaceTexture.updateTexImage();
                    }
                } catch (Throwable tr) {
                    Log.e(TAG, "", tr);
                }
                return;
            }
            if (mSizeChanging) {
                mSizeChanging = false;
                try {
                    if (mSurfaceTexture != null) {
                        mSurfaceTexture.updateTexImage();
                    }
                } catch (Throwable tr) {
                    Log.e(TAG, "", tr);
                }
                return;
            }
        }
        if (!mFilterChanging) {
            if (isOESFilter()) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                mFilter.onDraw(mSurfaceTextureID, mGLCubeBuffer, mGLTextureBuffer);
            } else {
                mFilter.onDraw(mSurfaceTextureID, mGLCubeBuffer, mGLTextureBuffer);
            }
            if (isRecording() && mRecorder != null) {
                if (InstantApps.isInstantApp(CameraApp.getApplication())) {

//                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds.get(mPboIndex));
//                    GLES30.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,0);
//                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds.get(mPboNewIndex));
//                    mPreviewBuffer = (ByteBuffer) GLES30.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER,
//                            0, mOutputWidth * mOutputHeight * 4, GLES30.GL_MAP_READ_BIT);
//                    GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
//                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
//                    mPboIndex = (mPboIndex + 1) % 2;
//                    mPboNewIndex = (mPboNewIndex + 1) % 2;
                    GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GL_RGBA, GL_UNSIGNED_BYTE, mPreviewBuffer);
                    mPreviewBuffer.position(0);
                }
                mRecorder.onDrawFrame();
            }
        }

        runAll(mRunOnDrawEnd);
        try {
            if (mSurfaceTexture != null) {
                mSurfaceTexture.updateTexImage();
            }
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
        }

        if (mListener != null && mListener.needCallback()) {
            mListener.onFiltFrameDraw(getFiltFrame(gl, mOutputWidth, mOutputHeight));
        }
    }

    public void onSurfaceDestroy() {
        mFilter.destroy();
        if (mSurfaceTextureID != NO_IMAGE) {
            GLES20.glDeleteTextures(1, new int[]{
                    mSurfaceTextureID
            }, 0);
            mSurfaceTextureID = NO_IMAGE;
        }
        if (mGLTextureId != NO_IMAGE) {
            GLES20.glDeleteTextures(1, new int[]{
                    mGLTextureId
            }, 0);
            mGLTextureId = NO_IMAGE;
        }
    }

    public static Bitmap getFiltFrame(GL10 gl, int width, int height) {
        int[] iat = new int[width * height];
        IntBuffer ib = IntBuffer.allocate(width * height);
        GLES20.glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, ib);
        int[] ia = ib.array();

        // Convert upside down mirror-reversed image to right-side up normal
        // image.
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                iat[(height - i - 1) * width + j] = ia[i * width + j];
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(iat));
        return bitmap;
    }

    private void runAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
//        if (isOESFilter()) {
//            return;
//        }
    }

    public void setUpSurfaceTexture(final Preview preview) {
//        runOnDraw(new Runnable() {
//            @Override
//            public void run() {
                synchronized (preview) {
//                    int[] textures = new int[1];
//                    GLES20.glGenTextures(1, textures, 0);
//                    mSurfaceTexture = new SurfaceTexture(textures[0]);
                    try {
                        if (mGLTextureId != -1) {
                            deleteImage();
                        }
//                        mGLTextureId = -1;
                        CameraController.CameraHolder camera = preview.getCamera();
                        camera.setPreviewTexture(mSurfaceTexture);
                        camera.setPreviewCallback(GPUImageRenderer.this);
                        camera.startPreview();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    runOnDrawEnd(new Runnable() {
                        @Override
                        public void run() {
                            preview.startOverlayGone();
                        }
                    });
                }
//            }
//        });
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public void setFilter(final GPUImageFilter filter, final GPUImageFilter baseFilter) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                mFilterChanging = mIsCamera;
                final GPUImageFilter oldFilter = mFilter;
                mFilter = filter;
                if (oldFilter != null && oldFilter != baseFilter) {
                    if (oldFilter instanceof GPUImageFilterGroup) {
                        ((GPUImageFilterGroup) oldFilter).removeFilter(baseFilter);
                    }
                    oldFilter.destroy();
                }
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
            }
        });
    }

    public void deleteImage() {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glDeleteTextures(1, new int[]{
                        mGLTextureId
                }, 0);
                mGLTextureId = NO_IMAGE;
            }
        });
    }

    public void setImageBitmap(final Bitmap bitmap) {
        setImageBitmap(bitmap, true);
    }

    public void setImageBitmap(final Bitmap bitmap, final boolean recycle) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        runOnDraw(new Runnable() {

            @Override
            public void run() {
                if (bitmap == null || bitmap.isRecycled()) {
                    return;
                }

                try {
                    Bitmap resizedBitmap = null;
                    if (bitmap.getWidth() % 2 == 1) {
                        resizedBitmap = Bitmap.createBitmap(bitmap.getWidth() + 1, bitmap.getHeight(),
                                Bitmap.Config.ARGB_8888);
                        Canvas can = new Canvas(resizedBitmap);
                        can.drawARGB(0x00, 0x00, 0x00, 0x00);
                        can.drawBitmap(bitmap, 0, 0, null);
                        mAddedPadding = 1;
                    } else {
                        mAddedPadding = 0;
                    }

                    mGLTextureId = OpenGlUtils.loadTexture(
                            resizedBitmap != null ? resizedBitmap : bitmap, mGLTextureId, recycle);
                    if (resizedBitmap != null) {
                        resizedBitmap.recycle();
                    }
                    mImageWidth = bitmap.getWidth();
                    mImageHeight = bitmap.getHeight();
                    adjustImageScaling();
                } catch (Exception e) {

                }
            }
        });
    }

    public void setScaleType(GPUImage.ScaleType scaleType) {
        mScaleType = scaleType;
    }

    protected int getFrameWidth() {
        return mOutputWidth;
    }

    protected int getFrameHeight() {
        return mOutputHeight;
    }

    private void adjustImageScaling() {
        float outputWidth = mOutputWidth;
        float outputHeight = mOutputHeight;
        if (mRotation == Rotation.ROTATION_270 || mRotation == Rotation.ROTATION_90) {
            outputWidth = mOutputHeight;
            outputHeight = mOutputWidth;
        }

        float ratio1 = outputWidth / mImageWidth;
        float ratio2 = outputHeight / mImageHeight;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(mImageWidth * ratioMax);
        int imageHeightNew = Math.round(mImageHeight * ratioMax);

        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;

        float[] cube = CUBE;
        float[] cube90 = CUBE_90;
        float[] textureCords = TextureRotationUtil.getRotation(mRotation, mFlipHorizontal, mFlipVertical);
        if (mScaleType == GPUImage.ScaleType.CENTER_CROP) {
            float distHorizontal = 0;
            float distVertical = 0;
//            if (ratioWidth != 0 && ratioHeight != 0) {
//                distHorizontal = (1 - 1 / ratioWidth) / 2;
//                distVertical = (1 - 1 / ratioHeight) / 2;
//            }

            textureCords = new float[]{
                    addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                    addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                    addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                    addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
            };
        } else {
            cube = new float[]{
                    CUBE[0] / ratioHeight, CUBE[1] / ratioWidth,
                    CUBE[2] / ratioHeight, CUBE[3] / ratioWidth,
                    CUBE[4] / ratioHeight, CUBE[5] / ratioWidth,
                    CUBE[6] / ratioHeight, CUBE[7] / ratioWidth,
            };
        }

        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(cube).position(0);
        mGLRotatedCubeBuffer.clear();
        mGLRotatedCubeBuffer.put(cube90).position(0);
        mGLTextureBuffer.clear();
        mGLTextureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    public void setRotationCamera(final Rotation rotation, final boolean flipHorizontal,
                                  final boolean flipVertical) {
        synchronized (mChangeingLock) {
            mRotationChanging = true;
            setRotation(rotation, flipVertical, flipHorizontal);
        }
    }

    public void setRotation(final Rotation rotation) {
        mRotation = rotation;
        adjustImageScaling();
    }

    public void setRotation(final Rotation rotation,
                            final boolean flipHorizontal, final boolean flipVertical) {
        mFlipHorizontal = flipHorizontal;
        mFlipVertical = flipVertical;
        setRotation(rotation);
    }

    public Rotation getRotation() {
        return mRotation;
    }

    public boolean isFlippedHorizontally() {
        return mFlipHorizontal;
    }

    public boolean isFlippedVertically() {
        return mFlipVertical;
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.add(runnable);
        }
    }

    protected void runOnDrawEnd(final Runnable runnable) {
        synchronized (mRunOnDrawEnd) {
            mRunOnDrawEnd.add(runnable);
        }
    }

    public void setFiltFrameListener(FiltFrameListener listener) {
        this.mListener = listener;
    }

    private int createTextureID()
    {
        int[] texture = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        if (!PhoneInfo.isNotSupportOES() && !PhoneInfo.isNotSupportVideoRender()) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        }

        return texture[0];
    }

    public boolean isOESFilter() {
        if (mFilter == null) {
            return false;
        }

        if (mFilter.getClass() == GPUImageOESFilter.class
                || mFilter.getClass() == GPUImageHDROESFilter.class) {
            return true;
        }

        if (mFilter instanceof GPUImageFilterGroup) {
            return ((GPUImageFilterGroup)mFilter).getMergedFilters().get(0).getClass()
                    == GPUImageOESFilter.class;
        }

        return false;
    }

    public void startRecording(final GPUImageFilter videoFilter, final boolean isBadTwoInputFilter, final int rotation, final File outputFile,
                               final CamcorderProfile profile, final Location location, final boolean recordAudio) {
        synchronized (mRunOnDraw) {
            mVideoStartRunnable = new Runnable() {
                @Override
                public void run() {
                    ImageFilterTools.rotateFilter(videoFilter, mRotation, isBadTwoInputFilter);

                    final int width = profile.videoFrameWidth;
                    final int height = profile.videoFrameHeight;
//                if (mRotation == Rotation.ROTATION_90 || mRotation == Rotation.ROTATION_270) {
//                    width = mOutputHeight;
//                    height = mOutputWidth;
//                }

                    Log.d("Renderer", "mRotation=" + mRotation.asInt() + " uiRotation=" + rotation);
                    Log.d("Renderer", "profile width=" + width + " height=" + height);
                    Log.d("Renderer", "Output width=" + mOutputWidth + " height=" + mOutputHeight);
                    final float[] textureCords = TextureRotationUtil.getRotation(Rotation.NORMAL, mFlipHorizontal, mFlipVertical);
                    mVideoTextureBuffer.clear();
                    mVideoTextureBuffer.put(textureCords).position(0);

                    SessionConfig.Builder builder = new SessionConfig.Builder(outputFile.getAbsolutePath(), recordAudio)
                            .withVideoResolution(width, height)
                            .withVideoDegrees((mRotation.asInt() - rotation + 360) % 360)
                            .withVideoBitrate(profile.videoBitRate)
                            .withAudioBitrate(profile.audioBitRate)
//                        .withAudioChannels(profile.audioChannels)
                            .withAudioSamplerate(profile.audioSampleRate);
                    if (location != null) {
                        builder.withLocation((float)location.getLatitude(), (float)location.getLongitude());
                    }
                    SessionConfig config = builder.build();
                    try {
                        mRecorder = new AVRecorder(config, new RenderAdapter() {

                            private boolean mIsInited;

                            public int mTextureId = NO_TEXTURE;

                            @Override
                            public void requestRender() {
                                if(mFrameCallback != null) {
                                    mFrameCallback.onFrameAvaliable(getSurfaceTexture().getTimestamp());
                                }
                            }

                            @Override
                            public void drawFrame(boolean eosRequested) {
                                if (!eosRequested) {
                                    if (!mIsInited) {
                                        mIsInited = true;

                                        videoFilter.init();
                                        videoFilter.onOutputSizeChanged(width, height);
                                    }
                                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                                    if (videoFilter instanceof GPUImageFilterGroup &&
                                            ((GPUImageFilterGroup)videoFilter).getMergedFilters().get(0) instanceof GPUImageOESFilter) {
                                        videoFilter.onDraw(mSurfaceTextureID, mGLCubeBuffer, mVideoTextureBuffer);
                                    } else {
                                        mTextureId = OpenGlUtils.loadTexture(mPreviewBuffer, mOutputWidth, mOutputHeight, mTextureId);
                                        mPreviewBuffer.position(0);
                                        videoFilter.onDraw(mTextureId, mGLRotatedCubeBuffer, mVideoTextureBuffer);
                                    }
                                }
                            }

                            @Override
                            public void realse() {
                                videoFilter.destroy();
                            }

                            @Override
                            public SurfaceTexture getSurfaceTexture() {
                                return mSurfaceTexture;
                            }
                        });
                        mRecorder.saveEGLState();
                        mRecorder.startRecording();
                    } catch (Exception e) {
                        Log.e(TAG, "mVideoStartRunnable error!");
                        e.printStackTrace();
                    }
                }
            };
            runOnDraw(mVideoStartRunnable);
            mIsRecording = true;
        }

    }

    public void stopRecording() {
        synchronized (mRunOnDraw) {
            runOnDraw(new Runnable() {
                @Override
                public void run() {
                    mRunOnDraw.remove(mVideoStartRunnable);
                    mVideoStartRunnable = null;
                    if (mRecorder != null) {
                        mRecorder.stopRecording();
                        mRecorder.onFrameAvailable(null);
                        mRecorder.release();
                        mRecorder = null;
                    }
                    mIsRecording = false;
                }
            });
        }
    }

    public boolean isRecording() {
        synchronized (mRunOnDraw) {
            return mIsRecording;
        }
    }

    private void initMaxTextureSize() {
        int size = getMaxTextureSize();
        if (size == 0) {
            try {
                IntBuffer ib = IntBuffer.allocate(1);
                GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, ib);
                ib.position(0);
                size = ib.get();
            } catch (Throwable tr) {

            }
            if (size >= 2048) {
                SharedPreferences sharedPreferences = getSharedPreferences();
                sharedPreferences.edit().putInt(PRE_KEY_MAX_TEXTURE_SIZE, size).apply();
            }
        }
    }

    private int getMaxTextureSize() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getInt(PRE_KEY_MAX_TEXTURE_SIZE, 0);
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(CameraApp.getApplication());
    }
}