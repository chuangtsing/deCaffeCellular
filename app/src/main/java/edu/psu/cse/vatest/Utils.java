package edu.psu.cse.vatest;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

/**
 * Created by Zack on 7/17/15.
 */
public class Utils {
    public static String TAG = "VATest";

    public static boolean buildDir(String path) {
        if (new File(path).exists())
            return true;
        String[] dirs = path.split("/");
        String dir = "";

        for (String str : dirs) {
            dir += "/" + str;
            File f = new File(dir);
            if (!f.exists()) {
                if (!f.mkdir())
                    return false;
            }
        }

        return true;
    }

    public static ArrayList<Video> prepareAllVideos() {
        Context context = VATest.getContext();
        ArrayList<Video> vids = new ArrayList<Video>();
        SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.package_name), Context.MODE_PRIVATE);
        String directory = prefs.getString(context.getString(R.string.pref_directory), null);
        if (directory == null)
            return vids;
        //path = CaffeMobile.VIDEOS_DIR;
        File dir = new File(directory);
        for (File vid : dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                String ext = name.substring(name.lastIndexOf('.') + 1);
                return (ext.equals("3gp") || ext.equals("mp4"));
            }
        })) {
            vids.add(new Video(vid.getPath()));
        }
        return vids;
    }
}
