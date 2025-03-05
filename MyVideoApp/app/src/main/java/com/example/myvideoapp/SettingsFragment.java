package com.example.myvideoapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.StaticIpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int CONNECTION_TIMEOUT =30 * 1000; // 30秒超时
    private boolean isConnected = false;
    private Socket clientSocket;
    private PrintWriter out;

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
                String command = "resolution:" + parent.getItemAtPosition(position);
                sendCommandToQt(command);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
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
                String command = "interval:" + parent.getItemAtPosition(position);
                sendCommandToQt(command);            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return view;
    }
    
    private void sendCommandToQt(String command) {
        new Thread(() -> {
            try {
                if (clientSocket == null || clientSocket.isClosed()) {
                    clientSocket = new Socket("192.168.1.1", 12345); // Qt设备IP
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                }
                out.println(command);
            } catch (IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "连接失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
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
        // Check if the device is running Android 10 (API 29) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Step 1: Create a WiFi network suggestion
            String ssid = "Jetson"; // 替换为目标 Wi-Fi 的 SSID
            String pwd = "12345678"; // 替换为目标 Wi-Fi 的密码

            WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(pwd)
                    .setIsAppInteractionRequired(true) // 需要用户确认连接
                    .build();

            List<WifiNetworkSuggestion> suggestionsList = new ArrayList<>();
            suggestionsList.add(suggestion);

            WifiManager wifiManager = (WifiManager) requireContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Toast.makeText(requireContext(), "无法获取 WifiManager", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add the network suggestion to the system
            int status = wifiManager.addNetworkSuggestions(suggestionsList);
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                // Step 2: Create a NetworkSpecifier based on the suggestion
                WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                        .setWpa2Passphrase(pwd)
                        .build();

                // Create a NetworkRequest
                NetworkRequest networkRequest = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // 不需要互联网
                        .setNetworkSpecifier(wifiNetworkSpecifier)
                        .build();

                // Step 3: Request the network
                ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager == null) {
                    Toast.makeText(requireContext(), "无法获取 ConnectivityManager", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Set up a timeout handler
                final Handler timeoutHandler = new Handler();
                timeoutHandler.postDelayed(() -> {
                    Toast.makeText(requireContext(), "连接超时，请稍后重试", Toast.LENGTH_SHORT).show();
                }, CONNECTION_TIMEOUT);

                // Register the network callback
                connectivityManager.requestNetwork(networkRequest, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        // Bind the process to the network
                        connectivityManager.bindProcessToNetwork(network);
                        timeoutHandler.removeCallbacksAndMessages(null); // 取消超时回调
                        Toast.makeText(requireContext(), "已连接到热点", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onUnavailable() {
                        super.onUnavailable();
                        timeoutHandler.removeCallbacksAndMessages(null);
                        Toast.makeText(requireContext(), "热点连接失败", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        // 断开 Socket 连接
                        if (clientSocket != null && !clientSocket.isClosed()) {
                            try {
                                clientSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        Toast.makeText(requireContext(), "热点连接已断开", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(requireContext(), "添加网络建议失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "当前设备不支持 Android 10 或更高版本", Toast.LENGTH_SHORT).show();
        }
    }
}