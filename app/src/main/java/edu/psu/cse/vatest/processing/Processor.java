package edu.psu.cse.vatest.processing;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.opencv.core.Mat;
import org.opencv.objdetect.Objdetect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import edu.psu.cse.vatest.Messager;
import edu.psu.cse.vatest.VANet;
import edu.psu.cse.vatest.VATest;
import edu.psu.cse.vatest.VatestProto;
import edu.psu.cse.vatest.Video;
import edu.psu.cse.vatest.optimization.CellularOnline;
import edu.psu.cse.vatest.optimization.Solution;
import edu.psu.cse.vatest.optimization.WiFiEnergy;
import edu.psu.cse.vatest.optimization.WiFiOffline;

public class Processor {

    public enum OptMode {
        WIFI, WIFI_ENERGY, CELLULAR;
    }

    private CaffeMobile caffe;
    private static int QUEUE_CAPACITY = 30;

    private int speed;
    private OptMode mode;
    private int e_constraint, width, height, procMode;
    private double d_constraint = -1, extractRate;
    private boolean energyMode, dataMode;
    private VANet vanet;
    private ArrayList<Pair<AsyncExtractor, Solution>> procList;
    private ExecutorService sendService;
    private int topK;
    private Semaphore sem;
    private HashMap<String, Video> videoMap;
    private Object remainingSync;
    private int remaining;
    private boolean cellular, current;


    public Processor(VANet vanet, int mode, boolean cellular, boolean energyMode, boolean dataMode,
                     int topK, double extractRate, int width, int height, boolean current) {
        this.vanet = vanet;
        this.energyMode = energyMode;
        this.topK = topK;
        this.sendService = Executors.newSingleThreadExecutor();
        this.sem = new Semaphore(10000);
        this.procMode = mode;
        this.dataMode = dataMode;
        this.extractRate = extractRate;
        this.width = width;
        this.height = height;
        caffe = VATest.getCaffe();
        videoMap = new HashMap<>();
        remainingSync = new Object();
        this.cellular = cellular;
        this.current = current;
    }

    public void setEnergy(int e_constraint) {
        this.e_constraint = e_constraint;
    }

    public void setData(double d_constraint) {
        this.d_constraint = d_constraint;
    }

    public void process(List<Video> videos) {
        for (Video vid : videos) {
            videoMap.put(vid.path, vid);
            remaining++;
        }


        switch (procMode) {
            case 0:
                Log.i("MODE", "Offload videos");
                offloadVideos(videos);
                break;
            case 1:
                Log.i("MODE", "Offload frames");
                offloadFrames(videos);
                break;
            case 2:
                Log.i("MODE", "Local classification");
                localProcess(videos);
                break;
            case 3:
                Log.i("MODE", "Optimized");
                if (!cellular)
                    wifi(videos);
                else
                    cellular(videos);
                break;
        }
    }

    private void offloadVideos(List<Video> videos) {
        try {
            vanet.connect();
        } catch (IOException e) {
            return;
        }
        Receiver receiver = new Receiver();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        for (Video vid : videos) {
            vid.sending = true;
            vid.start();
            vanet.sendVideo(vid, topK);
            vid.stop();
            vid.sending = false;
        }
        try {
            recvThread.join();
        } catch (InterruptedException e) {
            Messager.sendError(e.getStackTrace().toString());
        }
        vanet.disconnect();
        vanet.close();
    }

    private void offloadFrames(List<Video> videos) {
        try {
            vanet.connect();
        } catch (IOException e) {
            return;
        }
        Receiver receiver = new Receiver();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        ExecutorService extractService = Executors.newSingleThreadExecutor();
       // long start = System.currentTimeMillis();
        procList = new ArrayList<>();
        for (Video vid : videos) {
            vid.extracting = true;
            AsyncExtractor asyncExtractor = new AsyncExtractor(new Extractor(vid, width, height, extractRate, sem), vid);
            extractService.submit(asyncExtractor);
            procList.add(new Pair(asyncExtractor, null));
        }
        for (Pair<AsyncExtractor, Solution> proc : procList) {
            BlockingDeque<Mat> extractQueue = proc.first.getExtractQueue();
            Video vid = proc.first.getVideo();
            Extractor extractor = proc.first.getExtractor();
            vid.batches = new Vector<>();
            vid.serverProcessing = true;

            int i = 0;
            // Offload all to server
            vid.sending = true;
            while (true) {
                try {
                    Mat mat = extractQueue.takeFirst();
                    if (mat.empty()) {
                        synchronized (vid.sync) {
                            vid.stop();
                            vid.extracting = false;
                            vid.sending = false;
                            if (!vid.serverProcessing)
                                videoFinished(vid);

                        }
                        break;
                    }
                    synchronized (vid.sync) {
                        vid.batches.add(1);
                        vid.lastServerBatchSent = i;
                    }
                    byte[] arr = Extractor.matToJpg(mat);
                    vanet.sendBatch(vid, 1, i++, topK);
//                    vanet.sendFrameGetStats(vid, arr, i++, current);
                    vanet.sendFrame(vid,arr);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            vanet.disconnect();
            extractService.shutdown();
            extractService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            sendService.shutdown();
            sendService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            recvThread.join();
            vanet.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void localProcess(List<Video> videos) {
        ExecutorService extractService = Executors.newSingleThreadExecutor();
        ExecutorService classifyService = Executors.newSingleThreadExecutor();
        procList = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (Video vid : videos) {
            vid.localProcessing = true;
            AsyncExtractor asyncExtractor = new AsyncExtractor(new Extractor(vid, width, height, extractRate, sem), vid);
            extractService.submit(asyncExtractor);
            procList.add(new Pair(asyncExtractor, null));
        }

        for (Pair<AsyncExtractor, Solution> proc : procList) {
            BlockingDeque<Mat> extractQueue = proc.first.getExtractQueue();
            Video vid = proc.first.getVideo();
            vid.batches = new Vector<>();
            Extractor extractor = proc.first.getExtractor();
            AsyncClassifier classifier = new AsyncClassifier(vid);
            BlockingQueue<Pair<ProcessMessage, ArrayList<Mat>>> classifyQueue = classifier.getQueue();
            classifyService.submit(classifier);

            while (true) {
                try {
                    // Generate list of mats
                    Mat mat = extractQueue.takeFirst();
                    if (mat.empty()) {
                        classifyQueue.put(new Pair(ProcessMessage.FINISH, null));
                        break;
                    }
                    ArrayList<Mat> mats = new ArrayList<>();
                    mats.add(mat);
                    sem.release();
                    // Add to classify queue
                    vid.batches.add(1);
                    classifyQueue.put(new Pair(ProcessMessage.FRAME, mats));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            extractService.shutdown();
            extractService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            classifyService.shutdown();
            classifyService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            classifyService.isTerminated();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void wifi(List<Video> vids) {
        //AbstractOffline offline;
        try {
            vanet.connect();
        } catch (IOException e) {
            return;
        }
        Receiver receiver = new Receiver();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        ExecutorService extractService = Executors.newSingleThreadExecutor();
        ExecutorService classifyService = Executors.newSingleThreadExecutor();
        procList = new ArrayList<>();
//       double speed = vanet.getUplinkSpeed();
//       Log.i("SPEED", speed + "kB/s");

        for (Video vid : vids) {
            double size = 512;
            double speed = 5048;
            /*{
                Extractor extractor = new Extractor(vid, sem);

                Mat first = extractor.extractSingle();
                byte[] jpg = new byte[0];
                try {
                    jpg = Extractor.matToJpg(first);
                    size = (double) jpg.length / 1000;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }*/

            /*// Force garbage collection
            Object obj = new Object();
            WeakReference ref = new WeakReference<Object>(obj);
            obj = null;
            while (ref.get() != null) {
                System.gc();
            }*/


            Solution sol;
            if (energyMode) {
                WiFiEnergy energy = new WiFiEnergy((double) vid.size / 1000, (double) vid.duration / 1000, extractRate, size, speed);
                energy.setEnergyConstraint(e_constraint);
                sol = energy.algorithm();
            } else {
                WiFiOffline offline = new WiFiOffline((double) vid.size / 1000, (double) vid.duration / 1000, extractRate, size, speed);
                sol = offline.algorithm();
            }

            vid.timePredicted = sol.time;

            if (sol.mode == 0) {
                sendService.submit(vanet.newAsyncSender(VANet.UploadTask.VIDEO, vid, topK));
            } else {
                AsyncExtractor asyncExtractor = new AsyncExtractor(new Extractor(vid, width, height, extractRate, sem), vid);
                procList.add(new Pair(asyncExtractor, sol));
            }
        }


        for (Pair<AsyncExtractor, Solution> proc : procList) {
            extractService.submit(proc.first);
        }

        for (Pair<AsyncExtractor, Solution> proc : procList) {
            BlockingDeque<Mat> extractQueue = proc.first.getExtractQueue();
            Video vid = proc.first.getVideo();
            Extractor extractor = proc.first.getExtractor();
            Solution sol = proc.second;
            AsyncClassifier classifier = new AsyncClassifier(vid);
            BlockingQueue<Pair<ProcessMessage, ArrayList<Mat>>> classifyQueue = classifier.getQueue();
            classifyService.submit(classifier);

            /*// Offload all to server if Caffe initialization not finished
            while (!caffe.isInit() && !(extractor.getFinished() && extractQueue.isEmpty())) {
                try {
                    byte[] arr = Extractor.matToJpg(extractQueue.takeFirst());
                    vanet.sendBatch(vid, 1, 0, topK);
                    vanet.sendFrame(vid, arr);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }*/


            int i = 0, batchNum = 0, localFrame=0;;
            while (true) {
                Mat mat;
                try {
                    mat = extractQueue.takeFirst();
                } catch (InterruptedException e) {
                    Messager.sendError(e.getStackTrace().toString());
                    break;
                }
                if (mat.empty()) {
                    synchronized (vid.sync) {
                        vid.stop();
                        vid.extracting = false;
                        if (!vid.serverProcessing && !vid.localProcessing)
                            videoFinished(vid);
                    }
                    break;
                }

                int batch = 0;
                if (sol.batches != null && batchNum < sol.batches.size())
                    batch = sol.batches.get(batchNum);

                if (batch > 0 && extractQueue.size() >= batch -1) {
                    try {
                        // Generate list of mats
                        ArrayList<Mat> mats = new ArrayList<>(batch);
                        mats.add(mat);
                        sem.release();
                        for (int j = 0; j < batch - 1; j++) {
                            mats.add(extractQueue.takeFirst());
                            sem.release();
                        }
                        // Add to classify queue
                        synchronized (vid.sync) {
                            vid.localProcessing = true;
                        }
                        classifyQueue.put(new Pair(ProcessMessage.FRAME, mats));
                        batchNum++;
                        localFrame += batch;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Next batch
                } else {
                    try {
                        synchronized (vid.sync) {
                            vid.serverProcessing = true;
                            vid.lastServerBatchSent = i;
                        }
                        byte[] arr = Extractor.matToJpg(mat);
                        vanet.sendBatch(vid, 1, i++, topK);
                        vanet.sendFrame(vid, arr);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
//                i++;
            }
            try {
                classifyQueue.put(new Pair(ProcessMessage.FINISH, null));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.i("WiFi-Optimization", "offloaded frames: " + i + " local frames: " + localFrame);

        }


        // Close classifier
        try {
            vanet.disconnect();
            extractService.shutdown();
            extractService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            sendService.shutdown();
            sendService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

            classifyService.shutdown();
            classifyService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            recvThread.join();
            vanet.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void cellular(List<Video> vids) {
        try {
            vanet.connect();
        } catch (IOException e) {
            return;
        }
        Receiver receiver = new Receiver();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        ExecutorService extractService = Executors.newSingleThreadExecutor();
        procList = new ArrayList<>();
        double speed = vanet.getUplinkSpeed();
        caffe.startInit();
        BatteryManager batteryManager =
                (BatteryManager) VATest.getContext().getSystemService(Context.BATTERY_SERVICE);

        for (Video vid : vids) {
            double size = 100;
            Extractor extractor = new Extractor(vid, width, height, extractRate, sem);
            AsyncExtractor asyncExtractor = new AsyncExtractor(extractor, vid);
            double startEnergy = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            extractService.submit(asyncExtractor);
            BlockingDeque<Mat> extractQueue = asyncExtractor.getExtractQueue();

            byte[] jpg = new byte[0];
            try {
                Mat first = extractQueue.takeFirst();
                jpg = Extractor.matToJpg(first);
                size = (double) jpg.length / 1000;
                extractQueue.putFirst(first);
            } catch (Exception e) {
                e.printStackTrace();
            }
            CellularOnline online = new CellularOnline((double) vid.duration / 1000, extractRate, size, d_constraint);
            vid.timePredicted = online.optimalPredictionTime();
            int frames = (int) ((vid.duration / 1000) * extractRate);

            int i = 0;
            int rem = frames;
            boolean running = true;
            while (running) {
                Mat mat;
                try {
                    mat = extractQueue.takeFirst();
                } catch (InterruptedException e) {
                    Messager.sendError(e.getStackTrace().toString());
                    break;
                }
                if (mat.empty()) {
                    break;
                }

                if (online.isBelowDataLimit(vid.totalOffload + 1)) {
                    synchronized (vid.sync) {
                        vid.serverProcessing = true;
                        vid.lastServerBatchSent = i;
                    }
                    byte[] arr = new byte[0];
                    try {
                        arr = Extractor.matToJpg(mat);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    vanet.sendBatch(vid, 1, i, topK);
                    Pair stats = vanet.sendFrameGetStats(vid, arr, i, current);
                    i++;
                    rem--;
                    vid.totalOffload++;
                    if (online.isSuccess(stats)) {
                        online.resetBackoff();
                        continue;
                    }
                }

                int num = online.frameToPredict(extractQueue.size(), rem);
                if (num <= 0)
                    continue;

                ArrayList<Mat> mats = new ArrayList<>();
                for (int j = 0; j < num; j++) {
                    try {
                        mat = extractQueue.takeFirst();
                    } catch (InterruptedException e) {
                        Messager.sendError(e.getStackTrace().toString());
                        break;
                    }
                    if (mat.empty()) {
                        running = false;
                        break;
                    }

                    synchronized (vid.sync) {
                        vid.localProcessing = true;
                    }
                    mats.add(mat);
                    rem--;
                }
                if (!running)
                    continue;

                long[] addr = new long[mats.size()];
                for (int j = 0; j < mats.size(); j++) {
                    addr[j] = mats.get(j).getNativeObjAddr();
                }
                int[] top = caffe.predictImageMatArray(addr, topK);
                synchronized (vid.sync) {
                    vid.batchTags.add(top);
                }
                i++;
            }

            synchronized (vid.sync) {
                vid.stop();
                vid.energy = (startEnergy - batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)) * .0036;
                vid.localProcessing = false;
                vid.extracting = false;
                if (!vid.serverProcessing)
                    videoFinished(vid);
            }


        }
        try {
            extractService.shutdown();
            extractService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            sendService.shutdown();
            sendService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            vanet.disconnect();
            recvThread.join();
            vanet.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void videoFinished(Video vid) {
        if (vid.finished)
            return;
        vid.finished = true;
        vid.stop();
        vid.compileTags();
        StringBuilder sb = new StringBuilder(vid.name + ": time=" + vid.getTimeActual() + "s");
        if (vid.timePredicted != -1)
            sb.append(" (predicted=" + vid.timePredicted + "s)");
        if (vid.timePredicted != -1)
            sb.append(", energy=" + vid.energy + "mJ");
        sb.append("\ntotal offload=" + vid.totalOffload);

        sb.append("\nClasses:\n");

        for (int i = 0; i < vid.tags.length; i++) {
            sb.append((i + 1) + ") " + vid.tags[i] + "\n");
        }
        Messager.sendInfo(sb.toString());
    }


    class AsyncExtractor implements Callable<Void> {

        private Video vid;
        private Extractor extractor;
        private BlockingDeque<Mat> extractQueue;

        public AsyncExtractor(Extractor extractor, Video vid) {
            this.vid = vid;
            this.extractor = extractor;
            this.extractQueue = new LinkedBlockingDeque<>();
        }

        @Override
        public Void call() throws Exception {
            try {
                vid.start();
                extractor.doExtract(extractQueue);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public BlockingDeque<Mat> getExtractQueue() {
            return extractQueue;
        }

        public Extractor getExtractor() {
            return extractor;
        }

        public Video getVideo() {
            return vid;
        }
    }

    class AsyncClassifier implements Callable<Void> {
        private Video vid;
        private Vector<Mat> vec;
        private BlockingQueue<Pair<ProcessMessage, ArrayList<Mat>>> queue;

        public AsyncClassifier(Video vid) {
            this.vid = vid;
            vec = new Vector<>();
            this.queue = new LinkedBlockingQueue<>();
        }

        @Override
        public Void call() throws Exception {
            if (!caffe.getInit()) {
                return null;
            }
            Pair<ProcessMessage, ArrayList<Mat>> pair;
            while (true) {
                pair = queue.take();
                if (pair.first == ProcessMessage.FINISH) {
                    synchronized (vid.procSyc) {
                        vid.localProcessing = false;
                        if (!vid.extracting) {
                            if (!vid.serverProcessing)
                                videoFinished(vid);
                            else if (!vid.sending)
                                vid.stop();
                        }
                    }
                    break;
                } else if (pair.first == ProcessMessage.FRAME) {
                    if (!caffe.getInit()) {
                        return null;
                    }
                    ArrayList<Mat> mats = pair.second;
                    long[] arr = new long[mats.size()];
                    for (int i = 0; i < mats.size(); i++) {
                        arr[i] = mats.get(i).getNativeObjAddr();
                    }
                    int[] top = caffe.predictImageMatArray(arr, topK);
                    synchronized (vid.sync) {
                        vid.batchTags.add(top);
                    }
                    /*if (vid.batchTags.size() == vid.totalBatches) {
                        videoFinished(vid);
                    }*/
                }
            }

            //queuedFrames.release(vec.size());
            return null;
        }

        public BlockingQueue<Pair<ProcessMessage, ArrayList<Mat>>> getQueue() {
            return this.queue;
        }

    }

    class Receiver implements Runnable {
        private boolean running = true;

        @Override
        public void run() {
            running = true;
            while (running) {
                VatestProto.ServerMessage sMsg = vanet.receiveMessage();
                if (sMsg == null)
                    continue;
                else if (sMsg.getType() == VatestProto.ServerMessage.Type.DISCONNECT) {
                    return;
                }
                else if (sMsg.getType() == VatestProto.ServerMessage.Type.VIDEO) {
                    Video vid = videoMap.get(sMsg.getPath());
                    vid.serverProcessing = false;
                    int tags[] = new int[sMsg.getTagsCount()];
                    for (int i = 0; i < sMsg.getTagsCount(); i++) {
                        tags[i] = sMsg.getTags(i);
                    }
                    vid.tags = tags;
                    videoFinished(vid);
                    synchronized (remainingSync) {
                        remaining--;
                        if (remaining == 0) {
                            return;
                        }
                    }
                } else if (sMsg.getType() == VatestProto.ServerMessage.Type.BATCH) {
                    Video vid = videoMap.get(sMsg.getPath());
                    synchronized (vid.sync) {
                        int[] arr = new int[sMsg.getTagsCount()];
                        for (int i = 0; i < arr.length; i++)
                            arr[i] = sMsg.getTags(i);
                        vid.batchTags.add(arr);
                        vid.lastServerBatchReceived = sMsg.getBatch();

                        if (!vid.extracting && vid.lastServerBatchSent == vid.lastServerBatchReceived) {
                            vid.serverProcessing = false;
                            if (!vid.localProcessing) {
                                videoFinished(vid);
                                synchronized (remainingSync) {
                                    remaining--;
                                    if (remaining == 0) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
//                else if (sMsg.getType() == VatestProto.ServerMessage.Type.RECEIVED) {
//                    vanet.notifyResponse(sMsg.getBatch());
//                    int i = 0;
//                    i++;
//                }
            }
        }
    }

}

enum ProcessMessage {
    FRAME,
    FINISH
}


