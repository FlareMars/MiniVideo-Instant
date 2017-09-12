package com.jb.zcamera.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.List;

/**
 * Created by ruanjiewei on 2017/9/10
 */

public class CameraController2  extends CameraController {

    private CameraManager mManager;
    private CameraDevice mCameraDevice;

    public CameraController2(Context context) {
        mManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    void release() {

    }

    @Override
    CameraFeatures getCameraFeatures() {
        String cameraId = null;
        try {
            cameraId = mManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    SupportedValues setSceneMode(String value) {
        return null;
    }

    @Override
    public String getSceneMode() {
        return null;
    }

    @Override
    public Size getPictureSize() {
        return null;
    }

    @Override
    void setPictureSize(int width, int height) {

    }

    @Override
    public Size getPreviewSize() {
        return null;
    }

    @Override
    void setPreviewSize(int width, int height) {

    }

    @Override
    public int getZoom() {
        return 0;
    }

    @Override
    void setZoom(int value) {

    }

    @Override
    void setPreviewFpsRange(int min, int max) {

    }

    @Override
    void getPreviewFpsRange(int[] fps_range) {

    }

    @Override
    List<int[]> getSupportedPreviewFpsRange() {
        return null;
    }

    @Override
    void setFocusValue(String focus_value) {

    }

    @Override
    public String getFocusValue() {
        return null;
    }

    @Override
    void setFlashValue(String flash_value) {

    }

    @Override
    public String getFlashValue() {
        return null;
    }

    @Override
    void setRecordingHint(boolean hint) {

    }

    @Override
    void setRotation(int rotation) {

    }

    @Override
    void setLocationInfo(Location location) {

    }

    @Override
    void removeLocationInfo() {

    }

    @Override
    void enableShutterSound(boolean enabled) {

    }

    @Override
    boolean setFocusAndMeteringArea(List<Area> focusAreas, List<Area> meterAreas) {
        return false;
    }

    @Override
    public List<Area> getFocusAreas() {
        return null;
    }

    @Override
    public List<Area> getMeteringAreas() {
        return null;
    }

    @Override
    void reconnect() throws IOException {

    }

    @Override
    void setPreviewDisplay(SurfaceHolder holder) throws IOException {

    }

    @Override
    void startPreview() {

    }

    @Override
    void stopPreview() {

    }

    @Override
    public boolean startFaceDetection() {
        return false;
    }

    @Override
    public boolean stopFaceDetection() {
        return false;
    }

    @Override
    void setFaceDetectionListener(FaceDetectionListener listener) {

    }

    @Override
    void autoFocus(AutoFocusCallback cb) {

    }

    @Override
    void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {

    }

    @Override
    void cancelAutoFocus() {

    }

    @Override
    void takePicture(PictureCallback raw, PictureCallback jpeg, boolean shutterSound) {

    }

    @Override
    void setDisplayOrientation(int degrees) {

    }

    @Override
    int getDisplayOrientation() {
        return 0;
    }

    @Override
    int getCameraOrientation() {
        return 0;
    }

    @Override
    boolean isFrontFacing() {
        return false;
    }

    @Override
    void unlock() {

    }

    @Override
    void initVideoRecorder(MediaRecorder video_recorder) {

    }

    @Override
    CameraHolder getCamera() {
        return null;
    }

    @Override
    boolean supportedHDR() {
        return false;
    }

    @Override
    boolean isAutoFocus() {
        return false;
    }

    @Override
    public boolean setWhiteBalance(String value) {
        return false;
    }

    @Override
    public String getWhiteBalance() {
        return null;
    }

    @Override
    public int getExposureCompensation() {
        return 0;
    }

    @Override
    public boolean setExposureCompensation(int new_exposure) {
        return false;
    }

    @Override
    public List<String> getSupportedISO() {
        return null;
    }

    @Override
    public boolean setISO(String value) {
        return false;
    }

    @Override
    public String getISO() {
        return null;
    }
}
