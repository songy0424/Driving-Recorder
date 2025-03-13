package com.example.myvideoapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class VideoFragment extends Fragment {
    private ExoPlayer player;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video, container, false);
        PlayerView playerView = view.findViewById(R.id.fullscreen_player);
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);

        Button backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        return view;
    }

    public void playVideo(String url) {
        try {
            MediaItem mediaItem = MediaItem.fromUri(url);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "视频播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        player.release();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!player.isPlaying()) {
            player.play();
        }
    }
}