package com.avaucamps.whatsmydog;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Vector;

public class ModelHelper {
    private static final String MODEL_PATH = "mobilenetv2_model.tflite";
    private static final String LABELS_FILENAME =  "labels.txt";

    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;

    private ImageClassification imageClassification;
    private Interpreter tflite;
    private float[][][][] imgData = new float[DIM_BATCH_SIZE][DIM_IMG_SIZE_Y][DIM_IMG_SIZE_X][DIM_PIXEL_SIZE];
    private Vector<String> labels = new Vector<String>();
    private AssetManager assetManager;
    private float[][] labelProb = null;
    private int[] intValues;

    public ModelHelper(ImageClassification imageClassification, AssetManager assetManager) {
        this.imageClassification = imageClassification;
        this.assetManager = assetManager;
        loadModel();
        loadLabels();
        intValues = new int[224 * 224];
    }

    /**
     * Classifies the image(bitmap) passed in parameter with the model and calls "onImageClassified"
     * with predicted breed when done.
     * @param bitmap: bitmap representing the image to classify.
     */
    public void classify(Bitmap bitmap) {
        preprocess(bitmap);
        runInference();
        postprocess();
    }

    private void loadModel() {
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void loadLabels() {
        BufferedReader br = null;

        try {
            br = new BufferedReader(
                    new InputStreamReader(assetManager.open(LABELS_FILENAME))
            );
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!" , e);
        }

        labelProb = new float[1][labels.size()];
    }

    private void runInference() {
        tflite.run(imgData, labelProb);
    }

    private void preprocess(Bitmap bitmap) {
        if (imgData == null || bitmap == null) {
            return;
        }

        bitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, false);
        bitmap.getPixels(
                intValues,
                0, bitmap.getWidth(),
                0, 0,
                bitmap.getWidth(),
                bitmap.getHeight()
        );

        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData[0][i][j][0] = (float) ((val >> 16) & 0xFF);
                imgData[0][i][j][1] = (float) ((val >> 8) & 0xFF);
                imgData[0][i][j][2] = (float) (val  & 0xFF);
            }
        }
    }

    private void postprocess() {
        float best_score = 0;
        int best_index = 0;

        for (int i = 0; i < labelProb[0].length; i++) {
            float score = labelProb[0][i];
            if (score > best_score) {
                best_score = score;
                best_index = i;
            }
        }

        if (best_score < 0.01) {
            imageClassification.onImageClassified("Not recognized.");
            return;
        }

        String label = labels.get(best_index).replace("_", " ");
        imageClassification.onImageClassified(label);
    }
}
