package com.example.myvideoapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class MainFragment extends Fragment {
    private ExoPlayer player;
    private Socket clientSocket;
    private boolean isConnected = false; // 新增连接状态标志
    private PrintWriter out;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        PlayerView playerView = view.findViewById(R.id.playerView);
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isConnected) {
            startVideoPlayback();
        }
    }

    private void startVideoPlayback() {
        MediaItem mediaItem = MediaItem.fromUri("rtsp://192.168.1.1:8554/stream");
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    public void setConnected(boolean connected) {
        isConnected = connected; // 更新连接状态
        if (connected) {
            setupSocketConnection();
            if (isAdded()) {
                startVideoPlayback();
            }
        } else {
            if (player != null) {
                player.stop();
            }
            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setupSocketConnection() {
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