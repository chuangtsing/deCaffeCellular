package edu.psu.cse.vatest.processing;

import android.annotation.SuppressLint;
import android.os.FileObserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import edu.psu.cse.vatest.CircularStringBuffer;
import edu.psu.cse.vatest.Utils;
import edu.psu.cse.vatest.VATest;
import edu.psu.cse.vatest.Video;

/**
 * Created by Zack on 7/13/15.
 */
public class Ffmpeg {
    private static String OLD_BIN_FOLDER_PATH = "/data/data/edu.psu.cse.vatest/assets/";
    public static String BIN_FOLDER_PATH = "/data/data/edu.psu.cse.vatest/bin/";
    private static boolean initialized = false;

    public static void init() {
        if (initialized)
            return;

        File file = new File(BIN_FOLDER_PATH);
        if (!file.exists())
            file.mkdirs();


        File f = new File(BIN_FOLDER_PATH + "ffmpeg");
        if (!f.exists()) {
            copyFile("ffmpeg", BIN_FOLDER_PATH
                    + "ffmpeg");
            try {
                Runtime.getRuntime().exec("chmod" + " 0700 " + BIN_FOLDER_PATH
                        + "ffmpeg");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }// copy needed files to the folder


        initialized = true;
    }

    @SuppressLint("ShowToast")
    private static void copyFile(String oldPath, String newPath) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            InputStream inStream = VATest.getContext().getResources().getAssets().open(oldPath); // input
            // xml
            // in
            // assests
            // folder
            FileOutputStream fs = new FileOutputStream(newPath);
            byte[] buffer = new byte[1444];
            while ((byteread = inStream.read(buffer)) != -1) {
                bytesum += byteread; // file size
                fs.write(buffer, 0, byteread);
            }
            inStream.close();
            fs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getInfo(Video vid) {
        init();
        String output = "";
        try {
            String command = BIN_FOLDER_PATH + "ffmpeg -i " + vid.path + " 2>&1";
            Process process = Runtime.getRuntime().exec(command);

            // we must empty the output and error stream to end the process
            EmptyStreamThread emptyErrorStreamThread = new EmptyErrorStreamThread(
                    process.getErrorStream());
            emptyErrorStreamThread.start();

            // if (process.waitFor() == 0) {
            // System.out.println("Successfully executed: " + command);
            emptyErrorStreamThread.join();
            output = emptyErrorStreamThread.getOutput(); // DEBUG
            // output = emptyInputStreamThread.getOutput();
            // } else {
            // System.err.println(emptyErrorStreamThread.getOutput());
            // System.err.println(emptyInputStreamThread.getOutput());
            // }

            // close streams
            process.getErrorStream().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public static boolean extract(final Video vid, final String path) {
        init();
        Thread listener = new Thread() {
            public Object extrLock;
            @Override
            public void run() {
                FileObserver observer = new FileObserver(path) { // set up a file observer to watch this directory on sd card
                    int i = 0;
                    @Override
                    public void onEvent(int event, String file) {
                        if (event == FileObserver.CREATE && i < vid.totalFrames) {
                            i++;
                            //Notifier.updateProcess(Notifier.Location.LOCAL, VideoProcess.ProcessState.EXTRACTING, i);
                        }
                    }

                };
                observer.startWatching();
                synchronized (this) {
                    while(!isInterrupted()) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            observer.stopWatching();
                            return;
                        }
                    }
                }
                observer.stopWatching();
            }
        };

        Utils.buildDir(path);
        listener.start();
        String command = BIN_FOLDER_PATH + "ffmpeg -i " + vid.path + " -vf fps=1 "
                + path + "frame_%d.jpg";
        try {
            Process process = Runtime.getRuntime().exec(command);
            boolean ret = process.waitFor() == 0;
            synchronized (listener) {
                listener.interrupt();
                listener.notify();
            }
            return ret;
        } catch (Exception e) {
            return false;
        }
    }

    public static void cleanup(String dir) {
        File file = new File(dir);
        for (File f : file.listFiles())
            f.delete();
        file.delete();
    }

    private static abstract class EmptyStreamThread extends Thread {

        private InputStream istream = null;
        private CircularStringBuffer buff = new CircularStringBuffer();

        public EmptyStreamThread(InputStream istream) {
            this.istream = istream;
        }

        public String getOutput() {
            return buff.toString().trim();
        }

        protected abstract void handleLine(String line);

        public void run() {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(istream));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    istream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class EmptyErrorStreamThread extends EmptyStreamThread {

        public EmptyErrorStreamThread(InputStream istream) {
            super(istream);
        }

        protected void handleLine(String line) {
            super.buff.append(line).append("\n");
            // Log.d(TAG, "ERROR: " + line); // DEBUG
        }
    }
}
