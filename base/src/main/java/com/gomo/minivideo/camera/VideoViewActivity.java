package com.gomo.minivideo.camera;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.VideoView;

import com.jb.zcamera.gallery.util.FileUtil;
import com.jb.zcamera.image.ImageHelper;
import com.pixelslab.stickerpe.R;

import java.io.File;

public class VideoViewActivity extends AppCompatActivity {

    private static final String TAG = "VideoViewActivity";
    private static final String PARAM_VIDEO_PATH = "video_path";

    public static Intent getIntent(Context context, String resultFilePath) {
        Intent intent = new Intent(context, VideoViewActivity.class);
        intent.putExtra(PARAM_VIDEO_PATH, resultFilePath);
        return intent;

    }

    private VideoView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_view);

        mVideoView = (VideoView) findViewById(R.id.video_view);

        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)mVideoView.getLayoutParams();
        layoutParams.height = ImageHelper.SCREEN_HEIGHT;
        layoutParams.width = (int) (ImageHelper.SCREEN_HEIGHT * 9.0f / 16.0f);
        layoutParams.leftMargin = (ImageHelper.SCREEN_WIDTH - layoutParams.width) / 2;
        mVideoView.setLayoutParams(layoutParams);

        String videoPath = getIntent().getStringExtra(PARAM_VIDEO_PATH);
        mVideoView.setVideoPath(videoPath);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVideoView.start();
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                VideoViewActivity.this.finish();
            }
        });
    }
}
