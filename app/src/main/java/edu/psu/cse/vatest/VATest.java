package edu.psu.cse.vatest;

import android.app.Application;
import android.content.Context;

import edu.psu.cse.vatest.processing.CaffeMobile;


public class VATest extends Application {
    private static VATest instance;
    private static CaffeMobile caffe = new CaffeMobile();

    public static VATest getInstance() {
        return instance;
    }

    public static Context getContext(){
        return instance.getApplicationContext();
    }

    public static CaffeMobile getCaffe() {
        return caffe;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}