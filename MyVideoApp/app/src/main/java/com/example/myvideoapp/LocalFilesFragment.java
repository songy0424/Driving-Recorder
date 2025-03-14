package com.example.myvideoapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

public class LocalFilesFragment extends Fragment {
    private static final int REQUEST_WRITE_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final String SMB_URL = "smb://192.168.1.1/SharedFolder/";

    private GridView gridView;
    private FileAdapter fileAdapter;
    private List<FileInfo> fileList = new ArrayList<>();
    private FileType currentFileType = FileType.ALL;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_files, container, false);

        gridView = view.findViewById(R.id.file_grid_view);
        Button selectBtn = view.findViewById(R.id.select_btn);
        Button videoBtn = view.findViewById(R.id.video_btn);
        Button imageBtn = view.findViewById(R.id.image_btn);

        selectBtn.setOnClickListener(v -> toggleSelectMode());
        videoBtn.setOnClickListener(v -> filterFiles(FileType.VIDEO));
        imageBtn.setOnClickListener(v -> filterFiles(FileType.IMAGE));

        fileAdapter = new FileAdapter(requireContext(), fileList);
        gridView.setAdapter(fileAdapter);
        gridView.setOnItemClickListener((parent, v, position, id) -> handleItemClick(position));

        new LoadFilesTask().execute();

        return view;
    }

    private void toggleSelectMode() {
        // 实现选择模式逻辑
    }

    private void filterFiles(FileType type) {
        currentFileType = type;
        new LoadFilesTask().execute();
    }

    private void handleItemClick(int position) {
        FileInfo file = fileList.get(position);
        if (file.isVideo()) {
            playVideo(file.getPath());
        } else {
            showImage(file.getPath());
        }
    }

    private void playVideo(String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(path);
        intent.setDataAndType(uri, "video/*");
        startActivity(intent);
    }

    private void showImage(String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(path);
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }

    private class LoadFilesTask extends AsyncTask<Void, Void, List<FileInfo>> {

        @Override
        protected List<FileInfo> doInBackground(Void... voids) {
            List<FileInfo> filteredList = new ArrayList<>();
            try {
                SmbFile smbDir = new SmbFile(SMB_URL);

                if (smbDir.exists() && smbDir.isDirectory()) {
                    SmbFile[] files = smbDir.listFiles();
                    for (SmbFile file : files) {
                        boolean isVideo = isVideoFile(file.getName());
                        if (currentFileType == FileType.ALL ||
                                (currentFileType == FileType.VIDEO && isVideo) ||
                                (currentFileType == FileType.IMAGE && !isVideo)) {
                            filteredList.add(new FileInfo(file.getName(), file.getPath(), isVideo));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return filteredList;
        }

        @Override
        protected void onPostExecute(List<FileInfo> result) {
            fileList.clear();
            fileList.addAll(result);
            fileAdapter.notifyDataSetChanged();
        }
    }

    private boolean isVideoFile(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return extension.equals("mp4") || extension.equals("avi") || extension.equals("mkv");
    }

    private enum FileType {
        ALL, VIDEO, IMAGE
    }

    // 内部类 FileAdapter
    private static class FileAdapter extends ArrayAdapter<FileInfo> {
        private Context context;
        private List<FileInfo> fileList;

        public FileAdapter(Context context, List<FileInfo> fileList) {
            super(context, 0, fileList);
            this.context = context;
            this.fileList = fileList;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_file_preview, parent, false);
            }

            FileInfo file = fileList.get(position);
            ImageView previewImage = convertView.findViewById(R.id.preview_image);
            TextView fileName = convertView.findViewById(R.id.file_name);

            fileName.setText(file.getName());

            if (file.isVideo()) {
                previewImage.setImageResource(R.drawable.ic_video);
            } else {
                try {
                    Glide.with(context)
                            .load(new URL(file.getPath()))
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .centerCrop()
                            .into(previewImage);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    previewImage.setImageResource(R.drawable.ic_broken_image);
                }
            }

            return convertView;
        }
    }

    // 文件信息类
    private static class FileInfo {
        private String name;
        private String path;
        private boolean isVideo;

        public FileInfo(String name, String path, boolean isVideo) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public boolean isVideo() {
            return isVideo;
        }
    }
}