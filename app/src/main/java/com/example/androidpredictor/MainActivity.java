package com.example.androidpredictor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button ocr;
    Button od;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ocr = findViewById(R.id.Ocr);
        od = findViewById(R.id.Od);

        ocr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startOcrActivity();
            }
        });

        od.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startObjectDetectionActivity();
            }
        });
    }

    private void startOcrActivity() {
        Intent intent = new Intent(MainActivity.this, MainActivityOcr.class);
        startActivity(intent);
    }

    private void startObjectDetectionActivity() {
        Intent intent = new Intent(MainActivity.this, MainActivityObjectDetection.class);
        startActivity(intent);
    }
}
