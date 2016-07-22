package edu.psu.cse.vatest.processing;

import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import edu.psu.cse.vatest.Messager;
import edu.psu.cse.vatest.SettingsFragment;
import edu.psu.cse.vatest.Utils;
import edu.psu.cse.vatest.VANet;
import edu.psu.cse.vatest.VATest;
import edu.psu.cse.vatest.Video;

/**
 * Created by Zack on 11/28/15.
 */
public class ProcessTask extends AsyncTask<Void, Double, Void> {
    private boolean energyMode, dataMode;
    private int energyConstraint;
    private double dataConstraint;
    private int topK;
    private double extractRate;
    private int width, height, mode;
    private String vidPath;
    private boolean cellular, current;

    private VANet vanet;
    private SettingsFragment settingsFragment;

    public ProcessTask(SettingsFragment settingsFragment, String vidPath) {
        this.settingsFragment = settingsFragment;
        vanet = new VANet(VATest.getContext());
        this.vidPath = vidPath;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        //vanet.init();
        ArrayList<Video> vids;
        if (vidPath != null) {
            vids = new ArrayList<>();
            vids.add(new Video(vidPath));
        }
        else {
            vids = Utils.prepareAllVideos();
        }
        Processor proc = new Processor(vanet, mode, cellular, energyMode, dataMode, topK, extractRate, width, height, current);
        if (energyMode)
            proc.setEnergy(energyConstraint);
        if (dataMode)
            proc.setData(dataConstraint);
        proc.process(vids);
        /*Extractor ext = new Extractor(vids.get(0), new Semaphore(10000));
        try {
            ext.doExtract(new LinkedBlockingDeque<Mat>());
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        for (Video vid : vids) {
            Log.i("RESULT", vid.name + "pred: " + vid.timePredicted + ", actual:" + vid.getTimeActual() + " " + vid.getStart() + " " + vid.getStop());
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        energyMode = settingsFragment.getEnergyMode();
        energyConstraint = settingsFragment.getEnergyConstraint();
        topK = settingsFragment.getTopK();
        extractRate = settingsFragment.getExtractRate();
        dataConstraint = settingsFragment.getDataConstraint();
        width = settingsFragment.getWidth();
        height = settingsFragment.getHeight();
        mode = settingsFragment.getMode();
        dataMode = settingsFragment.getDataMode();
        cellular = settingsFragment.getCellular();
        current = settingsFragment.getCurrent();
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        Messager.processEnd();
    }
}
