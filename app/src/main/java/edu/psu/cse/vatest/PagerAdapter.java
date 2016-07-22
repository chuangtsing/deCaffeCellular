package edu.psu.cse.vatest;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * Created by zblas_000 on 8/11/2015.
 */
public class PagerAdapter extends FragmentPagerAdapter {
    private int tabs;
    private MainActivity activity;
    private ProcessFragment processFragment;
    private SettingsFragment settingsFragment;

    public PagerAdapter(android.support.v4.app.FragmentManager fm, int tabs, MainActivity activity) {
        super(fm);
        this.tabs = tabs;
        this.activity = activity;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return (processFragment = ProcessFragment.newInstance(activity, this));
            case 1:
                return (settingsFragment = SettingsFragment.newInstance(activity));
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        return tabs;
    }

    public ProcessFragment getProcessFragment() { return processFragment; }
    public SettingsFragment getSettingsFragment() {return settingsFragment; }
}
