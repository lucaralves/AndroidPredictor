package com.example.androidpredictor;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.example.androidpredictor.ml.Detect;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {

    private ImageView imagem;
    Button selecionarBtn, predictBtn, cameraBtn;
    private TextView resultado;
    private TextView classe;
    private Bitmap bitmap;
    private Detect model;

    String[] classes = {"CocaCola", "Pepsi"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagem = findViewById(R.id.imagem);
        selecionarBtn = findViewById(R.id.selecionar);
        predictBtn = findViewById(R.id.predict);
        cameraBtn = findViewById(R.id.camera);
        resultado = findViewById(R.id.resultado);
        classe = findViewById(R.id.classe);

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

                try {
                    model = Detect.newInstance(MainActivity.this);

                    int height = bitmap.getHeight();
                    int width = bitmap.getWidth();

                    // Creates inputs for reference.
                    TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 320, 320, 3}, DataType.FLOAT32);
                    ByteBuffer input = ByteBuffer.allocateDirect(320 * 320 * 3 * 4).order(ByteOrder.nativeOrder());
                    bitmap = Bitmap.createScaledBitmap(bitmap, 320, 320, true);

                    // Normalização do input.
                    for (int y = 0; y < 320; y++) {
                        for (int x = 0; x < 320; x++) {
                            int px = bitmap.getPixel(x, y);

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
                    box[1] = boundingBoxes[mostConfident]; //ymin
                    box[0] = boundingBoxes[mostConfident + 1]; //xmin
                    box[3] = boundingBoxes[mostConfident + 2]; // ymax
                    box[2] = boundingBoxes[mostConfident + 3]; // xmax

                    // Get the detection score.
                    float score = detectionScores[mostConfident];

                    // Get the class label index.
                    float classIndex = indexOfClassesDetected[mostConfident];

                    // Draw the box on image.
                    Bitmap processedBitmap = drawBoundingBox(bitmap, box);
                    processedBitmap = Bitmap.createScaledBitmap(processedBitmap, width, height, true);

                    // Show the results on UI.
                    imagem.setImageBitmap(processedBitmap);
                    resultado.setText("Score: " + String.valueOf(score));
                    classe.setText("Classe identificada: " + classes[(int) classIndex]);

                    // Releases model resources if no longer used.
                    model.close();

                } catch (IOException e) {
                    // Handle any exceptions during inference.
                    e.printStackTrace();
                    // Display an error message to the user if necessary.
                    Toast.makeText(MainActivity.this, "Error during inference: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
        });
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

    public static Bitmap drawBoundingBox(Bitmap bitmap, float[] box) {

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
        paint.setTextSize(24.0f);

        // Get the coordinates of the bounding box.
        float xmin = box[0] * bitmap.getWidth();
        float ymin = box[1] * bitmap.getHeight();
        float xmax = box[2] * bitmap.getWidth();
        float ymax = box[3] * bitmap.getHeight();

        // Draw the bounding box on the canvas.
        canvas.drawRect(xmin, ymin, xmax, ymax, paint);

        // Return the output bitmap with the bounding box drawn on it.
        return outputBitmap;
    }

    public static int maxIndex(float[] x) {
        float maxValue = -Float.MAX_VALUE;
        int maxIndex = -1;
        for (int i = 0; i < x.length; i++) {
            if (x[i] > maxValue) {
                maxValue = x[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }
}