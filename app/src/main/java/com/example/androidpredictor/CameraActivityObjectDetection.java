package com.example.androidpredictor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.androidpredictor.ml.Detect;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static android.content.ContentValues.TAG;
import static com.example.androidpredictor.MainActivityObjectDetection.*;

public class CameraActivityObjectDetection extends AppCompatActivity {

    private TextureView textureView;
    private ImageView imageView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private SeekBar seekBar;
    private TextView textView;

    Bitmap bitmap;
    Bitmap scaledBitmap;
    Detect model;
    String[] classes = {"FF1", "FF2", "FB2", "FB1", "FE1", "FE2", "GB2", "FP1", "FP2", "FM1", "FM2", "GB1"};
    float confidenceThreshold = (float) 0.50;

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
        setContentView(R.layout.activity_camera_object_detection);

        textureView = findViewById(R.id.texture);
        imageView = findViewById(R.id.imageView);
        seekBar = findViewById(R.id.seekBar);
        textView = findViewById(R.id.textView);

        try {
            model = Detect.newInstance(CameraActivityObjectDetection.this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (textureView != null) {
            textureView.setSurfaceTextureListener(textureListner);
        }

        // Define um listener para a SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                confidenceThreshold = (float) progress / 100.0f;
                textView.setText("Limiar de confiança: " + progress + " %");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Chamado quando o usuário toca na SeekBar
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Chamado quando o usuário para de tocar na SeekBar
            }
        });
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
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, imageDimension.getHeight(), imageDimension.getWidth(), false);

            TensorImage normalizedInputImageTensor = TensorImage.fromBitmap(scaledBitmap);

            // Runs model inference and gets result.
            Detect.Outputs outputs = model.process(normalizedInputImageTensor);

            // Detection Scores.
            TensorBuffer outputFeature0 = outputs.getScoresAsTensorBuffer();
            // Bounding Boxes.
            TensorBuffer outputFeature1 = outputs.getLocationsAsTensorBuffer();
            // Index Of Classes Detected.
            TensorBuffer outputFeature3 = outputs.getClassesAsTensorBuffer();

            // Converte-se o output do modelo num array float.
            float[] detectionScores = outputFeature0.getFloatArray();
            float[] boundingBoxes = outputFeature1.getFloatArray();
            float[] indexOfClassesDetected = outputFeature3.getFloatArray();

            // Get the index of the most confident detections.
            ArrayList<Integer> mostConfidents = getMostConfidentDetections(detectionScores, confidenceThreshold);

            for (int i = 0; i < mostConfidents.size(); i++) {
                // Get the bounding box coordinates.
                float[] box = new float[4];
                box[1] = boundingBoxes[mostConfidents.get(i) * 4]; // ymin
                box[0] = boundingBoxes[(mostConfidents.get(i) * 4) + 1]; // xmin
                box[3] = boundingBoxes[(mostConfidents.get(i) * 4) + 2]; // ymax
                box[2] = boundingBoxes[(mostConfidents.get(i) * 4) + 3]; // xmax

                // Get the detection score.
                float score = detectionScores[mostConfidents.get(i)];

                // Get the class label index.
                float classIndex = indexOfClassesDetected[mostConfidents.get(i)];

                // Draw the box and label on image.
                scaledBitmap = drawBoundingBox(scaledBitmap, box, score,
                        classes[(int)classIndex]);
            }
            imageView.setImageBitmap(null);
            imageView.setImageBitmap(scaledBitmap);
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
                    Toast.makeText(CameraActivityObjectDetection.this, "Configuration change", Toast.LENGTH_SHORT).show();
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
                ActivityCompat.requestPermissions(CameraActivityObjectDetection.this, new String[]{Manifest.permission.CAMERA,
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
                Toast.makeText(CameraActivityObjectDetection.this, "You can´t use this app without granting permissions", Toast.LENGTH_LONG).show();
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
