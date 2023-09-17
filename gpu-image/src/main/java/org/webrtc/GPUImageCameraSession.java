package org.webrtc;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;

import com.flashphoner.fpwcsapi.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer;
import jp.co.cyberagent.android.gpuimage.PixelBuffer;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageMonochromeFilter;
import jp.co.cyberagent.android.gpuimage.util.Rotation;

public class GPUImageCameraSession implements CameraSession {
    private static final String TAG = "GPUImageCameraSession";
    private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;
    private static final Histogram camera1StartTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StartTimeMs", 1, 10000, 50);
    private static final Histogram camera1StopTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StopTimeMs", 1, 10000, 50);
    private static final Histogram camera1ResolutionHistogram;
    private final Handler cameraThreadHandler;
    private final Events events;
    private final boolean captureToTexture;
    private final Context applicationContext;
    private final SurfaceTextureHelper surfaceTextureHelper;
    private final int cameraId;
    private final Camera camera;
    private final Camera.CameraInfo info;
    private final CameraEnumerationAndroid.CaptureFormat captureFormat;
    private final long constructionTimeNs;
    private GPUImageCameraSession.SessionState state;
    private boolean firstFrameReported;
    private boolean cameraReleased = true;

    private static boolean isUsedFilter = false;

    public static void create(CreateSessionCallback callback, Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, int width, int height, int framerate) {
        long constructionTimeNs = System.nanoTime();
        Logging.d("GPUImageCameraSession", "Open camera " + cameraId);
        events.onCameraOpening();

        Camera camera;
        try {
            camera = Camera.open(cameraId);

        } catch (RuntimeException var19) {
            callback.onFailure(FailureType.ERROR, var19.getMessage());
            return;
        }

        if (camera == null) {
            callback.onFailure(FailureType.ERROR, "android.hardware.Camera.open returned null for camera id = " + cameraId);
        } else {
            try {
                camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
            } catch (RuntimeException | IOException var18) {
                camera.release();
                callback.onFailure(FailureType.ERROR, var18.getMessage());
                return;
            }

            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);

            CameraEnumerationAndroid.CaptureFormat captureFormat;
            try {
                Camera.Parameters parameters = camera.getParameters();
                captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
                Size pictureSize = findClosestPictureSize(parameters, width, height);
                updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
            } catch (RuntimeException var17) {
                camera.release();
                callback.onFailure(FailureType.ERROR, var17.getMessage());
                return;
            }

            if (!captureToTexture) {
                int frameSize = captureFormat.frameSize();

                for(int i = 0; i < 3; ++i) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
                    camera.addCallbackBuffer(buffer.array());
                }
            }

            camera.setDisplayOrientation(0);
            callback.onDone(new GPUImageCameraSession(events, captureToTexture, applicationContext, surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
        }
    }

    private static void updateCameraParameters(Camera camera, Camera.Parameters parameters, CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
        parameters.setPreviewSize(captureFormat.width, captureFormat.height);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);
        if (!captureToTexture) {
            Objects.requireNonNull(captureFormat);
            parameters.setPreviewFormat(17);
        }

        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }

        if (focusModes.contains("continuous-video")) {
            parameters.setFocusMode("continuous-video");
        }

        camera.setParameters(parameters);
    }

    private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(Camera.Parameters parameters, int width, int height, int framerate) {
        List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
        Logging.d("GPUImageCameraSession", "Available fps ranges: " + supportedFramerates);
        CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
        Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
        CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);
        return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
    }

    private static Size findClosestPictureSize(Camera.Parameters parameters, int width, int height) {
        return CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
    }

    private GPUImageCameraSession(Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, Camera camera, Camera.CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
        Logging.d("GPUImageCameraSession", "Create new camera1 session on camera " + cameraId);
        this.cameraThreadHandler = new Handler();
        this.events = events;
        this.captureToTexture = captureToTexture;
        this.applicationContext = applicationContext;
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.cameraId = cameraId;
        this.camera = camera;
        setCameraReleased(false);
        this.info = info;
        this.captureFormat = captureFormat;
        this.constructionTimeNs = constructionTimeNs;
        surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height);
        this.startCapturing();
    }

    public void stop() {
        Logging.d("GPUImageCameraSession", "Stop camera1 session on camera " + this.cameraId);
        this.checkIsOnCameraThread();
        if (this.state != GPUImageCameraSession.SessionState.STOPPED) {
            long stopStartTime = System.nanoTime();
            this.stopInternal();
            int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
            camera1StopTimeMsHistogram.addSample(stopTimeMs);
        }

    }

    private void startCapturing() {
        Logging.d("GPUImageCameraSession", "Start capturing");
        this.checkIsOnCameraThread();
        this.state = GPUImageCameraSession.SessionState.RUNNING;
        this.camera.setErrorCallback(new Camera.ErrorCallback() {
            public void onError(int error, Camera camera) {
                String errorMessage;
                if (error == 100) {
                    errorMessage = "Camera server died!";
                } else {
                    errorMessage = "Camera error: " + error;
                }

                Logging.e("GPUImageCameraSession", errorMessage);
                GPUImageCameraSession.this.stopInternal();
                if (error == 2) {
                    GPUImageCameraSession.this.events.onCameraDisconnected(GPUImageCameraSession.this);
                } else {
                    GPUImageCameraSession.this.events.onCameraError(GPUImageCameraSession.this, errorMessage);
                }

            }
        });
        if (this.captureToTexture) {
            this.listenForTextureFrames();
        } else {
            this.listenForBytebufferFrames();
        }

        try {
            this.camera.startPreview();
        } catch (RuntimeException var2) {
            this.stopInternal();
            this.events.onCameraError(this, var2.getMessage());
        }

    }

    private void stopInternal() {
        Logging.d("GPUImageCameraSession", "Stop internal");
        this.checkIsOnCameraThread();
        if (this.state == GPUImageCameraSession.SessionState.STOPPED) {
            Logging.d("GPUImageCameraSession", "Camera is already stopped");
        } else {
            this.state = GPUImageCameraSession.SessionState.STOPPED;
            this.surfaceTextureHelper.stopListening();
            this.camera.stopPreview();
            this.camera.release();
            setCameraReleased(true);
            this.events.onCameraClosed(this);
            Logging.d("GPUImageCameraSession", "Stop done");
        }
    }

    private void setCameraReleased(boolean released) {
        this.cameraReleased = released;
    }

    public boolean isCameraReleased() {
        return cameraReleased;
    }

    private void listenForTextureFrames() {
        this.surfaceTextureHelper.startListening((frame) -> {
            this.checkIsOnCameraThread();
            if (this.state != GPUImageCameraSession.SessionState.RUNNING) {
                Logging.d("GPUImageCameraSession", "Texture frame captured but camera is no longer running.");
            } else {
                if (!this.firstFrameReported) {
                    int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.constructionTimeNs);
                    camera1StartTimeMsHistogram.addSample(startTimeMs);
                    this.firstFrameReported = true;
                }

                VideoFrame modifiedFrame = new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix((TextureBufferImpl)frame.getBuffer(), this.info.facing == 1, 0), this.getFrameOrientation(), frame.getTimestampNs());

                this.events.onFrameCaptured(this, modifiedFrame);
                modifiedFrame.release();
            }
        });
    }

    private void listenForBytebufferFrames() {
        this.camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera callbackCamera) {
                GPUImageCameraSession.this.checkIsOnCameraThread();
                if (callbackCamera != GPUImageCameraSession.this.camera) {
                    Logging.e("GPUImageCameraSession", "Callback from a different camera. This should never happen.");
                } else if (GPUImageCameraSession.this.state != GPUImageCameraSession.SessionState.RUNNING) {
                    Logging.d("GPUImageCameraSession", "Bytebuffer frame captured but camera is no longer running.");
                } else {
                    long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                    if (!GPUImageCameraSession.this.firstFrameReported) {
                        int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - GPUImageCameraSession.this.constructionTimeNs);
                        GPUImageCameraSession.camera1StartTimeMsHistogram.addSample(startTimeMs);
                        GPUImageCameraSession.this.firstFrameReported = true;
                    }

                    applyFilter(data, GPUImageCameraSession.this.captureFormat.width, GPUImageCameraSession.this.captureFormat.height);

                    VideoFrame.Buffer frameBuffer = new NV21Buffer(data, GPUImageCameraSession.this.captureFormat.width, GPUImageCameraSession.this.captureFormat.height, () -> {
                        GPUImageCameraSession.this.cameraThreadHandler.post(() -> {
                            if (GPUImageCameraSession.this.state == GPUImageCameraSession.SessionState.RUNNING) {
                                GPUImageCameraSession.this.camera.addCallbackBuffer(data);
                            }

                        });
                    });
                    VideoFrame frame = new VideoFrame(frameBuffer, GPUImageCameraSession.this.getFrameOrientation(), captureTimeNs);
                    GPUImageCameraSession.this.events.onFrameCaptured(GPUImageCameraSession.this, frame);
                    frame.release();
                }
            }
        });
    }

    private void applyFilter(byte[] data, int width, int height) {
        if (!isUsedFilter) {
            return;
        }
        GPUImageMonochromeFilter filter = new GPUImageMonochromeFilter();
        filter.setColor(0,0,0);

        GPUImageRenderer renderer = new GPUImageRenderer(filter);
        renderer.setRotation(Rotation.NORMAL, false, false);
        renderer.setScaleType(GPUImage.ScaleType.CENTER_INSIDE);

        PixelBuffer buffer = new PixelBuffer(width, height);
        buffer.setRenderer(renderer);

        renderer.onPreviewFrame(data, width, height);
        Bitmap newBitmapRgb = buffer.getBitmap();
        byte[] dataYuv = Utils.getNV21(width, height, newBitmapRgb);
        System.arraycopy(dataYuv, 0, data, 0, dataYuv.length);

        filter.destroy();
        buffer.destroy();
    }

    public static boolean isUsedFilter() {
        return isUsedFilter;
    }

    public static void setUsedFilter(boolean usedFilter) {
        isUsedFilter = usedFilter;
    }

    private int getFrameOrientation() {
        int rotation = CameraSession.getDeviceOrientation(this.applicationContext);
        if (this.info.facing == 0) {
            rotation = 360 - rotation;
        }

        return (this.info.orientation + rotation) % 360;
    }

    private void checkIsOnCameraThread() {
        if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
            throw new IllegalStateException("Wrong thread");
        }
    }

    static {
        camera1ResolutionHistogram = Histogram.createEnumeration("WebRTC.Android.Camera1.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
    }

    private static enum SessionState {
        RUNNING,
        STOPPED;

        private SessionState() {
        }
    }
}
