package edu.psu.cse.vatest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;

public class MainActivity extends AppCompatActivity implements DirectoryChooserFragment.OnFragmentInteractionListener  {
    private DirectoryChooserFragment mDialog;
    private SharedPreferences prefs;
    private PagerAdapter pagerAdapter;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        prefs = getSharedPreferences(getString(R.string.package_name), Context.MODE_PRIVATE);
        handler = new Handler(new HandlerCallback());
        Messager.registerActivityHandler(handler);
        VATest.getCaffe().startInit();

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("Processes"));
        tabLayout.addTab(tabLayout.newTab().setText("Settings"));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        pagerAdapter = new PagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount(), this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }
    @Override
    protected void onDestroy() {
        Messager.unregisterActivityHandler();
        super.onDestroy();
    }

    protected void chooseDirectory() {
        mDialog = DirectoryChooserFragment.newInstance("Select video directory", prefs.getString(getString(R.string.pref_directory), null));
        mDialog.show(getFragmentManager(), "VATest");
    }

    @Override
    public void onSelectDirectory(String path) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(getString(R.string.pref_directory), path);
        editor.commit();
        pagerAdapter.getSettingsFragment().getTxtDir().setText(path);
        mDialog.dismiss();
    }

    @Override
    public void onCancelChooser() {
        mDialog.dismiss();
    }


    class HandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            Toast.makeText(VATest.getContext(), (String) msg.obj, msg.arg2).show();
            return true;
        }
    }

}
