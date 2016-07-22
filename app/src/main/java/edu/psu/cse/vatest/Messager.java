package edu.psu.cse.vatest;

import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

/**
 * Created by Zack on 1/25/16.
 */
public class Messager {
    private static Handler handler;
    private static Handler activityHandler;

    public static final int COLOR_RED = 0xFF990000;
    public static final int COLOR_GREEN = 0xFF004D00;

    protected static void registerHandler(Handler handler) {
        Messager.handler = handler;
    }

    protected static void registerActivityHandler(Handler handler) {
        Messager.activityHandler = handler;
    }

    public static void sendInfo(String text) {
        if (handler == null)
            return;

        Message msg = handler.obtainMessage();
        msg.arg1 = 0;
        msg.arg2 = Color.BLACK;
        msg.obj = text + '\n';
        handler.sendMessage(msg);
    }

    public static void sendError(String text) {
        if (handler == null)
            return;

        Message msg = handler.obtainMessage();
        msg.arg1 = 0;
        msg.arg2 = COLOR_RED;
        msg.obj = text + '\n';
        handler.sendMessage(msg);
    }

    public static void processEnd() {
        Message msg = handler.obtainMessage();
        msg.arg1 = 1;
        msg.arg2 = Color.BLACK;
        handler.sendMessage(msg);
    }

    public static void showToast(String text, int duration) {
        if (activityHandler == null)
            return;

        Message msg = activityHandler.obtainMessage();
        msg.arg1 = 0;
        msg.arg2 = duration;
        msg.obj = text;
        activityHandler.sendMessage(msg);
    }

    protected static void unregisterHandler() {
        handler = null;
    }

    protected static void unregisterActivityHandler() {
        activityHandler = null;
    }
}
