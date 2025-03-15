package com.example.myvideoapp;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        ImageView imageView = findViewById(R.id.image_view);
        String imagePath = getIntent().getStringExtra("image_path");

        Glide.with(this)
                .load(imagePath)
                .error(R.drawable.ic_broken_image)
                .into(imageView);
    }
}