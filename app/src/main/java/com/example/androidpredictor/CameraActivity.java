package com.example.androidpredictor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.androidpredictor.ml.Detect;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static android.content.ContentValues.TAG;
import static com.example.androidpredictor.MainActivity.maxIndex;

public class CameraActivity extends AppCompatActivity {

    private TextureView textureView;
    private ImageView imageView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    Bitmap bitmap;
    Bitmap inputBitmap;
    int[] colours = new int[] {
      Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    };
    Paint paint = new Paint();
    Detect model;
    String[] classes = {"CocaCola", "Pepsi"};

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView = findViewById(R.id.texture);
        imageView = findViewById(R.id.imageView);

        if (textureView != null) {
            textureView.setSurfaceTextureListener(textureListner);
        }
    }

    TextureView.SurfaceTextureListener textureListner = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            bitmap = textureView.getBitmap();

            try {
                model = Detect.newInstance(CameraActivity.this);

                TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 320, 320, 3}, DataType.FLOAT32);
                ByteBuffer input = ByteBuffer.allocateDirect(320 * 320 * 3 * 4).order(ByteOrder.nativeOrder());
                inputBitmap = Bitmap.createScaledBitmap(bitmap, 320, 320, true);

                // Normalização do input.
                for (int y = 0; y < 320; y++) {
                    for (int x = 0; x < 320; x++) {
                        int px = inputBitmap.getPixel(x, y);

                        // Get channel values from the pixel value.
                        int r = Color.red(px);
                        int g = Color.green(px);
                        int b = Color.blue(px);

                        float rf = (r) / 255.0f;
                        float gf = (g) / 255.0f;
                        float bf = (b) / 255.0f;

                        input.putFloat(rf);
                        input.putFloat(gf);
                        input.putFloat(bf);
                    }
                }

                inputFeature0.loadBuffer(input);

                // Runs model inference and gets result.
                Detect.Outputs outputs = model.process(inputFeature0);

                // Detection Scores.
                TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
                // Bounding Boxes.
                TensorBuffer outputFeature1 = outputs.getOutputFeature1AsTensorBuffer();
                // Number Of Detections.
                TensorBuffer outputFeature2 = outputs.getOutputFeature2AsTensorBuffer();
                // Index Of Classes Detected.
                TensorBuffer outputFeature3 = outputs.getOutputFeature3AsTensorBuffer();

                // Converte-se o output do modelo num array float.
                float[] detectionScores = outputFeature0.getFloatArray();
                float[] boundingBoxes = outputFeature1.getFloatArray();
                float[] numberOfDetections = outputFeature2.getFloatArray();
                float[] indexOfClassesDetected = outputFeature3.getFloatArray();

                // Get the index of the most confident detection.
                int mostConfident = maxIndex(detectionScores);

                // Get the bounding box coordinates.
                float[] box = new float[4];
                box[1] = boundingBoxes[mostConfident];   //ymin
                box[0] = boundingBoxes[mostConfident + 1]; //xmin
                box[3] = boundingBoxes[mostConfident + 2]; // ymax
                box[2] = boundingBoxes[mostConfident + 3]; // xmax

                // Get the detection score.
                float score = detectionScores[mostConfident];

                // Get the class label index.
                float classIndex = indexOfClassesDetected[mostConfident];

                Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(mutable);

                paint.setTextSize(mutable.getHeight()/15f);
                paint.setStrokeWidth(mutable.getHeight()/85f);

                if (score > 0.85) {
                    paint.setColor(colours[2]);
                    paint.setStyle(Paint.Style.STROKE);

                    canvas.drawRect(new RectF(box[0] * mutable.getWidth(), box[1] * mutable.getHeight(),
                            box[2] * mutable.getWidth(), box[3] * mutable.getHeight()), paint);

                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawText(classes[(int) classIndex] + " " + score,
                            box[0] * mutable.getWidth(), box[1] * mutable.getHeight() , paint);
                }

                imageView.setImageBitmap(mutable);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(CameraActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");

        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        Log.e(TAG, "openCamera X");
    }

    protected void updatePreview() {

        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(CameraActivity.this, "You can´t use this app without granting permissions", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();

        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListner);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
}
