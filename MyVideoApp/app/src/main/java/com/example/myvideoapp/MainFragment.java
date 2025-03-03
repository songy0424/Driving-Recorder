package com.example.myvideoapp;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class MainFragment extends Fragment {
    private ExoPlayer player;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        // 初始化 ExoPlayer
        player = new ExoPlayer.Builder(requireContext()).build();
        PlayerView playerView = view.findViewById(R.id.playerView);
        playerView.setPlayer(player);

        // 设置 RTSP URL
        String rtspUrl = "rtsp://192.168.0.100:554/stream";
        MediaItem mediaItem = MediaItem.fromUri(rtspUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (player != null) {
            player.release();
        }
    }
}
