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
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class ConnectFragment extends Fragment {
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int CONNECTION_TIMEOUT = 30 * 1000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect, container, false);

        Button connectButton = view.findViewById(R.id.connectButton);
        connectButton.setOnClickListener(v -> checkWriteSettingsPermission());

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
            ActivityCompat.requestPermissions(requireActivity(), permissions, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
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
                Toast.makeText(requireContext(), "权限被拒绝，无法连接", Toast.LENGTH_SHORT).show();
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
        connectToHotspot();
    }

    private void connectToHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                    .setSsid("Jetson")
                    .setWpa2Passphrase("12345678")
                    .setIsAppInteractionRequired(true)
                    .build();

            List<WifiNetworkSuggestion> suggestions = new ArrayList<>();
            suggestions.add(suggestion);

            WifiManager wifiManager = (WifiManager) requireContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.addNetworkSuggestions(suggestions) == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                connectViaNetworkSpecifier();
            }
        }
    }

    private void connectViaNetworkSpecifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                    .setSsid("Jetson")
                    .setWpa2Passphrase("12345678")
                    .build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "已连接到热点", Toast.LENGTH_SHORT).show();
                            MainActivity mainActivity = (MainActivity) requireActivity();
                            mainActivity.setConnected(true);
                        });
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "网络连接已丢失", Toast.LENGTH_SHORT).show();
                            MainActivity mainActivity = (MainActivity) requireActivity();
                            mainActivity.setConnected(false);
                        });
                    }
                }, new Handler(), CONNECTION_TIMEOUT);
            }
        }
    }
}