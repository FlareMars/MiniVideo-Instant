package com.gomo.minivideo.camera;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.gomo.minivideo.CameraApp;
import com.google.android.instantapps.InstantApps;
import com.jb.zcamera.camera.Preview;
import com.jb.zcamera.camera.ProcessVideoService;
import com.jb.zcamera.camera.SensorHelper;
import com.jb.zcamera.camera.SettingsManager;
import com.jb.zcamera.filterstore.bo.LocalFilterBO;
import com.jb.zcamera.image.ImageHelper;
import com.jb.zcamera.imagefilter.FilterAdapter;
import com.jb.zcamera.imagefilter.util.ImageFilterTools;
import com.jb.zcamera.sticker.BackgroundAdapter;
import com.jb.zcamera.sticker.LocalBackgroundBO;
import com.jb.zcamera.sticker.LocalStickerBO;
import com.jb.zcamera.sticker.StickerAdapter;
import com.jb.zcamera.ui.DrawableOverlayImageView;
import com.jb.zcamera.ui.MyHighlightView;
import com.jb.zcamera.ui.drawable.StickerDrawable;
import com.jb.zcamera.utils.BitmapUtils;
import com.pixelslab.stickerpe.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.gomo.minivideo.MainActivity.ANIM_BGS_DIR;

/**
 * Created by ruanjiewei on 2017/8/27
 */

public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);

    private static final int REQUEST_CODE_INSTALL_APP = 1;
    public static final int MESSAGE_SHOW_ZOOMLAYOUT = 3;
    public static final int MESSAGE_SHOW_TOAST = 10;
    public static final int MESSAGE_UPDATE_GALLARY_ICON = 5;

    private FrameLayout mPreviewContainer;
    private View mPreviewOverlay;
    private ObjectAnimator mOverlayVisibleAnimator;
    private ObjectAnimator mOverlayGoneAnimator;

    private View mTouchEventInterceptor;
    private TextView mVideoTime = null;
    private TextView mShowTextView = null;
    private ImageView mDownloadButton = null;
    private ImageView mChangeCameraButton = null;
    private ImageView mTakeVideoButton = null;
    private ImageView mCloseFiltersButton = null;
    private ImageView mCloseStickersButton = null;
    private ImageView mCloseBackgroundsButton = null;
    private ImageView mFiltersButton = null;
    private ImageView mStickersButton = null;
    private ImageView mBackgroundsButton = null;
    private LinearLayout mFiltersPanel = null;
    private LinearLayout mStickersPanel = null;
    private LinearLayout mBackgroundsPanel = null;
    private RecyclerView mFiltersListView;
    private RecyclerView mStickersListView;
    private RecyclerView mBackgroundsListView;
    private DrawableOverlayImageView mOverlayImageView;
    private LinearLayout mBottomButtonsPanel = null;

    private int mFiltersPanelHeight;

    private FilterAdapter mFilterAdapter;
    private BackgroundAdapter mBackgroundAdapter;
    private StickerAdapter mStickerAdapter;
    private Handler mHandler = new CameraUIHandler(this);
    private Preview mPreview;

    private int mCurrentOrientation = 0;

    private Handler mMainHandler;
    private boolean mIsRunning = false;
    private int mCurIndex = 0;
    private SensorHelper mSensorHelper;
    private OrientationEventListener mOrientationEventListener = null;

    private SensorEventListener mAccelerometerListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            mPreview.onAccelerometerSensorChanged(event);
        }
    };

    private SensorEventListener mMagneticListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            mPreview.onMagneticSensorChanged(event);
        }
    };

    private View.OnClickListener mTakeVideoButtonClickedListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mPreview.isVideo()) {
                mPreview.toggleTakeButtonClick(false);
            }
        }
    };

    private FilterAdapter.OnItemClickListener mOnFilterClickedListener = new FilterAdapter.OnItemClickListener() {

        @Override
        public void onItemClick(LocalFilterBO item, int position) {
            if (mFilterAdapter.setSelectItemPosition(position)) {
                if (position == FilterAdapter.ORIGINAL_FILTER_POSITION) {
                    mFiltersButton.setImageResource(R.drawable.main_filter_off_normal);
                } else {
                    mFiltersButton.setImageResource(R.drawable.main_filter_on_normal);
                }

                if (position == 0) {
                    mPreview.switchFilter(-1);
                } else {
                    mPreview.switchFilter(position - 1);
                }
            }
        }
    };

    private BackgroundAdapter.OnItemClickListener mOnBackgroundClickedListener = new BackgroundAdapter.OnItemClickListener() {
        @Override
        public void onItemClick(LocalBackgroundBO item, int position) {
            if (mBackgroundAdapter.setSelectItemPosition(position)) {
                if (position == BackgroundAdapter.ORIGINAL_POSITION) {
                    if (mOverlayImageView.getHighlightCount() > 0) {
                        mIsRunning = false;
                        mCurIndex = 0;
                        MyHighlightView background = mOverlayImageView.getHighlightViewAt(0);
                        if (!background.isSelectable()) {
                            mOverlayImageView.removeHightlightView(background);
                            mOverlayImageView.postInvalidate();
                        }
                    }
                } else {
                    mIsRunning = true;
                    mMainHandler.postDelayed(mTestStickerRunnable, 100);
                }
            }
        }
    };

    private StickerAdapter.OnItemClickListener mOnStickerClickedListener = new StickerAdapter.OnItemClickListener() {
        @Override
        public void onItemClick(LocalStickerBO item, int position) {
            addSticker(BitmapFactory.decodeResource(getResources(), item.getResourceId()), false);
        }
    };

    private BroadcastReceiver mVideoProcessFinishedReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (ProcessVideoService.ACTION_UPDATE_GALLARY_ICON
                        .equals(intent.getAction())) {
                    if (!mHandler.hasMessages(MESSAGE_UPDATE_GALLARY_ICON)) {
                        mHandler.sendEmptyMessage(MESSAGE_UPDATE_GALLARY_ICON);
                    }
                }
            }
        }
    };

    private static class CameraUIHandler extends Handler {
        private final WeakReference<CameraFragment> mFragmentRef;

        CameraUIHandler(CameraFragment fragment) {
            mFragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraFragment fragment = mFragmentRef.get();
            if (fragment != null) {
                switch (msg.what) {
                    case MESSAGE_UPDATE_GALLARY_ICON:
                        fragment.useVideoFile(SettingsManager.getRecentlyVideoFile());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public CameraFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mMainHandler = new Handler(Looper.getMainLooper());
        return inflater.inflate(R.layout.camera_fragment_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPreviewContainer = view.findViewById(R.id.preview);
        mTouchEventInterceptor = view.findViewById(R.id.touch_event_interceptor);
        mPreviewOverlay = view.findViewById(R.id.preview_overlay);
        mDownloadButton = view.findViewById(R.id.download_button);
        mChangeCameraButton = view.findViewById(R.id.camera_change_button);
        mTakeVideoButton = view.findViewById(R.id.take_video);
        mFiltersListView = view.findViewById(R.id.filter_list_view);
        mStickersListView = view.findViewById(R.id.sticker_list_view);
        mBackgroundsListView = view.findViewById(R.id.background_list_view);
        mCloseFiltersButton = view.findViewById(R.id.filters_close_btn);
        mCloseStickersButton = view.findViewById(R.id.stickers_close_btn);
        mCloseBackgroundsButton = view.findViewById(R.id.backgrounds_close_btn);
        mOverlayImageView = view.findViewById(R.id.drawable_overlay_image_view);
        mFiltersPanel = view.findViewById(R.id.panel_filters);
        mFiltersButton = view.findViewById(R.id.filter_button);
        mBottomButtonsPanel = view.findViewById(R.id.panel_main_bottom_buttons);
        mStickersPanel = view.findViewById(R.id.panel_sticker);
        mStickersButton = view.findViewById(R.id.sticker_button);
        mBackgroundsPanel = view.findViewById(R.id.panel_backgrounds);
        mBackgroundsButton = view.findViewById(R.id.background_button);

        initViews(savedInstanceState);
    }

    private void initViews(Bundle savedInstanceState) {
        mTouchEventInterceptor.setAlpha(0f);
        mTouchEventInterceptor.setVisibility(View.GONE);
        mPreviewOverlay.setAlpha(0f);
        mPreviewOverlay.setVisibility(View.GONE);
        // 专门用于拦截touch事件
        mTouchEventInterceptor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        mPreview = new Preview(this, savedInstanceState, mPreviewContainer, Preview.MODE_VIDEO);
        mPreview.addView();
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)mOverlayImageView.getLayoutParams();
        layoutParams.height = ImageHelper.SCREEN_HEIGHT;
        layoutParams.width = (int) (ImageHelper.SCREEN_HEIGHT * 9.0f / 16.0f);
        int margin = (ImageHelper.SCREEN_WIDTH - layoutParams.width) / 2;
        layoutParams.leftMargin = margin;
        mOverlayImageView.setLayoutParams(layoutParams);

        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams)mPreview.getGlSurfaceView().getLayoutParams();
        layoutParams2.leftMargin = margin;
        mPreview.getGlSurfaceView().setLayoutParams(layoutParams2);

        mSensorHelper = new SensorHelper(getContext());
        mOrientationEventListener = new OrientationEventListener(CameraApp.getApplication()) {
            @Override
            public void onOrientationChanged(int orientation) {
                CameraFragment.this.onOrientationChanged(orientation);
            }
        };
        mTakeVideoButton.setOnClickListener(mTakeVideoButtonClickedListener);

        List<LocalFilterBO> filterData = ImageFilterTools.getFilterData(getActivity());
        mFilterAdapter = new FilterAdapter(getContext(), filterData, FilterAdapter.MAIN_FILTER_TYPE);
        mFiltersListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mFiltersListView.setAdapter(mFilterAdapter);
        mFilterAdapter.setOnItemClickListener(mOnFilterClickedListener);

        List<LocalBackgroundBO> backgroundData = getBackgroundStickers();
        mBackgroundAdapter = new BackgroundAdapter(getContext(), backgroundData);
        mBackgroundsListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mBackgroundsListView.setAdapter(mBackgroundAdapter);
        mBackgroundAdapter.setOnItemClickListener(mOnBackgroundClickedListener);

        List<LocalStickerBO> stickerData = getNormalStickers();
        mStickerAdapter = new StickerAdapter(getContext(), stickerData);
        mStickersListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mStickersListView.setAdapter(mStickerAdapter);
        mStickerAdapter.setOnItemClickListener(mOnStickerClickedListener);

        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (InstantApps.isInstantApp(getContext())) {
                    InstantApps.showInstallPrompt(getActivity(),
                            REQUEST_CODE_INSTALL_APP, "Install Full Version To Get More Fun!");
                }
            }
        });

        mChangeCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearAllEffect();
                startOverlayVisible(new Runnable() {
                    @Override
                    public void run() {
                        mPreview.toggleSwitchCamera();
                    }
                });
            }
        });

        mCloseStickersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBottomButtonsPanel.animate().alpha(1.0f).setDuration(300).start();
                mStickersPanel.animate().translationY(mFiltersPanelHeight).setDuration(300).start();
            }
        });

        mStickersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBottomButtonsPanel.animate().alpha(0.0f).setDuration(300).start();
                mStickersPanel.animate().translationY(0).setDuration(300).start();
            }
        });

        mCloseBackgroundsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBottomButtonsPanel.animate().alpha(1.0f).setDuration(300).start();
                mBackgroundsPanel.animate().translationY(mFiltersPanelHeight).setDuration(300).start();
            }
        });

        mBackgroundsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBottomButtonsPanel.animate().alpha(0.0f).setDuration(300).start();
                mBackgroundsPanel.animate().translationY(0).setDuration(300).start();
            }
        });

        mCloseFiltersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBottomButtonsPanel.animate().alpha(1.0f).setDuration(300).start();
                mFiltersPanel.animate().translationY(mFiltersPanelHeight).setDuration(300).start();
            }
        });

        mFiltersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBottomButtonsPanel.animate().alpha(0.0f).setDuration(300).start();
                mFiltersPanel.animate().translationY(0).setDuration(300).start();
            }
        });

        mFiltersPanelHeight = getResources().getDimensionPixelSize(R.dimen.panel_filters_height);
    }

    private List<LocalBackgroundBO> getBackgroundStickers() {
        List<LocalBackgroundBO> result = new ArrayList<>();
        result.add(new LocalBackgroundBO("original", R.drawable.filter_original));
        result.add(new LocalBackgroundBO("bg1", R.drawable.frame_v));
        return result;
    }

    private List<LocalStickerBO> getNormalStickers() {
        List<LocalStickerBO> result = new ArrayList<>();
        result.add(new LocalStickerBO("sticker1", R.drawable.emoji_73));
        result.add(new LocalStickerBO("sticker2", R.drawable.emoji_11));
        return result;
    }

    private void clearAllEffect() {
        mFilterAdapter.setSelectItemPosition(0);
        mBackgroundAdapter.setSelectItemPosition(0);
        mOverlayImageView.clearOverlays();
        mPreview.switchFilter(-1);
    }

    private void addSticker(Bitmap stickerBitmap, boolean isBackground) {
        StickerDrawable drawable = new StickerDrawable(getResources(), stickerBitmap);
        drawable.setAntiAlias(true);
        drawable.setMinSize(30, 30);
        MyHighlightView hv = new MyHighlightView(mOverlayImageView, R.style.CameraTheme, drawable);
        hv.setSelectable(!isBackground);
        hv.setOnDeleteClickListener(new MyHighlightView.OnDeleteClickListener() {
            @Override
            public void onDeleteClick(MyHighlightView highlightView) {
                mOverlayImageView.removeHightlightView(highlightView);
            }
        });

        final int width = mOverlayImageView.getWidth();
        final int height = mOverlayImageView.getHeight();
        Matrix mImageMatrix = mOverlayImageView.getImageViewMatrix();
        Rect imageRect = new Rect(0, 0, width, height);
        if (isBackground) {
            RectF cropRect = new RectF(0, 0, width, height);
            hv.setup(getContext(), mImageMatrix, imageRect, cropRect, false);
        } else {
            hv.setPadding(10);

            int cropWidth, cropHeight;
            int x, y;

            // width/height of the sticker
            cropWidth = (int) drawable.getCurrentWidth();
            cropHeight = (int) drawable.getCurrentHeight();

            final int cropSize = Math.max(cropWidth, cropHeight);
            final int screenSize = Math.min(mOverlayImageView.getWidth(), mOverlayImageView.getHeight());
            RectF positionRect = null;
            if (cropSize > screenSize) {
                float ratio;
                float widthRatio = (float) mOverlayImageView.getWidth() / cropWidth;
                float heightRatio = (float) mOverlayImageView.getHeight() / cropHeight;

                if (widthRatio < heightRatio) {
                    ratio = widthRatio;
                } else {
                    ratio = heightRatio;
                }

                cropWidth = (int) ((float) cropWidth * (ratio / 2));
                cropHeight = (int) ((float) cropHeight * (ratio / 2));

                int w = mOverlayImageView.getWidth();
                int h = mOverlayImageView.getHeight();
                positionRect = new RectF(w / 2 - cropWidth / 2, h / 2 - cropHeight / 2,
                        w / 2 + cropWidth / 2, h / 2 + cropHeight / 2);

                positionRect.inset((positionRect.width() - cropWidth) / 2,
                        (positionRect.height() - cropHeight) / 2);
            }

            if (positionRect != null) {
                x = (int) positionRect.left;
                y = (int) positionRect.top;

            } else {
                x = (width - cropWidth) / 2;
                y = (height - cropHeight) / 2;
            }

            Matrix matrix = new Matrix(mImageMatrix);
            matrix.invert(matrix);

            float[] pts = new float[]{x, y, x + cropWidth, y + cropHeight};
            BitmapUtils.mapPoints(matrix, pts);

            RectF cropRect = new RectF(pts[0], pts[1], pts[2], pts[3]);

            hv.setup(getContext(), mImageMatrix, imageRect, cropRect, false);
        }

        if (isBackground) {
            mOverlayImageView.addBackgroundHighlightView(hv);
        } else {
            mOverlayImageView.addHighlightView(hv);
            mOverlayImageView.setSelectedHighlightView(hv);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorHelper.unregisterAccelerometerListener(mAccelerometerListener);
        mSensorHelper.unregisterMagneticListener(mMagneticListener);
        mOrientationEventListener.disable();
        mPreview.onPause();
        getActivity().unregisterReceiver(mVideoProcessFinishedReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        mPreview.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPreview.onDestroy();
        mIsRunning = false;
        mCurIndex = 0;
        mMainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorHelper.registerAccelerometerListener(mAccelerometerListener);
        mSensorHelper.registerMagneticListener(mMagneticListener);
        mOrientationEventListener.enable();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ProcessVideoService.ACTION_UPDATE_GALLARY_ICON);
        filter.addAction(ProcessVideoService.ACTION_ACTIVITY_RESULT);
        getActivity().registerReceiver(mVideoProcessFinishedReceiver, filter);

        mPreview.onResume();
        layoutUI();
    }

    @Override
    public void onStart() {
        super.onStart();
        mPreview.onStart();
    }

    private void startOverlayVisible(final Runnable runnable) {
        if (mOverlayGoneAnimator != null) {
            mOverlayGoneAnimator.cancel();
        }
        if (mOverlayVisibleAnimator != null && mOverlayVisibleAnimator.isRunning()) {
            mOverlayVisibleAnimator.removeAllListeners();
            mOverlayVisibleAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (runnable != null) {
                        runnable.run();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        } else if (mPreviewOverlay.getVisibility() == View.VISIBLE && mPreviewOverlay.getAlpha() == 1.0f) {
            if (runnable != null) {
                runnable.run();
            }
        } else {
            mPreviewOverlay.setVisibility(View.VISIBLE);
            mOverlayVisibleAnimator = ObjectAnimator.ofFloat(mPreviewOverlay, "alpha", 0f, 1.0f);
            mOverlayVisibleAnimator.setDuration(300);
            mOverlayVisibleAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (runnable != null) {
                        runnable.run();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            mOverlayVisibleAnimator.start();
        }
    }

    public void startOverlayGone() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPreviewOverlay.getVisibility() == View.VISIBLE) {
                    if (mOverlayVisibleAnimator != null) {
                        mOverlayVisibleAnimator.cancel();
                    }
                    if (mOverlayGoneAnimator == null || !mOverlayGoneAnimator.isRunning()) {
                        mOverlayGoneAnimator = ObjectAnimator.ofFloat(mPreviewOverlay, "alpha", 1f, 0f);
                        mOverlayGoneAnimator.setDuration(300);
                        mOverlayGoneAnimator.addListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {

                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mPreviewOverlay.setAlpha(0);
                                mPreviewOverlay.setVisibility(View.GONE);
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }

                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        });
                        mOverlayGoneAnimator.start();
                    }
                }
            }
        });
    }

    private void useVideoFile(String filePath) {
        Intent intent = ShareActivity.getIntent(getContext(), filePath);
        startActivity(intent);
    }

    /**
     * 更新录像时间，0为停止录像
     */
    public void updateRecordTime(final long time) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (mVideoTime == null) {
                    return;
                }
                long hour = time / 1000 / 60 / 60;
                long minute = (time / 1000 / 60) % 60;
                long second = (time / 1000) % 60;
                String text = String.format(Locale.CHINA, "%02d:%02d:%02d", hour, minute, second);
                mVideoTime.setText(text);
            }
        });
    }

    /**
     * 更新延迟拍摄剩余时间，0为隐藏显示
     */
    public void updateDelayRemainingTime(final int remainingTime) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (mShowTextView == null) {
                    return;
                }
                if (remainingTime == 0) {
                    mShowTextView.setVisibility(View.GONE);
                } else {
                    if (mShowTextView.getVisibility() != View.VISIBLE) {
                        mShowTextView.setVisibility(View.VISIBLE);
                    }
                    mShowTextView.setText(remainingTime + "");
                }
            }
        });
    }

    public void showLoadingDialog() {

    }

    public void hideLoadingDialog() {

    }

    public boolean isSeekbarTouching() {
        return false;
    }

    public Handler getShowTextHandler() {
        return mHandler;
    }

    public void showCameraErrorDialog() {

    }

    public void updatePreviewMask() {

    }

    public void initCanvasEmojiView() {

    }

    public void updateZoomBarByPercent(final float percent) {

    }

    public void updateFlashValue(final String flashValue,
                                 final boolean showToast) {

    }

    public void startGallaryLoading() {

    }

    public void updateFlashButton() {

    }

    public void updateHdrButton(final boolean showToast) {

    }

    public boolean isTakeButtonPressed() {
        //return mTakePhotoButton.isPressed();
        return false;
    }

    public void showToast(String s) {
        Message msg = mHandler.obtainMessage(MESSAGE_SHOW_TOAST, s);
        mHandler.sendMessage(msg);
    }

    public FilterAdapter getFilterAdapter() {
        return mFilterAdapter;
    }

    public boolean hasStickers() {
        int size = mOverlayImageView.getHighlightCount();
        if (hasBackground()) {
            size--;
        }
        return size > 0;
    }

    public boolean hasBackground() {
        return mOverlayImageView.getHighlightCount() > 0 && !mOverlayImageView.getHighlightViewAt(0).isSelectable();
    }

    private static List<String> mTestStickerBitmaps = new ArrayList<>();

    private Runnable mTestStickerRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsRunning) {
                addSticker(BitmapFactory.decodeFile(mTestStickerBitmaps.get(mCurIndex++)), true);
                if (mCurIndex == mTestStickerBitmaps.size()) {
                    mCurIndex = 0;
                }
                mMainHandler.postDelayed(this, 30);
            }
        }
    };

    static {
        for (int i = 0; i <= 40; i++) {
            if (i < 10) {
                mTestStickerBitmaps.add(ANIM_BGS_DIR + "magazine_0000" + i + ".png");
            } else {
                mTestStickerBitmaps.add(ANIM_BGS_DIR + "magazine_000" + i + ".png");
            }
        }
    }

    public String getStickerBitmap() {
        if (hasStickers()) {
            mOverlayImageView.setSelectedHighlightView(null);
            Bitmap bitmap = Bitmap.createBitmap(mOverlayImageView.getWidth(), mOverlayImageView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mOverlayImageView.draw(canvas, false);
            String dstFile = clearAndGetTempBitmapsPath() + DATE_FORMAT.format(new Date()) + ".png";
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(dstFile));
                return dstFile;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public List<String> getBackgroundBitmaps() {
        if (hasBackground()) {
            return new ArrayList<>(mTestStickerBitmaps);
        }
        return null;
    }

    private String clearAndGetTempBitmapsPath() {
        File tempBitmapsDir = new File(getContext().getCacheDir().getAbsolutePath(), "bitmaps");
        if (!tempBitmapsDir.exists()) {
            tempBitmapsDir.mkdirs();
        } else {
            File[] tempFiles = tempBitmapsDir.listFiles();
            for (File temp : tempFiles) {
                temp.delete();
            }
        }

        return tempBitmapsDir.getAbsolutePath() + File.separator;
    }

    public void changeUIForStartVideo(boolean isStart) {
        if (isStart) {
            if (mPreview.isVideo()) {
                mTouchEventInterceptor.setVisibility(View.VISIBLE);
                mFiltersButton.setVisibility(View.INVISIBLE);
                mStickersButton.setVisibility(View.INVISIBLE);
                mBackgroundsButton.setVisibility(View.INVISIBLE);
                mChangeCameraButton.setVisibility(View.INVISIBLE);
                mDownloadButton.setVisibility(View.INVISIBLE);
                mTakeVideoButton.setImageResource(R.drawable.main_take_video_stop_selector);
            }
        } else {
            if (mPreview.isVideo()) {
                mTouchEventInterceptor.setVisibility(View.GONE);
                mFiltersButton.setVisibility(View.VISIBLE);
                mStickersButton.setVisibility(View.VISIBLE);
                mBackgroundsButton.setVisibility(View.VISIBLE);
                mChangeCameraButton.setVisibility(View.VISIBLE);
                mDownloadButton.setVisibility(View.VISIBLE);
                mTakeVideoButton.setImageResource(R.drawable.main_take_video_selector);
            }
        }
    }

    public void changeUIForResumeVideo(boolean isResume) {

    }

    public void layoutUI() {
        int rotation = getActivity().getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int relative_orientation = (mCurrentOrientation + degrees) % 360;
        int ui_rotation = (360 - relative_orientation) % 360;
        mPreview.setUIRotation(ui_rotation);
    }

    private void onOrientationChanged(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
            return;
        int diff = Math.abs(orientation - mCurrentOrientation);
        if (diff > 180)
            diff = 360 - diff;
        // only change orientation when sufficiently changed
        if (diff > 60) {
            orientation = (orientation + 45) / 90 * 90;
            orientation = orientation % 360;
            if (orientation != mCurrentOrientation) {
                mCurrentOrientation = orientation;
                Log.d(TAG, "mCurrentOrientation is now: " + mCurrentOrientation);
                layoutUI();
            }
        }
        mPreview.onOrientationChanged(orientation);
    }
}
