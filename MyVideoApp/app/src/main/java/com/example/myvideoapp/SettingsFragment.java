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

public class SettingsFragment extends Fragment {
    private MainFragment mainFragment;
    private boolean isInitialized = false; // 添加标记
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 获取主 Fragment 的引用
        mainFragment = (MainFragment) requireActivity().getSupportFragmentManager().findFragmentByTag(MainFragment.class.getName());
        setupResolutionSpinner(view);
        setupIntervalSpinner(view);
        isInitialized = true;
        return view;
    }

    private void setupResolutionSpinner(View view) {

        Spinner spinner = view.findViewById(R.id.spinnerResolution);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.resolution_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0, false); // 第二个参数设为 false 禁止触发事件

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialized && mainFragment != null ) {
                    String command = "resolution:" + parent.getItemAtPosition(position);
                    mainFragment.sendCommandToQt(command);
                } else {
                    Toast.makeText(requireContext(), "未连接到设备", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupIntervalSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.spinnerPhotoInterval);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.photo_interval_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0, false); // 第二个参数设为 false 禁止触发事件

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialized && mainFragment != null ) {
                    String command = "interval:" + parent.getItemAtPosition(position);
                    mainFragment.sendCommandToQt(command);
                } else {
                    Toast.makeText(requireContext(), "未连接到设备", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}