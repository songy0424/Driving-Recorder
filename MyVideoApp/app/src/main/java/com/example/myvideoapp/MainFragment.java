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
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainFragment extends Fragment {
    public boolean isConnected() {
        return isConnected;
    }
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int CONNECTION_TIMEOUT = 30 * 1000;
    private ExoPlayer player;
    private Button connectButton;
    private Button videoButton;
    private Socket clientSocket;
    private boolean isConnected = false;
    private PrintWriter out;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        connectButton = view.findViewById(R.id.connectButton);
        videoButton = view.findViewById(R.id.videoButton);
        videoButton.setEnabled(false);

        PlayerView playerView = view.findViewById(R.id.playerView);
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);

        connectButton.setOnClickListener(v -> checkWriteSettingsPermission());
        videoButton.setOnClickListener(v -> startVideoPlayback());

        return view;
    }

    // MainFragment.java
    private void startVideoPlayback() {
        // 使用Fragment跳转代替直接播放
        VideoFragment videoFragment = new VideoFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.mainFragment, videoFragment)
                .addToBackStack(null)
                .commit();

        new Handler().postDelayed(() ->
                videoFragment.playVideo("rtsp://192.168.1.1:8554/stream"), 300);
    }

    // 以下是连接相关方法
    private void checkWriteSettingsPermission() {
        if (!Settings.System.canWrite(requireContext())) {
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
            Toast.makeText(requireContext(), "正在开启WiFi...", Toast.LENGTH_SHORT).show();
            wifiManager.setWifiEnabled(true);
        }
        new Handler().postDelayed(this::connectToHotspot, 2000);
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
                            isConnected = true;
                            videoButton.setEnabled(true);
                            connectButton.setText("已连接");
                            setupSocketConnection();
                        });
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        requireActivity().runOnUiThread(() -> {
                            isConnected = false;
                            videoButton.setEnabled(false);
                            connectButton.setText("连接设备");
                        });
                    }
                }, new Handler(), CONNECTION_TIMEOUT);
            }
        }
    }

    private void setupSocketConnection() {
        new Thread(() -> {
            try {
                clientSocket = new Socket("192.168.1.1", 12345);
                out = new PrintWriter(clientSocket.getOutputStream(), true);
            } catch (IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "控制连接失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public void sendCommandToQt(String command) {
        if (out != null && !clientSocket.isClosed()) {
            new Thread(() -> out.println(command)).start();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (player != null) {
            player.release();
        }
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}