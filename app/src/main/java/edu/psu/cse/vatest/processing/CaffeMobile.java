package edu.psu.cse.vatest.processing;

import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import edu.psu.cse.vatest.Messager;
import edu.psu.cse.vatest.Utils;
import edu.psu.cse.vatest.VATest;

public class CaffeMobile {
    private static final ArrayList<String> classes = new ArrayList<String>();
    private static boolean initialized = false;
    private ExecutorService extractService, classifyService;

    public static final String RES_PATH = "/storage/emulated/0/vatest/models/";
    public static final String TMP_PATH = "/storage/emulated/0/vatest/tmp/";
    public static final String MODEL_NAME = "model.prototxt";
    public static final String WEIGHTS_NAME = "weights.caffemodel";
    public static final String MEAN_NAME = "mean.binaryproto";
    public static final String SYNSET_NAME = "synset.txt";
    public static final String VIDEOS_DIR = "/storage/emulated/0/DCIM/Camera/";
    public static final String MODEL_PATH = RES_PATH + MODEL_NAME;
    public static final String WEIGHTS_PATH = RES_PATH + WEIGHTS_NAME;
    public static final String MEAN_PATH = RES_PATH + MEAN_NAME;
    public static final String SYNSET_PATH = RES_PATH + SYNSET_NAME;
    private static final String EXTRACT_PATH = "/storage/emulated/0/vatest/extracted/";
    private static final double db_fps = 1.0;
    private static final int kResults = 13;

    private Initializer initializer = new Initializer(new InitCallable());
    private Semaphore queuedFrames = new Semaphore(100, true);

    public void startInit() {

        if (initialized == true)
            return;

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.submit(initializer);
    }

    public boolean getInit() {
        if (initializer == null)
            return false;
        if (initialized == true)
            return true;
        try {
            return initializer.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isInit() {
        return initialized;
    }


    public native void enableLog(boolean enabled);
    public native int loadModel(String modelPath, String weightsPath);
    public native int[] predictImage(String imgPath, int topk);
    public native int[] predictImageMat(long img, int topk);
    public native int[] predictImageMatArray(long[] imgArray, int topk);

    private class Initializer extends FutureTask<Boolean> {

        public Initializer(Callable<Boolean> callable) {
            super(callable);
        }

        @Override
        protected void done() {
            try {
                initialized = get();
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            initialized = false;
        }
    }


    private class InitCallable implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            try {
                System.loadLibrary("caffe");
                System.loadLibrary("caffe_jni");
            } catch (Exception e) {
                Log.e(Utils.TAG, "init failed: could not load caffe mobile libraries");
                return false;
            }


            File model = new File(MODEL_PATH);
            File weights = new File(WEIGHTS_PATH);
            if (model.exists() && weights.exists())
                loadModel(MODEL_PATH, WEIGHTS_PATH);
            else {
                Log.e(Utils.TAG, "init failed: data files not found");
                Messager.showToast("Caffe init files not found", Toast.LENGTH_SHORT);
                return false;
            }

            try {
                InputStream is = new FileInputStream(SYNSET_PATH);
                Scanner sc = new Scanner(is);
                while (sc.hasNextLine()) {
                    final String temp = sc.nextLine();
                    classes.add(temp.substring(temp.indexOf(" ") + 1));
                }
                if (classes.size() == 0)
                    throw new IOException("Synset file of size 0");
            } catch (IOException e) {
                Log.e(Utils.TAG, "init failed: failed to read synset file");
                return false;
            }
            Messager.showToast("Caffe initialized successfully", Toast.LENGTH_SHORT);
            return true;
        }
    }

    public void waitAll() {
        if (extractService != null) {
            extractService.shutdown();
            try {
                extractService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            extractService = null;
        }

        if (classifyService != null) {
            classifyService.shutdown();
            try {
                classifyService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            classifyService = null;
        }
    }



}