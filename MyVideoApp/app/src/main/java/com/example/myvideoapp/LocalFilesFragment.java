package com.example.myvideoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

public class LocalFilesFragment extends Fragment {
    private static final String SMB_BASE_URL = "smb://192.168.1.1/SharedFolder/";
    private static final String VIDEO_SUB_PATH = "Video/";
    private static final String IMAGE_SUB_PATH = "Picture/";
    private enum DisplayMode {DEVICE, LOCAL}
    private DisplayMode currentMode = DisplayMode.DEVICE;
    private boolean canDownload = true;
    private boolean isVideoFile(String path) {
        return path.contains(VIDEO_SUB_PATH);
    }
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private enum FileType {ALL, VIDEO, IMAGE}
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
    private Button selectAllBtn;
    private Button localFileBtn;
    private TextView titleText;

    private void toggleFileSource() {
        currentMode = currentMode == DisplayMode.DEVICE ? DisplayMode.LOCAL : DisplayMode.DEVICE;
        updateUIForCurrentMode();
        loadFiles();
    }

    private void updateUIForCurrentMode() {
        titleText.setText(currentMode == DisplayMode.DEVICE ? "设备文件" : "本地文件");
        localFileBtn.setText(currentMode == DisplayMode.DEVICE ? "本地文件" : "设备文件");
        canDownload = currentMode == DisplayMode.DEVICE;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_files, container, false);
        initializeViews(view);
        setupAdapter();
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
        localFileBtn = view.findViewById(R.id.local_file_btn);
        titleText = view.findViewById(R.id.title_text);

        localFileBtn.setOnClickListener(v -> toggleFileSource());
        selectBtn.setOnClickListener(v -> toggleSelectMode());
        videoBtn.setOnClickListener(v -> filterFiles(FileType.VIDEO));
        imageBtn.setOnClickListener(v -> filterFiles(FileType.IMAGE));
        btnCancel.setOnClickListener(v -> exitSelectMode());
        btnConfirm.setOnClickListener(v -> showActionDialog());
        selectAllBtn = view.findViewById(R.id.select_all_btn);
        selectAllBtn.setOnClickListener(v -> toggleSelectAll());
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
        if (isSelectMode) {
            selectedItems.clear();
            selectAllBtn.setVisibility(View.VISIBLE);
            selectAllBtn.setText("全选");
        } else {
            exitSelectMode();
        }
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
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }

        // 同步全选按钮状态
        if (selectedItems.size() == fileItems.size()) {
            selectAllBtn.setText("全不选");
        } else {
            selectAllBtn.setText("全选");
        }

        updateSelectedCount();
        adapter.notifyDataSetChanged();
    }

    private void toggleSelectAll() {
        if (selectedItems.size() == fileItems.size()) {
            selectedItems.clear();
            selectAllBtn.setText("全选");
        } else {
            selectedItems.addAll(fileItems);
            selectAllBtn.setText("全不选");
        }
        adapter.notifyDataSetChanged();
        updateSelectedCount();
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
                    if (currentMode == DisplayMode.DEVICE) {
                        String subPath = currentType == FileType.VIDEO ? VIDEO_SUB_PATH : IMAGE_SUB_PATH;
                        SmbFile smbDir = new SmbFile(SMB_BASE_URL + subPath);
                        List<SmbFile> fileList = Arrays.asList(smbDir.listFiles());

                        Collections.sort(fileList, (f1, f2) -> {
                            try {
                                return Long.compare(
                                        parseTimestampFromName(f2.getName()), // 降序排列
                                        parseTimestampFromName(f1.getName())
                                );
                            } catch (Exception e) {
                                return 0;
                            }
                        });

                        // 转换排序后的文件列表
                        for (SmbFile file : fileList) {
                            String name = file.getName();
                            boolean isVideo = isVideoFile(file.getPath());
                            long timestamp = parseTimestampFromName(name);
                            items.add(new FileItem(name, file.getPath(), isVideo, timestamp, false));
                        }
                    } else {
                        String subPath = currentType == FileType.VIDEO ? VIDEO_SUB_PATH : IMAGE_SUB_PATH;
                        File localDir = new File(getLocalStorageRoot(), subPath);
                        loadLocalDirectory(localDir, items, currentType == FileType.VIDEO);
                    }
                } catch (Exception e) {
                    Log.e("FileLoad", "Error loading files", e);
                }
                return items;
            }

            private void loadLocalDirectory(File dir, List<FileItem> items, boolean isVideo) {
                List<File> fileList = Arrays.asList(dir.listFiles());

                Collections.sort(fileList, (f1, f2) -> {
                    try {
                        return Long.compare(
                                parseTimestampFromName(f2.getName()), // 降序排列
                                parseTimestampFromName(f1.getName())
                        );
                    } catch (Exception e) {
                        return 0;
                    }
                });

                if (dir.exists() && dir.isDirectory()) {
                    for (File file : fileList) {
                        items.add(new FileItem(
                                file.getName(),
                                file.getAbsolutePath(),
                                isVideo,
                                file.lastModified(),
                                true
                        ));
                    }
                }
            }

            @Override
            protected void onPostExecute(List<FileItem> items) {
                fileItems.clear();
                fileItems.addAll(items);
                selectedItems.clear();
                exitSelectMode();
                adapter.notifyDataSetChanged();
            }
        }.execute();
    }

    private void enterSelectMode() {
        isSelectMode = true;
        selectionActionBar.setVisibility(View.VISIBLE);
        updateSelectedCount();
    }

    private void exitSelectMode() {
        isSelectMode = false;
        selectionActionBar.setVisibility(View.GONE);
        selectAllBtn.setVisibility(View.GONE);
        adapter.clearSelections();
    }

    private void handleFileClick(int position) {
        FileItem item = fileItems.get(position);
        if (item.isVideo) playVideo(item);
        else previewImage(item);
    }

    private void playVideo(FileItem item) {
        new Thread(() -> {
            try {
                File localFile = getLocalFile(item);
                if (!localFile.exists()) {
                    showDownloadProgress("下载中...");
                    SmbFile smbFile = new SmbFile(item.path);
                    copySmbToLocalWithProgress(smbFile, localFile);
                    dismissDownloadProgress();
                }
                Uri uri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".provider", localFile);
                startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "video/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception e) {
                dismissDownloadProgress();
                showError("播放失败");
            }
        }).start();
    }

    private void previewImage(FileItem item) {
        new Thread(() -> {
            try {
                File localFile = getLocalFile(item);
                if (!localFile.exists()) {
                    SmbFile smbFile = new SmbFile(item.path);
                    copySmbToLocal(smbFile, localFile);
                }
                startActivity(new Intent(requireContext(), ImageViewerActivity.class)
                        .putExtra("image_path", localFile.getAbsolutePath()));
            } catch (Exception e) {
                showError("预览失败");
            }
        }).start();
    }

    private File getLocalFile(FileItem item) {
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "My Video/" + (item.isVideo ? "Video" : "Picture"));
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, item.name);
    }

    private void showDownloadProgress(String message) {
        runOnUiThread(() -> {
            progressDialog = new ProgressDialog(requireContext());
            progressDialog.setMessage(message);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.show();
        });
    }

    private void updateDownloadProgress(int progress) {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.setProgress(progress);
            }
        });
    }

    private void dismissDownloadProgress() {
        runOnUiThread(() -> {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        });
    }

    private void copySmbToLocalWithProgress(SmbFile smbFile, File localFile) throws IOException {
        long fileSize = smbFile.length();
        try (InputStream in = new SmbFileInputStream(smbFile);
             OutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[4096];
            int len;
            long total = 0;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
                total += len;
                int progress = (int) (total * 100 / fileSize);
                updateDownloadProgress(progress);
            }
        }
    }

    private File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix, requireContext().getCacheDir());
    }

    private void copySmbToLocal(SmbFile smbFile, File localFile) throws IOException {
        try (InputStream in = new SmbFileInputStream(smbFile);
             OutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[4096];
            while (in.read(buffer) > 0) {
                out.write(buffer);
            }
        }
    }

    private void showActionDialog() {
        String[] actions = currentMode == DisplayMode.DEVICE ?
                new String[]{"下载到本地", "删除文件"} : new String[]{"删除文件"};
        new AlertDialog.Builder(requireContext())
                .setTitle("操作")
                .setItems(actions, (dialog, which) -> {
                    if (currentMode == DisplayMode.DEVICE && which == 0) {
                        checkStoragePermission();
                    } else {
                        confirmDelete();
                    }
                    exitSelectMode();
                }).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setMessage("确定删除选中的" + selectedItems.size() + "个文件？")
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
                        if (currentMode == DisplayMode.DEVICE) {
                            new SmbFile(item.path).delete();
                        } else {
                            new File(item.path).delete();
                        }
                        count++;
                    } catch (Exception e) {
                        Log.e("Delete", "删除失败: " + item.name, e);
                    }
                }
                return count;
            }

            @Override
            protected void onPostExecute(Integer count) {
                showToast("成功删除" + count + "个文件");
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
            int skippedCount = 0;
            int currentFileIndex = 0;

            // 创建基础目录
            File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "My Video");
            File videoDir = new File(baseDir, "Video");
            File pictureDir = new File(baseDir, "Picture");

            if (!videoDir.exists()) videoDir.mkdirs();
            if (!pictureDir.exists()) pictureDir.mkdirs();

            for (FileItem item : selectedItems) {
                try {
                    SmbFile smbFile = new SmbFile(item.path);

                    // 根据文件类型选择目标目录
                    File targetDir = item.isVideo ? videoDir : pictureDir;
                    File dest = new File(targetDir, item.name);

                    // 检查文件是否存在
                    if (dest.exists()) {
                        skippedCount++;
                        continue;
                    }

                    // 执行下载
                    downloadFileWithProgress(smbFile, dest, currentFileIndex);
                    count++;
                } catch (Exception e) {
                    Log.e("Download", "下载失败: " + item.name, e);
                }

                // 更新进度
                final int finalCurrentFileIndex = currentFileIndex;
                runOnUiThread(() -> progressDialog.setProgress(finalCurrentFileIndex + 1));
                currentFileIndex++;
            }

            // 显示结果
            final int finalCount = count;
            final int finalSkipped = skippedCount;
            // 在下载完成提示后添加路径显示
            runOnUiThread(() -> {
                progressDialog.dismiss();
                String message = "下载完成：" + finalCount + " 个文件已保存";
                if (finalSkipped > 0) {
                    message += "（跳过 " + finalSkipped + " 个已存在文件）";
                }
                showToast(message);
                exitSelectMode();
                loadFiles();
                // 显示完整路径对话框
                new AlertDialog.Builder(requireContext())
                        .setTitle("文件保存位置")
                        .setMessage(baseDir.getAbsolutePath())
                        .setPositiveButton("复制路径", (d, w) -> {
                            ClipboardManager clipboard = (ClipboardManager)
                                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("路径", baseDir.getAbsolutePath());
                            clipboard.setPrimaryClip(clip);
                            showToast("路径已复制到剪贴板");
                        })
                        .setNegativeButton("确定", null)
                        .show();
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

        public void clearSelections() {
            notifyDataSetChanged();
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
            TextView name, duration;
            CheckBox checkbox;

            ViewHolder(View view) {
                image = view.findViewById(R.id.preview_image);
                name = view.findViewById(R.id.file_name);
                duration = view.findViewById(R.id.file_duration);
                checkbox = view.findViewById(R.id.selection_checkbox);
                videoIcon = view.findViewById(R.id.video_icon);
            }

            void bind(FileItem item, boolean isSelectMode, boolean isSelected) {
                image.setImageDrawable(null);
                name.setText(item.name);
                videoIcon.setVisibility(item.isVideo ? View.VISIBLE : View.GONE);
                checkbox.setVisibility(isSelectMode ? View.VISIBLE : View.GONE);
                checkbox.setChecked(isSelected);

                // 仅本地视频显示时长
                if (item.isVideo && item.isLocalFile) {
                    duration.setVisibility(View.VISIBLE);
                    loadVideoDuration(item);
                } else {
                    duration.setVisibility(View.GONE);
                }

                // 加载缩略图
                loadThumbnail(item);
            }
            private String formatDuration(long durationMs) {
                long totalSec = durationMs / 1000;
                long minutes = totalSec / 60;
                long seconds = totalSec % 60;
                return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
            }
            void loadVideoDuration(FileItem item) {
                new Thread(() -> {
                    try {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(item.path); // 本地文件直接读取
                        String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (durationStr != null) {
                            long durationMs = Long.parseLong(durationStr);
                            final String formatted = formatDuration(durationMs);
                            runOnUiThread(() -> duration.setText(formatted));
                        } else {
                            runOnUiThread(() -> duration.setText("未知"));
                        }
                    } catch (Exception e) {
                        Log.e("Duration", "时长获取失败: " + item.path, e);
                        runOnUiThread(() -> duration.setText("未知"));
                    }
                }).start();
            }
            void loadThumbnail(FileItem item) {
                if (thumbnailCache.containsKey(item.path)) {
                    image.setImageBitmap(thumbnailCache.get(item.path));
                } else {
                    try {
                        if (item.isVideo) {
                            if (item.isLocalFile) {
                                loadLocalVideoThumbnail(item.path);
                            } else {
                                loadVideoThumbnail(item.path);
                            }
                        } else {
                            if (item.isLocalFile) {
                                loadLocalImageThumbnail(item.path);
                            } else {
                                loadImageThumbnail(item.path);
                            }
                        }
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
                        runOnUiThread(() -> {
                            thumbnailCache.put(path, bitmap);
                            image.setImageBitmap(bitmap);
                        });
                        
                    } catch (Exception e) {
                        setErrorImage();
                    }
                }).start();
            }

            void loadLocalVideoThumbnail(String path) {
                new Thread(() -> {
                    try {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(path);
                        Bitmap bitmap = retriever.getFrameAtTime();
                        thumbnailCache.put(path, bitmap);
                        runOnUiThread(() -> {
                            thumbnailCache.put(path, bitmap);
                            image.setImageBitmap(bitmap);
                        });
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

            void loadLocalImageThumbnail(String path) {
                new Thread(() -> {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
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

    private void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
    }

    private static class FileItem {
        String name;
        String path;
        boolean isVideo;
        long timestamp;
        boolean isLocalFile;

        FileItem(String name, String path, boolean isVideo, long timestamp, boolean isLocalFile) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
            this.timestamp = timestamp;
            this.isLocalFile = isLocalFile;
        }
    }

    private File getLocalStorageRoot() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "My Video");
    }

    private long parseTimestampFromName(String fileName) {
        try {
            String timeStr = fileName.substring(0, 15);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            Date date = sdf.parse(timeStr);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            downloadSelectedFiles();
        }
    }
}