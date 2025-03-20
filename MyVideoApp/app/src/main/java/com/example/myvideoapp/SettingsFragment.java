package com.example.myvideoapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

public class SettingsFragment extends Fragment {
    private MainFragment mainFragment;
    private boolean isInitialized = false;
    private AdapterView.OnItemSelectedListener resolutionListener;
    private AdapterView.OnItemSelectedListener intervalListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mainFragment = (MainFragment) requireActivity().getSupportFragmentManager().findFragmentByTag(MainFragment.class.getName());
        setupResolutionSpinner(view);
        setupIntervalSpinner(view);
        loadConfigAndSetSpinner(view);
        isInitialized = true;
        return view;
    }

    private void setupResolutionSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.spinnerResolution);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.resolution_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0, false);

        resolutionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialized && mainFragment != null) {
                    String selectedValue = parent.getItemAtPosition(position).toString();
                    int resolutionIndex = selectedValue.contains("1280") ? 0 : 1;
                    String command = "resolution:" + (resolutionIndex == 0 ? "1280p" : "1920p");
                    mainFragment.sendCommandToQt(command);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinner.setOnItemSelectedListener(resolutionListener);
    }

    private void setupIntervalSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.spinnerPhotoInterval);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.photo_interval_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0, false);

        intervalListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialized && mainFragment != null) {
                    String selectedValue = parent.getItemAtPosition(position).toString();
                    int interval = selectedValue.contains("1") ? 60 :
                            selectedValue.contains("3") ? 180 :
                                    selectedValue.contains("5") ? 300 : 600;
                    String command = "interval:" + selectedValue;
                    mainFragment.sendCommandToQt(command);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinner.setOnItemSelectedListener(intervalListener);
    }

    private void loadConfigAndSetSpinner(View view) {
        new Thread(() -> {
            JSONObject config = SMBConfigReader.loadConfig(requireContext());
            try {
                int resolutionIndex = config.getInt("resolution/index");
                int interval = config.getInt("capture/interval");

                requireActivity().runOnUiThread(() -> {
                    setSpinnerSelection(resolutionIndex, interval, view);
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void setSpinnerSelection(int resolutionIndex, int interval, View view) {
        Spinner resolutionSpinner = view.findViewById(R.id.spinnerResolution);
        Spinner intervalSpinner = view.findViewById(R.id.spinnerPhotoInterval);

        // 临时移除监听器
        resolutionSpinner.setOnItemSelectedListener(null);
        intervalSpinner.setOnItemSelectedListener(null);

        ArrayAdapter<CharSequence> resolutionAdapter = (ArrayAdapter<CharSequence>) resolutionSpinner.getAdapter();
        ArrayAdapter<CharSequence> intervalAdapter = (ArrayAdapter<CharSequence>) intervalSpinner.getAdapter();

        // 处理分辨率选择
        String resolutionText = resolutionIndex == 0 ? "1280p" : "1920p";
        int resPos = resolutionAdapter.getPosition(resolutionText);
        if (resPos != -1) resolutionSpinner.setSelection(resPos, false);

        // 处理间隔时间选择
        int intervalMin = interval / 60;
        String intervalText = intervalMin + "分钟";
        int intervalPos = intervalAdapter.getPosition(intervalText);
        if (intervalPos != -1) intervalSpinner.setSelection(intervalPos, false);

        // 恢复监听器
        resolutionSpinner.setOnItemSelectedListener(resolutionListener);
        intervalSpinner.setOnItemSelectedListener(intervalListener);
    }
}