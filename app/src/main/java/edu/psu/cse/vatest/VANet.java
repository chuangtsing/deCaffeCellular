package edu.psu.cse.vatest;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.CodedInputStream;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import edu.psu.cse.vatest.processing.CaffeMobile;

public class VANet {
    private Socket socket;
    private boolean connected;
    private static final int BUFFER_SIZE = 65536;
    private static final int TEST_SIZE = 100000;
    private Context context;
    private CaffeMobile caffe;
    private ExecutorService sendService;
    private ExecutorService extractService;
    private ExecutorService classifyService;
    private ConnectivityManager connMgr;

    private static String local_addr = "192.168.1.200";
    private static String ip_addr = "130.203.8.20";
    private static int default_port = 50000;
    private BlockingQueue<Object> responseWaitQueue;
    private Set<Integer> responses;

    public enum UploadTask {
        VIDEO, FRAME;
    }

    public VANet(Context context) {
        this.context = context;
        connMgr = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        responseWaitQueue = new LinkedBlockingQueue<>();
        responses = new HashSet<>();
    }

    public void connect() throws IOException {
        String addr = getNetworkType() == ConnectivityManager.TYPE_WIFI ? local_addr : ip_addr;
        socket = new Socket(addr, default_port);
        VatestProto.ClientMessage.Builder builder = VatestProto.ClientMessage.newBuilder();
        builder.setType(VatestProto.ClientMessage.Type.CONNECT);
        connected = true;
        sendMessage(builder);
        VatestProto.ServerMessage msg = receiveMessage();
        if (msg == null || msg.getType() != VatestProto.ServerMessage.Type.CONNECT) {
            connected = false;
            throw new IOException("Error connecting to server");
        }
    }

    public void connect(String addr, int port) throws IOException {
        socket = new Socket(addr, port);
        VatestProto.ClientMessage.Builder builder = VatestProto.ClientMessage.newBuilder();
        builder.setType(VatestProto.ClientMessage.Type.CONNECT);
        connected = true;
        sendMessage(builder);
        VatestProto.ServerMessage msg = receiveMessage();
        if (msg == null || msg.getType() != VatestProto.ServerMessage.Type.CONNECT) {
            connected = false;
            throw new IOException("Error connecting to server");
        }
    }

    public void disconnect() {
        if (!connected)
            return;
        VatestProto.ClientMessage.Builder builder = VatestProto.ClientMessage.newBuilder()
                .setType(VatestProto.ClientMessage.Type.DISCONNECT);
        sendMessage(builder);

    }

    public void close() {
        if (socket.isClosed())
            return;
        connected = false;
        try {
            socket.close();
        } catch (IOException e) {
            Messager.sendError(e.getStackTrace().toString());
        }
    }

    public void initCaffe() {
        if (caffe == null) {
            caffe = new CaffeMobile();
        }
        caffe.startInit();

    }


    public VatestProto.ServerMessage receiveMessage() {
        if (!connected)
            return null;
        try {

            InputStream in = socket.getInputStream();
            CodedInputStream coded = CodedInputStream.newInstance(socket.getInputStream());
            return VatestProto.ServerMessage.parseDelimitedFrom(in);
            //size = in.read(bite, 0, 30);
            /*int size = coded.readRawVarint32();
            int read = 0, total = 0;
            byte[] bites = new byte[size];
            int avail = in.available();
            while (total < size && (read = in.read(bites, 0, size)) != 0)
                total += read;
            return VatestProto.ServerMessage.parseFrom(bites);*/
        } catch (SocketTimeoutException e) {
            Log.e(Utils.TAG, "server connection timeout");
            connected = false;
        } catch (IOException e) {
            Log.e(Utils.TAG, e.getMessage());
            connected = false;
        }
        /*size = size;
        bite = bite;
        VatestProto.ServerMessage msg = null;
        byte[] arr = Arrays.copyOfRange(bite, 4, size);
        try {
            msg = VatestProto.ServerMessage.parseFrom(arr);
        }
        catch (InvalidProtocolBufferException e){
        }
        return msg;*/
        return null;
    }

    public void sendMessage(VatestProto.ClientMessage.Builder builder) {
        if (!connected)
            return;
        WifiManager wifiMan = (WifiManager) context.getSystemService(
                Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMan.getConnectionInfo();
        if (wifiInfo == null)
            return;
        VatestProto.ClientMessage msg = builder.build();

        try {
            //CodedOutputStream out = CodedOutputStream.newInstance(socket.getOutputStream());
            //out.writeRawVarint32(msg.getSerializedSize());
            //msg.writeTo(out);
            msg.writeDelimitedTo(socket.getOutputStream());
            //out.flush();
        } catch (SocketTimeoutException e) {
            Log.e(Utils.TAG, "server connection timeout");
            connected = false;
        } catch (IOException e) {
            Log.e(Utils.TAG, "error sending message");
            connected = false;
        }
    }

    public void sendVideo(Video vid, int topK) {
        if (!connected)
            return;

        VatestProto.ClientMessage.Builder msg = VatestProto.ClientMessage.newBuilder()
                .setType(VatestProto.ClientMessage.Type.VIDEO)
                .addSize(vid.size)
                .setPath(vid.path)
                .setTopK(topK);
        sendMessage(msg);
        long total = 0;
        int percent = 0;
        try {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            FileInputStream file = new FileInputStream(vid.path);
            byte[] buf = new byte[BUFFER_SIZE];

            int bytesRead = 0;
            while (-1 != (bytesRead = file.read(buf, 0, buf.length))) {
                out.write(buf, 0, bytesRead);
                total += bytesRead;
                int newPercent = (int) (total * 100 / vid.size);
                if (newPercent - percent >= 10) {
                    percent = newPercent;
                    //Notifier.updateProcess(Notifier.Location.SERVER, VideoProcess.ProcessState.UPLOADING, percent);
                }
            }
            file.close();
            out.flush();
        } catch (IOException e) {
            Log.e(Utils.TAG, "error sending video");
            connected = false;
        }
    }

    public void sendBatch(Video vid, int size, int batchNumber, int topK) {
        VatestProto.ClientMessage.Builder builder = VatestProto.ClientMessage.newBuilder();
        builder.setType(VatestProto.ClientMessage.Type.BATCH)
                .setPath(vid.path)
                .setBatch(batchNumber)
                .addSize(size)
                .setTopK(topK);
        sendMessage(builder);
    }

    public void sendFrame(Video vid, byte[] arr) {
        if (!connected)
            return;

        VatestProto.ClientMessage.Builder builder = VatestProto.ClientMessage.newBuilder()
                .setType(VatestProto.ClientMessage.Type.FRAME)
                .setPath(vid.path)
                .addSize(arr.length);
        sendMessage(builder);

        try {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            out.write(arr);
            out.flush();
        } catch (IOException e) {
            Log.e(Utils.TAG, "error sending frame");
            connected = false;
        }
    }

    // return pair: time (ms), energy (mW)
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Pair<Double, Double> sendFrameGetStats(Video vid, byte[] arr, int batchNumber, boolean current) {
        if (!connected)
            return new Pair(-1., -1.);
        ;
        Context context = VATest.getContext();
        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        VatestProto.ClientMessage.Builder builder = VatestProto.ClientMessage.newBuilder()
                .setType(VatestProto.ClientMessage.Type.FRAME)
                .setPath(vid.path)
                .addSize(arr.length)
                .setReceivedResponse(true);
        sendMessage(builder);

        double start = System.currentTimeMillis();
        double energyEnd = 0, energyStart = 0;
        if (!current) {
            energyStart = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
        }
        try {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            out.write(arr);
            out.flush();
        } catch (IOException e) {
            Log.e(Utils.TAG, "error sending frame");
            connected = false;
        }
        double currentValue = 0;
        if (current) {
            currentValue = (double) -batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000.;
        } else {
            energyEnd = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
        }

        Object wait = new Object();

        while (!responses.contains(batchNumber)) {
            try {
                synchronized (wait) {
                    if (!responseWaitQueue.contains(wait))
                        responseWaitQueue.put(wait);
                    wait.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        synchronized (responses) {
            responses.remove(batchNumber);
        }

        double end = System.currentTimeMillis();
        double time = end - start;
        double energy = current ? (time / 1000) * (currentValue /*mA*/ * 3.85 /*V*/) : (energyStart - energyEnd) * .0036;
        return new Pair(time, energy);
    }

    public void notifyResponse(int batch) {
        synchronized (responses) {
            responses.add(batch);
        }
        Object obj;
        try {
            obj = responseWaitQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
        synchronized (obj) {
            obj.notifyAll();
        }
    }


    public int getNetworkType() {
        NetworkInfo info = connMgr.getActiveNetworkInfo();
        return info.getType();
    }

    // Uplink speed in kB/s
    public double getUplinkSpeed() {
        byte[] data = new byte[TEST_SIZE];

        VatestProto.ClientMessage.Builder msg = VatestProto.ClientMessage.newBuilder()
                .setType(VatestProto.ClientMessage.Type.UPLINK_TEST)
                .addSize(data.length);
        sendMessage(msg);
        try {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            long tStart = System.nanoTime();
            out.write(data, 0, data.length);
            out.flush();
            long tEnd = System.nanoTime();
            long tDelta = tEnd - tStart;
            double elapsedSeconds = tDelta / 1000000000.;
            return (double) (TEST_SIZE / 1000) / elapsedSeconds;
        } catch (IOException e) {
            Log.e(Utils.TAG, "error testing uplink speed");
            connected = false;
            return -1;

        }
    }

    public AsyncSender newAsyncSender(UploadTask task, Video vid, int topK) {
        return new AsyncSender(task, vid, topK);
    }

    public AsyncSender newAsyncSender(Video vid, byte[] jpg, int topK) {
        return new AsyncSender(vid, jpg, topK);
    }


    public class AsyncSender implements Callable<Void> {
        private Video vid;
        private UploadTask task;
        private int[] tags;
        private int batchSize;
        private byte[] jpg;
        private int topK;

        public AsyncSender(UploadTask task, Video vid, int topK) {
            this.task = task;
            this.vid = vid;
            this.topK = topK;
        }

        public AsyncSender(Video vid, byte[] jpg, int topK) {
            this.vid = vid;
            this.jpg = jpg;
            this.task = UploadTask.FRAME;
            this.topK = topK;
        }

        @Override
        public Void call() throws Exception {
            if (!connected)
                return null;
            synchronized (vid.sync) {
                vid.sending = true;  // Possible race condition
            }
            if (task == UploadTask.VIDEO) {
                vid.start();
                sendVideo(vid, topK);
                vid.stop();
            } else if (task == UploadTask.FRAME) {
                sendFrame(vid, jpg);
            }
            synchronized (vid.sync) {
                vid.sending = false;
            }
            return null;
        }
    }
}