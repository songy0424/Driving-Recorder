package com.example.myvideoapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.Gravity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SettingsFragment extends Fragment {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int CONNECTION_TIMEOUT =30 * 1000; // 30秒超时
    private boolean isConnected = false;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
     
        Button connectButton = view.findViewById(R.id.connectButton);
        connectButton.setOnClickListener(v -> checkWriteSettingsPermission());

        // 初始化第一个 Spinner
        Spinner spinnerResolution = view.findViewById(R.id.spinnerResolution);
        ArrayAdapter<CharSequence> adapterResolution = ArrayAdapter.createFromResource(
                requireContext(), R.array.resolution_options, android.R.layout.simple_spinner_item);
        adapterResolution.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResolution.setAdapter(adapterResolution);

        // 设置第一个 Spinner 的监听器
        spinnerResolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedResolution = parent.getItemAtPosition(position).toString();
                Toast.makeText(requireContext(), "选中的分辨率是: " + selectedResolution, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 没有选择时的处理
            }
        });

        // 初始化第二个 Spinner
        Spinner spinnerPhotoInterval = view.findViewById(R.id.spinnerPhotoInterval);
        ArrayAdapter<CharSequence> adapterPhotoInterval = ArrayAdapter.createFromResource(
                requireContext(), R.array.photo_interval_options, android.R.layout.simple_spinner_item);
        adapterPhotoInterval.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPhotoInterval.setAdapter(adapterPhotoInterval);

        // 设置第二个 Spinner 的监听器
        spinnerPhotoInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedInterval = parent.getItemAtPosition(position).toString();
                Toast.makeText(requireContext(), "选中的摄影间隔是: " + selectedInterval, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 没有选择时的处理
            }
        });
        return view;
    }

    private void checkWriteSettingsPermission() {
        if (!Settings.System.canWrite(requireContext())) {
            Toast.makeText(requireContext(), "需要授予修改系统设置权限", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } else {
            checkPermissions();
        }
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(getActivity(), permissions, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            enableWiFiAndConnect();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                enableWiFiAndConnect();
            } else {
                Toast.makeText(requireContext(), "权限被拒绝，无法连接到热点", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enableWiFiAndConnect() {
        WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            Toast.makeText(requireContext(), "请先打开 WiFi", Toast.LENGTH_SHORT).show();

            // 跳转到 WiFi 设置页面
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
            return;
        }

        // WiFi 已启用，尝试连接到热点
        connectToHotspot();
    }

    private void connectToHotspot() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            Toast.makeText(requireContext(), "无法获取 ConnectivityManager", Toast.LENGTH_SHORT).show();
            return;
        }

        WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid("Jetson") // 替换为目标 Wi-Fi 的 SSID
                .setWpa2Passphrase("12345678") // 替换为目标 Wi-Fi 的密码
                .build();

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build();

        Handler timeoutHandler = new Handler();
        timeoutHandler.postDelayed(() -> {
            if (!isConnected) {
                Toast.makeText(requireContext(), "连接超时，请稍后重试", Toast.LENGTH_SHORT).show();
            }
        }, CONNECTION_TIMEOUT);

        connectivityManager.requestNetwork(networkRequest, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                connectivityManager.bindProcessToNetwork(network);
                isConnected = true;
                timeoutHandler.removeCallbacksAndMessages(null); // 取消超时回调
                Toast.makeText(requireContext(), "已连接到热点", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                if (!isConnected) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    Toast.makeText(requireContext(), "热点连接失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                isConnected = false;
                Toast.makeText(requireContext(), "热点连接已断开", Toast.LENGTH_SHORT).show();
            }
        });
    }
}