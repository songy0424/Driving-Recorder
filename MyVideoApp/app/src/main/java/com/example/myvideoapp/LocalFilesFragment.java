package com.example.myvideoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

public class LocalFilesFragment extends Fragment {
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String SMB_URL = "smb://192.168.1.1/SharedFolder/";
    private enum FileType {
        ALL, VIDEO, IMAGE
    }
    private GridView gridView;
    private FileAdapter adapter;
    private List<FileItem> fileItems = new ArrayList<>();
    private boolean isSelectMode = false;
    private Set<FileItem> selectedItems = new HashSet<>();
    private FileType currentType = FileType.VIDEO;
    private View selectionActionBar;
    private TextView tvSelectedCount;
    private Map<String, Bitmap> thumbnailCache = new HashMap<>();
    private ProgressDialog progressDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_files, container, false);

        initializeViews(view);
        setupAdapter();
        //        setupButtonListeners();
        loadFiles();

        return view;
    }

    private void initializeViews(View view) {
        gridView = view.findViewById(R.id.file_grid_view);
        selectionActionBar = view.findViewById(R.id.selection_action_bar);
        tvSelectedCount = view.findViewById(R.id.tv_selected_count);

        Button selectBtn = view.findViewById(R.id.select_btn);
        Button videoBtn = view.findViewById(R.id.video_btn);
        Button imageBtn = view.findViewById(R.id.image_btn);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        selectBtn.setOnClickListener(v -> toggleSelectMode());
        videoBtn.setOnClickListener(v -> filterFiles(FileType.VIDEO));
        imageBtn.setOnClickListener(v -> filterFiles(FileType.IMAGE));
        btnCancel.setOnClickListener(v -> exitSelectMode());
        btnConfirm.setOnClickListener(v -> showActionDialog());
    }

    private void setupAdapter() {
        adapter = new FileAdapter(requireContext(), fileItems);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            if (isSelectMode) toggleSelection(position);
            else handleFileClick(position);
        });
    }

    private void toggleSelectMode() {
        isSelectMode = !isSelectMode;
        if (!isSelectMode) selectedItems.clear();
        updateSelectionState();
    }

    private void updateSelectionState() {
        selectionActionBar.setVisibility(isSelectMode ? View.VISIBLE : View.GONE);
        updateSelectedCount();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectedCount() {
        tvSelectedCount.setText("已选 " + selectedItems.size() + " 项");
    }

    private void toggleSelection(int position) {
        FileItem item = fileItems.get(position);
        if (selectedItems.contains(item)) selectedItems.remove(item);
        else selectedItems.add(item);
        updateSelectedCount();
        adapter.notifyDataSetChanged();
    }

    private void filterFiles(FileType type) {
        currentType = type;
        loadFiles();
    }

    @SuppressLint("StaticFieldLeak")
    private void loadFiles() {
        new AsyncTask<Void, Void, List<FileItem>>() {
            @Override
            protected List<FileItem> doInBackground(Void... voids) {
                List<FileItem> items = new ArrayList<>();
                try {
                    SmbFile smbDir = new SmbFile(SMB_URL);
                    for (SmbFile file : smbDir.listFiles()) {
                        String name = file.getName();
                        boolean isVideo = isVideoFile(name);
                        if (shouldAddFile(isVideo)) {
                            items.add(new FileItem(name, file.getPath(), isVideo));
                        }
                    }
                } catch (Exception e) {
                    Log.e("SMB", "Error loading files", e);
                }
                return items;
            }

            @Override
            protected void onPostExecute(List<FileItem> items) {
                fileItems.clear();
                fileItems.addAll(items);
                selectedItems.clear();
                adapter.notifyDataSetChanged();
            }
        }.execute();
    }

    private boolean shouldAddFile(boolean isVideo) {
        return currentType == FileType.ALL ||
                (currentType == FileType.VIDEO && isVideo) ||
                (currentType == FileType.IMAGE && !isVideo);
    }

    private void enterSelectMode() {
        isSelectMode = true;
        selectionActionBar.setVisibility(View.VISIBLE);
        updateSelectedCount();
    }

    private void exitSelectMode() {
        isSelectMode = false;
        selectionActionBar.setVisibility(View.GONE);
        updateSelectedCount();
    }

    private void handleFileClick(int position) {
        FileItem item = fileItems.get(position);
        if (item.isVideo) playVideo(item.path);
        else previewImage(item.path);
    }

    private void playVideo(String path) {
        new Thread(() -> {
            try {
                SmbFile smbFile = new SmbFile(path);
                File tempFile = createTempFile("video", ".mp4");
                copySmbToLocal(smbFile, tempFile);

                runOnUiThread(() -> {
                    Uri uri = FileProvider.getUriForFile(requireContext(),
                            requireContext().getPackageName() + ".provider", tempFile);
                    startActivity(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "video/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                });
            } catch (Exception e) {
                showError("无法播放视频");
            }
        }).start();
    }

    private void previewImage(String path) {
        new Thread(() -> {
            try {
                SmbFile smbFile = new SmbFile(path);
                File tempFile = createTempFile("image", ".jpg");
                copySmbToLocal(smbFile, tempFile);

                runOnUiThread(() -> {
                    startActivity(new Intent(requireContext(), ImageViewerActivity.class)
                            .putExtra("image_path", tempFile.getAbsolutePath()));
                });
            } catch (Exception e) {
                showError("无法预览图片");
            }
        }).start();
    }

    private File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix, requireContext().getCacheDir());
    }

    private void copySmbToLocal(SmbFile smbFile, File localFile) throws IOException {
        try (InputStream in = new SmbFileInputStream(smbFile);
             OutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private void showActionDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("请选择操作")
                .setItems(new String[]{"下载到本地", "删除文件"}, (dialog, which) -> {
                    if (which == 0) checkStoragePermission();
                    else confirmDelete();
                    exitSelectMode();
                }).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setMessage("确定要删除选中的 " + selectedItems.size() + " 个文件吗？")
                .setPositiveButton("删除", (d, w) -> deleteSelectedFiles())
                .setNegativeButton("取消", null)
                .show();
    }

    @SuppressLint("StaticFieldLeak")
    private void deleteSelectedFiles() {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                int count = 0;
                for (FileItem item : selectedItems) {
                    try {
                        SmbFile smbFile = new SmbFile(item.path);
                        if (smbFile.exists()) {
                            smbFile.delete();
                            count++;
                        }
                    } catch (Exception e) {
                        Log.e("Delete", "删除失败: " + item.name, e);
                    }
                }
                return count;
            }

            @Override
            protected void onPostExecute(Integer count) {
                showToast("成功删除 " + count + " 个文件");
                loadFiles();
            }
        }.execute();
    }

    private void checkStoragePermission() {
        if (hasStoragePermission()) downloadSelectedFiles();
        else requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
    }

    private void downloadSelectedFiles() {
        progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("正在下载...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(selectedItems.size());
        progressDialog.show();

        new Thread(() -> {
            int count = 0;
            int currentFileIndex = 0;
            for (FileItem item : selectedItems) {
                try {
                    SmbFile smbFile = new SmbFile(item.path);
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File dest = new File(downloadDir, item.name);
                    downloadFileWithProgress(smbFile, dest, currentFileIndex);
                    count++;
                } catch (Exception e) {
                    Log.e("Download", "下载失败: " + item.name, e);
                }
                final int finalCurrentFileIndex = currentFileIndex;
                runOnUiThread(() -> progressDialog.setProgress(finalCurrentFileIndex + 1));
                currentFileIndex++;
            }
            final int finalCount = count;
            runOnUiThread(() -> {
                progressDialog.dismiss();
                showToast("成功下载 " + finalCount + " 个文件");
            });
        }).start();
    }

    private void downloadFileWithProgress(SmbFile smbFile, File localFile, int currentFileIndex) throws IOException {
        long fileLength = smbFile.length();
        try (InputStream in = new SmbFileInputStream(smbFile);
             OutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[4096];
            int len;
            long totalBytesRead = 0;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
                totalBytesRead += len;
                int progress = (int) ((totalBytesRead * 100) / fileLength);
                final int finalProgress = progress;
                final int finalCurrentFileIndex = currentFileIndex;
                runOnUiThread(() -> {
                    progressDialog.setMessage("正在下载文件 " + (finalCurrentFileIndex + 1) + " / " + selectedItems.size() + "：" + finalProgress + "%");
                });
            }
        }
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private class FileAdapter extends BaseAdapter {
        private Context context;
        private List<FileItem> items;

        FileAdapter(Context context, List<FileItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public FileItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_file_preview, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            FileItem item = getItem(position);
            holder.bind(item, isSelectMode, selectedItems.contains(item));
            convertView.setOnClickListener(v -> {
                if (isSelectMode) {
                    toggleSelection(position);
                } else {
                    handleFileClick(position);
                }
            });
            convertView.setOnLongClickListener(v -> {
                if (!isSelectMode) {
                    enterSelectMode();
                    toggleSelection(position);
                    return true;
                }
                return false;
            });
            return convertView;
        }


        private void copyStreamToFile(SmbFile smbFile, File localFile) throws IOException {
            try (SmbFileInputStream in = new SmbFileInputStream(smbFile);
                 FileOutputStream out = new FileOutputStream(localFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        }

        private class ViewHolder {
            ImageView image, videoIcon;
            TextView name;
            CheckBox checkbox;

            ViewHolder(View view) {
                image = view.findViewById(R.id.preview_image);
                name = view.findViewById(R.id.file_name);
                checkbox = view.findViewById(R.id.selection_checkbox);
                videoIcon = view.findViewById(R.id.video_icon);
            }

            void bind(FileItem item, boolean isSelectMode, boolean isSelected) {
                // 在加载新图片之前清空旧的图片
                image.setImageDrawable(null);
                name.setText(item.name);
                videoIcon.setVisibility(item.isVideo ? View.VISIBLE : View.GONE);
                checkbox.setVisibility(isSelectMode ? View.VISIBLE : View.GONE);
                checkbox.setChecked(isSelected);
                loadThumbnail(item);
            }

            void loadThumbnail(FileItem item) {
                if (thumbnailCache.containsKey(item.path)) {
                    image.setImageBitmap(thumbnailCache.get(item.path));
                } else {
                    try {
                        if (item.isVideo) loadVideoThumbnail(item.path);
                        else loadImageThumbnail(item.path);
                    } catch (Exception e) {
                        setErrorImage();
                    }
                }
            }

            void loadVideoThumbnail(String path) {
                new Thread(() -> {
                    try {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        File temp = createTempFile("thumb", ".mp4");
                        SmbFile smbFile = new SmbFile(path);
                        copyStreamToFile(smbFile, temp);
                        retriever.setDataSource(temp.getPath());
                        Bitmap bitmap = retriever.getFrameAtTime();
                        thumbnailCache.put(path, bitmap);
                        runOnUiThread(() -> image.setImageBitmap(bitmap));
                    } catch (Exception e) {
                        setErrorImage();
                    }
                }).start();
            }

            void loadImageThumbnail(String path) {
                new Thread(() -> {
                    try {
                        SmbFile smbFile = new SmbFile(path);
                        File temp = createTempFile("thumb", ".jpg");
                        copyStreamToFile(smbFile, temp);
                        Bitmap bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath());
                        thumbnailCache.put(path, bitmap);
                        runOnUiThread(() -> image.setImageBitmap(bitmap));
                    } catch (Exception e) {
                        setErrorImage();
                    }
                }).start();
            }

            void setErrorImage() {
                runOnUiThread(() ->
                        image.setImageResource(R.drawable.ic_broken_image));
            }
        }
    }

    // Helper方法
    private void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
    }

    // FileItem类
    private static class FileItem {
        String name;
        String path;
        boolean isVideo;

        FileItem(String name, String path, boolean isVideo) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
        }
    }

    private boolean isVideoFile(String name) {
        return name.toLowerCase().matches(".*\\.(mp4|avi|mkv|mov)$");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            downloadSelectedFiles();
        }
    }
}