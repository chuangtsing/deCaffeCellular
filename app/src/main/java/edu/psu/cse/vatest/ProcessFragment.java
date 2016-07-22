package edu.psu.cse.vatest;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import edu.psu.cse.vatest.processing.ProcessTask;

public class ProcessFragment extends Fragment {

    private Handler handler;
    private Button btnProcess, btnProcessSingle, btnClear;
    private ProgressBar progressBar;
    private TextView tvOut;
    private MainActivity activity;
    private PagerAdapter adapter;

    public static ProcessFragment newInstance(MainActivity activity, PagerAdapter adapter) {
        ProcessFragment fragment = new ProcessFragment();
        fragment.activity = activity;
        fragment.adapter = adapter;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        Messager.unregisterHandler();
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_process, container, false);
        tvOut = (TextView) view.findViewById(R.id.tvOut);
        btnProcess = (Button) view.findViewById(R.id.btnProcess);
        btnProcessSingle = (Button) view.findViewById(R.id.btnProcessSingle);
        btnClear = (Button) view.findViewById(R.id.btnClear);
        progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        handler = new Handler(new HandlerCallback());
        Messager.registerHandler(handler);
        tvOut.setMovementMethod(new ScrollingMovementMethod());

        btnProcess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressBar.setVisibility(View.VISIBLE);
                ProcessTask task = new ProcessTask(adapter.getSettingsFragment(), null);
                task.execute();
            }
        });

        btnProcessSingle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new FileChooser(activity, activity.getSharedPreferences(getString(R.string.package_name), Context.MODE_PRIVATE).getString("VIDEO_PATH", "/sdcard/DCIM/Camera"))
                        .setFileListener(new FileChooser.FileSelectedListener() {
                            @Override
                            public void fileSelected(File file) {
                                SharedPreferences.Editor editor = activity.getSharedPreferences(getString(R.string.package_name), Context.MODE_PRIVATE).edit();
                                editor.putString("VIDEO_PATH", file.getAbsolutePath());
                                editor.commit();
                                ProcessTask task = new ProcessTask(adapter.getSettingsFragment(), file.getAbsolutePath());
                                task.execute();
                            }
                        }).showDialog();
                progressBar.setVisibility(View.VISIBLE);
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvOut.setText("");
            }
        });

        return view;
    }


    class HandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.arg1 == 0) {
                String text = (String) msg.obj;
                tvOut.setTextColor(msg.arg1);
                tvOut.append(text);
            }
            else {
                progressBar.setVisibility(View.INVISIBLE);
                tvOut.setTextColor(Messager.COLOR_GREEN);
                tvOut.append("FINISHED\n");
            }
            return true;
        }
    }
}
