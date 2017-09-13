package com.gomo.minivideo.camera;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.share.Sharer;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.widget.ShareDialog;
import com.google.android.instantapps.InstantApps;
import com.pixelslab.stickerpe.R;

public class ShareActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_INSTALL_APP = 1;
    private static final String PARAM_VIDEO_PATH = "video_path";

    public static Intent getIntent(Context context, String resultFilePath) {
        Intent intent = new Intent(context, ShareActivity.class);
        intent.putExtra(PARAM_VIDEO_PATH, resultFilePath);
        return intent;
    }

    private String mVideoPath;

    private ImageView mThumbnailImageView;
    private ImageView mBackBtn;
    private TextView mTitleTv;
    private Button shareBtn;
    private Button installBtn;

    ShareDialog shareDialog;
    CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        mVideoPath = getIntent().getStringExtra(PARAM_VIDEO_PATH);
        mThumbnailImageView = (ImageView) findViewById(R.id.image_view);
        mBackBtn = (ImageView) findViewById(R.id.back);
        mTitleTv = (TextView) findViewById(R.id.title);
        shareBtn = (Button) findViewById(R.id.btn_share);
        installBtn = (Button) findViewById(R.id.btn_install);

        initViews();
    }

    private void initViews() {
        mTitleTv.setText("SHARE");        //设置标题字体
        AssetManager mgr=getAssets();
        Typeface tf= Typeface.createFromAsset(mgr, "Sansation_Regular.ttf");
        mTitleTv.setTypeface(tf);
        shareDialog = new ShareDialog(this);

        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callbackManager = CallbackManager.Factory.create();
                shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
                    @Override
                    public void onSuccess(Sharer.Result result) {
                        Log.e("Facebook", "onSuccess: " + result.toString());
                        if (InstantApps.isInstantApp(ShareActivity.this)) {
                            InstantApps.showInstallPrompt(ShareActivity.this,
                                    REQUEST_CODE_INSTALL_APP, "Install Full Version To Get More Fun!");
                        }
                    }

                    @Override
                    public void onCancel() {
                        Log.e("Facebook", "onCancel: ");
                    }

                    @Override
                    public void onError(FacebookException error) {
                        Log.e("Facebook", "onError: " + error.toString());
                    }
                });
                if (ShareDialog.canShow(ShareLinkContent.class)) {
                    ShareLinkContent linkContent = new ShareLinkContent.Builder()
                            .setContentTitle("MiniVideo")
                            .setContentDescription("A interesting camera app!")
                            .setContentUrl(Uri.parse("https://hugo775128583.github.io/main"))
                            .build();
                    shareDialog.show(linkContent);
                }else {
                    Toast.makeText(ShareActivity.this,"未安装facebook", Toast.LENGTH_LONG).show();
                }
            }
        });

        installBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (InstantApps.isInstantApp(ShareActivity.this)) {
                    InstantApps.showInstallPrompt(ShareActivity.this,
                            REQUEST_CODE_INSTALL_APP, "Install Full Version To Get More Fun!");
                }
            }
        });

        if (mVideoPath != null && !"".equals(mVideoPath)) {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(mVideoPath, MediaStore.Video.Thumbnails.MINI_KIND);
            mThumbnailImageView.setImageBitmap(bitmap);
            mThumbnailImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = VideoViewActivity.getIntent(ShareActivity.this, mVideoPath);
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }
}
