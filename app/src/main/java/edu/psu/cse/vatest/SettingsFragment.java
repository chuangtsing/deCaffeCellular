package edu.psu.cse.vatest;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;


/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment extends Fragment {
    private MainActivity activity;
    private TextView txtDir;
    private Button btnSelect;
    private DirectoryChooserFragment mDialog;
    private SharedPreferences prefs;

    private EditText txtExtract, txtK, txtWidth, txtHeight, txtEnergy, txtData;
    private Switch switchEnergy, switchData, switchCurrent;
    private Spinner spinnerMode;
    private RadioButton radioCellular;

    public static SettingsFragment newInstance(MainActivity activity) {
        SettingsFragment fragment = new SettingsFragment();
        fragment.activity = activity;
        return fragment;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = activity.getSharedPreferences(getString(R.string.package_name), Context.MODE_PRIVATE);
        btnSelect = (Button) view.findViewById(R.id.btnSelect);
        txtDir = (TextView) view.findViewById(R.id.txtDir);
        txtDir.setText(prefs.getString(getString(R.string.pref_directory), "No directory selected"));


        txtExtract = (EditText) view.findViewById(R.id.txtExtract);
        txtK = (EditText) view.findViewById(R.id.txtK);
        txtWidth = (EditText) view.findViewById(R.id.txtWidth);
        txtHeight = (EditText) view.findViewById(R.id.txtHeight);
        txtEnergy = (EditText) view.findViewById(R.id.txtEnergy);
        txtData = (EditText) view.findViewById(R.id.txtData);

        switchEnergy = (Switch) view.findViewById(R.id.switchEnergy);
        switchData = (Switch) view.findViewById(R.id.switchData);

        spinnerMode = (Spinner) view.findViewById(R.id.spinnerType);

        radioCellular = (RadioButton) view.findViewById(R.id.radioCell);
        switchCurrent = (Switch) view.findViewById(R.id.switchCurrent);


        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activity.chooseDirectory();
            }
        });
        
        return view;
    }

    protected TextView getTxtDir() { return txtDir; }

    public int getTopK() {
        return Integer.parseInt(txtK.getText().toString());
    }

    public int getMode() {
        return spinnerMode.getSelectedItemPosition();
    }

    public double getExtractRate() {
        return Double.parseDouble(txtExtract.getText().toString());
    }

    public boolean getEnergyMode() {
        return switchEnergy.isChecked();
    }

    public int getEnergyConstraint() {
        return Integer.parseInt(txtEnergy.getText().toString());
    }

    public boolean getDataMode() {
        return switchData.isChecked();
    }

    public double getDataConstraint() {
        return Double.parseDouble(txtData.getText().toString());
    }

    public int getWidth() {
        return Integer.parseInt(txtWidth.getText().toString());    }

    public int getHeight() {
        return Integer.parseInt(txtHeight.getText().toString());
    }

    public boolean getCellular() {
        return radioCellular.isChecked();
    }

    public boolean getCurrent() {
        return switchCurrent.isChecked();
    }

}
