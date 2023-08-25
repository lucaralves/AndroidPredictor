package com.example.androidpredictor;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.example.androidpredictor.ml.Detect;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ImageView imagem;
    Button selecionarBtn, predictBtn, cameraBtn;
    private SeekBar seekBar;
    private TextView textView;
    private Bitmap bitmap;
    private Detect model;
    float confidenceThreshold = (float) 0.50;

    String[] classes = {"FF1", "FF2", "FB2", "FB1", "FE1", "FE2", "GB2", "FP1", "FP2", "FM1", "FM2", "GB1"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagem = findViewById(R.id.imagem);
        selecionarBtn = findViewById(R.id.selecionar);
        predictBtn = findViewById(R.id.predict);
        cameraBtn = findViewById(R.id.camera);
        seekBar = findViewById(R.id.seekBar);
        textView = findViewById(R.id.textView);

        try {
            model = Detect.newInstance(MainActivity.this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        selecionarBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, 10);
            }
        });

        predictBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bitmap != null) {
                    TensorImage normalizedInputImageTensor = TensorImage.fromBitmap(bitmap);

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
                        bitmap = drawBoundingBox(bitmap, box, score,
                                classes[(int) classIndex]);
                    }

                    // Show the results on UI.
                    imagem.setImageBitmap(bitmap);
                }
                else {
                    Toast.makeText(MainActivity.this, "Selecione uma imagem.", Toast.LENGTH_LONG).show();
                }
            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    model.close();
                    Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
        });

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

    @Override
    protected void onResume() {
        super.onResume();

        try {
            model = Detect.newInstance(MainActivity.this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (requestCode == 10) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                    imagem.setImageBitmap(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public static Bitmap drawBoundingBox(Bitmap bitmap, float[] box, float score, String label) {

        // Create a new bitmap with the same dimensions as the input bitmap.
        Bitmap outputBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());

        // Create a canvas object to draw on the output bitmap.
        Canvas canvas = new Canvas(outputBitmap);

        // Draw the input bitmap on the canvas.
        canvas.drawBitmap(bitmap, 0, 0, null);

        // Create a paint object to draw the bounding box and label.
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4.0f);
        paint.setTextSize(80.0f);

        // Get the coordinates of the bounding box.
        float xmin = box[0] * bitmap.getWidth();
        float ymin = box[1] * bitmap.getHeight();
        float xmax = box[2] * bitmap.getWidth();
        float ymax = box[3] * bitmap.getHeight();

        // Draw the bounding box on the canvas.
        canvas.drawRect(xmin, ymin, xmax, ymax, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(label + " " + score,
                box[0] * bitmap.getWidth(), box[1] * bitmap.getHeight() , paint);

        // Return the output bitmap with the bounding box drawn on it.
        return outputBitmap;
    }

    public static ArrayList<Integer> getMostConfidentDetections(float[] detections, float confidenceThreshold) {

        ArrayList<Integer> mostConfidentDetections = new ArrayList<>();
        for (int i = 0; i < detections.length; i++) {
            if (detections[i] >= confidenceThreshold) {
                mostConfidentDetections.add(i);
            }
        }
        return mostConfidentDetections;
    }
}