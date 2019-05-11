package com.avaucamps.whatsmydog;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements ImageClassification {

    final private int GALLERY_REQUEST_CODE = 1;
    static final int REQUEST_IMAGE_CAPTURE = 2;

    private boolean isCameraAvailable;
    private PackageManager packageManager;
    private ImageButton takePicture;
    private ImageButton importPicture;
    private ImageView imageView;
    private TextView breedTextView;
    private ProgressBar loadingSpinner;
    private ImageHelper imageHelper;
    private ModelHelper modelHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        packageManager = getApplicationContext().getPackageManager();
        setupUI();
        setupActions();
        modelHelper = new ModelHelper(this, this.getAssets());
    }

    private void setupUI() {
        imageView = findViewById(R.id.imageView);
        breedTextView = findViewById(R.id.breedTextView);
        breedTextView.setEnabled(false);
        breedTextView.setVisibility(View.INVISIBLE);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        loadingSpinner.setVisibility(View.GONE);
    }

    private void setupActions() {
        setupImportPictureAction();
        isCameraAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        if (isCameraAvailable) {
            setupTakePictureAction();
        } else {
            takePicture.setEnabled(false);
        }
    }

    private void setupTakePictureAction() {
        takePicture = findViewById(R.id.take_picture_action);
        imageHelper = new ImageHelper();
        takePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }

    private void setupImportPictureAction() {
        importPicture = findViewById(R.id.import_picture_action);
        importPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGallery();
            }
        });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), GALLERY_REQUEST_CODE);
        }
    }

    private void takePicture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;

            try {
                photoFile = imageHelper.createImageFile(getApplicationContext());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                imageHelper.setPhotoURI(photoURI);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!(resultCode == Activity.RESULT_OK)) {
            return;
        }
        breedTextView.setVisibility(View.INVISIBLE);

        switch (requestCode) {
            case GALLERY_REQUEST_CODE:
                handleImageSelected(data);
                break;
            case REQUEST_IMAGE_CAPTURE:
                handleImageCaptured();
                break;
        }
    }

    private void handleImageSelected(Intent data) {
        Uri selectedImage = data.getData();
        imageView.setImageURI(selectedImage);
        predictImage(selectedImage);
    }

    private void handleImageCaptured() {
        imageView.setImageURI(imageHelper.getPhotoURI());
        imageHelper.addPictureToGallery(getApplicationContext());
        predictImage(imageHelper.getPhotoURI());
    }

    private void predictImage(Uri imageUri) {
        loading(true);

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                    this.getContentResolver(),
                    imageUri
            );
            modelHelper.classify(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onImageClassified(String breed) {
        loading(false);
        loadingSpinner.setVisibility(View.GONE);
        breedTextView.setVisibility(View.VISIBLE);
        String text = "Breed: " + breed;
        breedTextView.setText(text);
    }

    private void loading(Boolean enabled) {
        if (enabled) {
            loadingSpinner.setVisibility(View.VISIBLE);
            importPicture.setEnabled(false);
            takePicture.setEnabled(false);
        } else {
            loadingSpinner.setVisibility(View.GONE);
            importPicture.setEnabled(true);
            takePicture.setEnabled(true);
        }
    }
}
