package com.example.myvideoapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
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
    private Switch switchTimestamp;
    private Switch switchEnhancement;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mainFragment = (MainFragment) requireActivity().getSupportFragmentManager().findFragmentByTag(MainFragment.class.getName());
        switchTimestamp = view.findViewById(R.id.switchTimestamp);
        switchEnhancement = view.findViewById(R.id.switchEnhancement);
        setupResolutionSpinner(view);
        setupIntervalSpinner(view);
        setupSwitches();
        loadConfigAndSetSpinner(view);
        setupResetButton(view);
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
    private void setupSwitches() {
        // 时间标签开关监听
        switchTimestamp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isInitialized && mainFragment != null) {
                String command = "timestamp:" + (isChecked ? "on" : "off");
                mainFragment.sendCommandToQt(command);
            }
        });

        // 图像增强算法开关监听
        switchEnhancement.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isInitialized && mainFragment != null) {
                String command = "enhancement:" + (isChecked ? "on" : "off");
                mainFragment.sendCommandToQt(command);
            }
        });
    }
    private void loadConfigAndSetSpinner(View view) {
        new Thread(() -> {
            JSONObject config = SMBConfigReader.loadConfig(requireContext());
            try {
                int resolutionIndex = config.getInt("resolution/index");
                int interval = config.getInt("capture/interval");
                boolean showTimestamp = config.getBoolean("display/showTimeStamp");
                boolean useEnhancement = config.getBoolean("image/enhancement");

                requireActivity().runOnUiThread(() -> {
                    setSpinnerSelection(resolutionIndex, interval, view);
                    switchTimestamp.setChecked(showTimestamp);
                    switchEnhancement.setChecked(useEnhancement);
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

    private void setupResetButton(View view) {
        Button resetButton = view.findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("确认恢复出厂设置")
                    .setMessage("你确定要恢复出厂设置吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (mainFragment != null) {
                            // 发送恢复出厂设置命令
                            String command = "reset:factory";
                            mainFragment.sendCommandToQt(command);
                            // 重置所有控件
                            setSpinnerResolutionTo1280p(view);
                            setSpinnerIntervalTo1Minute(view);
                            switchTimestamp.setChecked(true); // 默认开启时间标签
                            switchEnhancement.setChecked(false); // 默认关闭图像增强
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void setSpinnerResolutionTo1280p(View view) {
        Spinner resolutionSpinner = view.findViewById(R.id.spinnerResolution);
        ArrayAdapter<CharSequence> resolutionAdapter = (ArrayAdapter<CharSequence>) resolutionSpinner.getAdapter();
        int resPos = resolutionAdapter.getPosition("1280p");
        if (resPos != -1) {
            resolutionSpinner.setSelection(resPos, false);
        }
    }

    private void setSpinnerIntervalTo1Minute(View view) {
        Spinner intervalSpinner = view.findViewById(R.id.spinnerPhotoInterval);
        ArrayAdapter<CharSequence> intervalAdapter = (ArrayAdapter<CharSequence>) intervalSpinner.getAdapter();
        int intervalPos = intervalAdapter.getPosition("1分钟");
        if (intervalPos != -1) {
            intervalSpinner.setSelection(intervalPos, false);
        }
    }
}