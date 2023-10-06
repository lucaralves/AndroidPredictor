package com.example.androidpredictor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainActivityOcr extends AppCompatActivity {

    private int STORAGE_REQUEST_CODE = 10;
    private int CAMERA_REQUEST_CODE = 100;
    private int CAMERA_PERMISSION_REQUEST = 123;
    private String price = "None";
    private String barCode = "None";
    private String productNum = "None";

    private ImageView imagem;
    private Button selecionarBtn, predictBtn, cameraBtn;
    private Bitmap bitmap;
    private TextView precoTextView;
    private TextView codigoDeBarrasTextView;
    private TextView numeroDeProdutoTextView;
    private ProgressBar progressBar;

    private TextRecognizer textRecognizer;
    private ArrayList<String> lines;
    private ArrayList<String> prices;
    private List<String> patterns;
    private List<Double> numericPrices;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_ocr);

        imagem = findViewById(R.id.imagem);
        selecionarBtn = findViewById(R.id.selecionar);
        predictBtn = findViewById(R.id.predict);
        cameraBtn = findViewById(R.id.camera);
        precoTextView = findViewById(R.id.preco);
        codigoDeBarrasTextView = findViewById(R.id.codigoDeBarras);
        numeroDeProdutoTextView = findViewById(R.id.numeroDeProduto);
        progressBar = findViewById(R.id.progressBar);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        patterns = new ArrayList<>();
        prices = new ArrayList<>();
        lines = new ArrayList<>();
        numericPrices = new ArrayList<>();

        patterns.add("\\d+,\\d{2}");
        patterns.add("e\\d+,\\d{2}");
        patterns.add("c\\d+,\\d{2}");
        patterns.add("€\\d+,\\d{2}");
        patterns.add("\\d+.\\d{2}");
        patterns.add("e\\d+.\\d{2}");
        patterns.add("c\\d+.\\d{2}");
        patterns.add("€\\d+.\\d{2}");
        patterns.add("(\\d{1})(\\d{2})");
        patterns.add("e(\\d{1})(\\d{2})");
        patterns.add("c(\\d{1})(\\d{2})");
        patterns.add("€(\\d{1})(\\d{2})");
        patterns.add("(\\d{2})(\\d{2})");
        patterns.add("e(\\d{2})(\\d{2})");
        patterns.add("c(\\d{2})(\\d{2})");
        patterns.add("€(\\d{2})(\\d{2})");
        patterns.add("\\d{13}");
        patterns.add("\\d{7}");

        selecionarBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, STORAGE_REQUEST_CODE);
            }
        });

        predictBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bitmap != null) {
                    unactivateUi();

                    InputImage image = InputImage.fromBitmap(bitmap, 0);
                    Task<Text> result =
                            textRecognizer.process(image)
                                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                                        @Override
                                        public void onSuccess(Text visionText) {
                                            for (Text.TextBlock block : visionText.getTextBlocks()) {
                                                for (Text.Line line : block.getLines()) {
                                                    String lineText = line.getText();
                                                    lines.add(lineText);
                                                }
                                            }
                                            getData();
                                            activateUi();
                                        }
                                    })
                                    .addOnFailureListener(
                                            new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    activateUi();
                                                    Toast.makeText(MainActivityOcr.this, e.toString(),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                            });
                }
                else {
                    Toast.makeText(MainActivityOcr.this, "Selecione uma imagem.", Toast.LENGTH_LONG).show();
                }
            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (ContextCompat.checkSelfPermission(MainActivityOcr.this, android.Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        startCameraActivity();
                    } else {
                        ActivityCompat.requestPermissions(MainActivityOcr.this,
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_REQUEST);
                    }
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
        });
    }

    private void startCameraActivity() {
        try {
            Intent intent = new Intent(MainActivityOcr.this, CameraActivityOcr.class);
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == STORAGE_REQUEST_CODE) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                    bitmap = loadAndRotateImage(getRealPathFromURI(uri));
                    imagem.setImageBitmap(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                if (data != null && data.hasExtra("image_path")) {
                    String imagePath = data.getStringExtra("image_path");
                    bitmap = BitmapFactory.decodeFile(imagePath);
                    imagem.setImageBitmap(bitmap);
                    data.removeExtra("image_path");
                }
            }
        }
    }

    private Bitmap loadAndRotateImage(String imagePath) {
        int orientation = getImageOrientation(imagePath);
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        return bitmap;
    }

    private int getImageOrientation(String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            return orientation;
        } catch (IOException e) {
            e.printStackTrace();
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    public String getRealPathFromURI(Uri uri) {
        InputStream inputStream = null;
        String filePath = null;

        try {
            inputStream = getContentResolver().openInputStream(uri);
            File tempFile = createTempFileFromInputStream(inputStream);
            if (tempFile != null) {
                filePath = tempFile.getAbsolutePath();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return filePath;
    }

    private File createTempFileFromInputStream(InputStream inputStream) {
        File tempFile = null;
        OutputStream outputStream = null;

        try {
            tempFile = File.createTempFile("temp_image", ".jpg");
            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return tempFile;
    }

    private void getData() {

        for (String line : lines) {
            for (String patternString : patterns) {
                Pattern pattern = Pattern.compile(patternString);
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    if (patternString.equals("\\d+,\\d{2}")) {
                        prices.add(line);
                    } else if (patternString.equals("e\\d+,\\d{2}")) {
                        prices.add(line.substring(1));
                    } else if (patternString.equals("c\\d+,\\d{2}")) {
                        prices.add(line.substring(1));
                    } else if (patternString.equals("€\\d+,\\d{2}")) {
                        prices.add(line.substring(1));
                    } else if (patternString.equals("\\d+\\.\\d{2}")) {
                        prices.add(line);
                    } else if (patternString.equals("e\\d+\\.\\d{2}")) {
                        prices.add(line.substring(1));
                    } else if (patternString.equals("c\\d+\\.\\d{2}")) {
                        prices.add(line.substring(1));
                    } else if (patternString.equals("€\\d+\\.\\d{2}")) {
                        prices.add(line.substring(1));
                    } else if (patternString.equals("(\\d{1})(\\d{2})")) {
                        prices.add(matcher.group(1) + "." + matcher.group(2));
                    } else if (patternString.equals("e(\\d{1})(\\d{2})")) {
                        prices.add(line.replaceAll("e(\\d{1})(\\d{2})", "$1.$2"));
                    } else if (patternString.equals("c(\\d{1})(\\d{2})")) {
                        prices.add(line.replaceAll("c(\\d{1})(\\d{2})", "$1.$2"));
                    } else if (patternString.equals("€(\\d{1})(\\d{2})")) {
                        prices.add(line.replaceAll("€(\\d{1})(\\d{2})", "$1.$2"));
                    } else if (patternString.equals("(\\d{2})(\\d{2})")) {
                        prices.add(matcher.group(1) + "." + matcher.group(2));
                    } else if (patternString.equals("e(\\d{2})(\\d{2})")) {
                        prices.add(line.replaceAll("e(\\d{2})(\\d{2})", "$1.$2"));
                    } else if (patternString.equals("c(\\d{2})(\\d{2})")) {
                        prices.add(line.replaceAll("c(\\d{2})(\\d{2})", "$1.$2"));
                    } else if (patternString.equals("€(\\d{2})(\\d{2})")) {
                        prices.add(line.replaceAll("€(\\d{2})(\\d{2})", "$1.$2"));
                    } else if (patternString.equals("\\d{7}")) {
                        productNum = line;
                    } else if (patternString.equals("\\d{13}")) {
                        barCode = line;
                    }
                }
            }
        }

        numericPrices = prices.stream()
                .map(price -> Double.parseDouble(price.replace(',', '.')))
                .collect(Collectors.toList());
        if (!numericPrices.isEmpty()) {
            double menorPreco = numericPrices.stream()
                    .min(Double::compare)
                    .get();
            price = Double.toString(menorPreco);
        }
        lines.clear();
        prices.clear();
    }

    private void activateUi() {
        progressBar.setVisibility(View.INVISIBLE);
        selecionarBtn.setEnabled(true);
        predictBtn.setEnabled(true);
        cameraBtn.setEnabled(true);

        if (price.equals("None"))
            precoTextView.setText("Preço: " + price);
        else if (!price.equals("None"))
            precoTextView.setText("Preço: " + price + "€");
        numeroDeProdutoTextView.setText("Número de produto: " + productNum);
        codigoDeBarrasTextView.setText("Código de barras: " + barCode);

        if (!productNum.equals("None")) {
            try {
                Intent intent = new Intent(MainActivityOcr.this, WebActivityOcr.class);
                intent.putExtra("productNum", productNum);
                startActivity(intent);
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }

        price = "None";
        productNum = "None";
        barCode = "None";
    }

    private void unactivateUi() {
        progressBar.setVisibility(View.VISIBLE);
        selecionarBtn.setEnabled(false);
        predictBtn.setEnabled(false);
        cameraBtn.setEnabled(false);
    }
}
