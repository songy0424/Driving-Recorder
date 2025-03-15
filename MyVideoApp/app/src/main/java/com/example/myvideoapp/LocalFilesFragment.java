package com.example.myvideoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
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
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

public class LocalFilesFragment extends Fragment {
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String SMB_URL = "smb://192.168.1.1/SharedFolder/";

    private GridView gridView;
    private FileAdapter adapter;
    private List<FileItem> fileItems = new ArrayList<>();
    private boolean isSelectMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();
    private FileType currentType = FileType.ALL;
    private View selectionActionBar;
    private TextView tvSelectedCount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_files, container, false);

        gridView = view.findViewById(R.id.file_grid_view);
        Button selectBtn = view.findViewById(R.id.select_btn);
        Button videoBtn = view.findViewById(R.id.video_btn);
        Button imageBtn = view.findViewById(R.id.image_btn);
        selectionActionBar = view.findViewById(R.id.selection_action_bar);
        tvSelectedCount = view.findViewById(R.id.tv_selected_count);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        // 设置操作栏按钮点击事件
        btnCancel.setOnClickListener(v -> exitSelectMode());
        btnConfirm.setOnClickListener(v -> showActionDialog());

        adapter = new FileAdapter(requireContext(), fileItems);
        gridView.setAdapter(adapter);

        selectBtn.setOnClickListener(v -> toggleSelectMode());
        videoBtn.setOnClickListener(v -> filterFiles(FileType.VIDEO));
        imageBtn.setOnClickListener(v -> filterFiles(FileType.IMAGE));

        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            if (isSelectMode) {
                toggleSelection(position);
            } else {
                handleFileClick(position);
            }
        });

        //首页显示视频
        filterFiles(FileType.VIDEO);

        return view;
    }

    private void toggleSelectMode() {
        isSelectMode = !isSelectMode;
        if (isSelectMode) {
            enterSelectMode();
        } else {
            exitSelectMode();
        }
        updateSelectionState(); // 统一状态更新
    }

    // 新增统一状态更新方法
    private void updateSelectionState() {
        selectionActionBar.setVisibility(isSelectMode ? View.VISIBLE : View.GONE);
        updateSelectedCount();
        adapter.notifyDataSetChanged();
    }

    private void enterSelectMode() {
        isSelectMode = true;
        selectionActionBar.setVisibility(View.VISIBLE);
        updateSelectedCount();
    }

    private void exitSelectMode() {
        isSelectMode = false;
        selectedPositions.clear();
        selectionActionBar.setVisibility(View.GONE);
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        int selectedCount = selectedPositions.size();
        tvSelectedCount.setText("已选 " + selectedCount + " 项");
        Log.d("LocalFilesFragment", "Updated selected count text: " + tvSelectedCount.getText());
    }

    private void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        Log.d("LocalFilesFragment", "Selected count: " + selectedPositions.size());
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
                        if (currentType == FileType.ALL ||
                                (currentType == FileType.VIDEO && isVideo) ||
                                (currentType == FileType.IMAGE && !isVideo)) {
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
                adapter.notifyDataSetChanged();
            }
        }.execute();
    }

    private void handleFileClick(int position) {
        FileItem item = fileItems.get(position);
        if (item.isVideo()) {
            playVideo(item.getPath());
        } else {
            previewImage(item.getPath());
        }
    }

    private void playVideo(String path) {
        try {
            SmbFile smbFile = new SmbFile(path);
            File cacheDir = requireContext().getCacheDir();
            File tempFile = File.createTempFile("video", ".mp4", cacheDir);
            Handler mainHandler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                try {
                    copySmbToLocal(smbFile, tempFile);
                    mainHandler.post(() -> {
                        // 文件复制成功，播放视频
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        Uri uri = FileProvider.getUriForFile(requireContext(),
                                requireContext().getPackageName() + ".provider", tempFile);
                        intent.setDataAndType(uri, "video/*");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    });
                } catch (IOException e) {
                    Log.e("Copy", "文件复制失败: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "文件复制失败，无法播放视频", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("PlayVideo", "未知异常: ", e);
            Toast.makeText(requireContext(), "未知异常，无法播放视频", Toast.LENGTH_SHORT).show();
        }
    }

    private void previewImage(String path) {
        try {
            SmbFile smbFile = new SmbFile(path);
            File cacheDir = requireContext().getCacheDir();
            File tempFile = File.createTempFile("image", ".jpg", cacheDir);

            Handler mainHandler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                try {
                    copySmbToLocal(smbFile, tempFile);
                    mainHandler.post(() -> {
                        // 文件复制成功，打开图片预览界面
                        Intent intent = new Intent(requireContext(), ImageViewerActivity.class);
                        intent.putExtra("image_path", tempFile.getAbsolutePath());
                        startActivity(intent);
                    });
                } catch (IOException e) {
                    Log.e("Copy", "文件复制失败: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "文件复制失败，无法预览图片", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("PreviewImage", "未知异常: ", e);
            Toast.makeText(requireContext(), "未知异常，无法预览图片", Toast.LENGTH_SHORT).show();
        }
    }

    private void copySmbToLocal(SmbFile smbFile, File localFile) throws IOException {
        try (SmbFileInputStream in = new SmbFileInputStream(smbFile);
             FileOutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    // 仅在退出选择模式时弹出对话框
    // 修改后的操作对话框显示方法
    private void showActionDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("请选择操作")
                .setItems(new String[]{"下载到本地", "删除文件"}, (dialog, which) -> {
                    if (which == 0) {
                        checkStoragePermission();
                    } else {
                        confirmDelete();
                    }
                    exitSelectMode(); // 立即退出选择模式
                })
                .setOnDismissListener(dialog -> exitSelectMode())
                .show();
    }

    // 添加删除确认对话框
    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setMessage("确定要删除选中的 " + selectedPositions.size() + " 个文件吗？")
                .setPositiveButton("删除", (d, w) -> deleteSelectedFiles())
                .setNegativeButton("取消", null)
                .show();
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        } else {
            downloadSelectedFiles();
        }
    }

    private void downloadSelectedFiles() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            return;
        }
        new DownloadTask().execute();
    }

    @SuppressLint("StaticFieldLeak")
    private class DownloadTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... voids) {
            int count = 0;
            for (int position : selectedPositions) {
                FileItem item = fileItems.get(position);
                try {
                    SmbFile smbFile = new SmbFile(item.getPath());
                    File downloadDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        if (!downloadDir.mkdirs()) {
                            Log.e("Download", "无法创建下载目录: " + downloadDir.getAbsolutePath());
                            continue;
                        }
                    }
                    File dest = new File(downloadDir, item.getName());
                    try (InputStream in = new SmbFileInputStream(smbFile);
                         OutputStream out = new FileOutputStream(dest)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        count++;
                    }
                } catch (SmbException e) {
                    Log.e("Download", "SMB 异常，下载失败: " + item.getName(), e);
                } catch (Exception e) {
                    Log.e("Download", "下载失败: " + item.getName(), e);
                }
            }
            return count;
        }

        @Override
        protected void onPostExecute(Integer count) {
            Toast.makeText(requireContext(),
                    "成功下载 " + count + " 个文件", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void deleteSelectedFiles() {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                int count = 0;
                for (int position : selectedPositions) {
                    FileItem item = fileItems.get(position);
                    try {
                        SmbFile smbFile = new SmbFile(item.getPath());
                        if (smbFile.exists()) {
                            smbFile.delete();
                            count++;
                        }
                    } catch (Exception e) {
                        Log.e("Delete", "Error deleting: " + item.getName(), e);
                    }
                }
                return count;
            }

            @Override
            protected void onPostExecute(Integer count) {
                Toast.makeText(requireContext(),
                        "成功删除 " + count + " 个文件", Toast.LENGTH_SHORT).show();
                loadFiles();
            }
        }.execute();
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
                holder = new ViewHolder();
                holder.image = convertView.findViewById(R.id.preview_image);
                holder.name = convertView.findViewById(R.id.file_name);
                holder.checkbox = convertView.findViewById(R.id.selection_checkbox);
                holder.videoIcon = convertView.findViewById(R.id.video_icon);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            FileItem item = getItem(position);
            holder.name.setText(item.getName());
            holder.checkbox.setVisibility(isSelectMode ? View.VISIBLE : View.GONE);
            holder.checkbox.setChecked(selectedPositions.contains(position));
            holder.videoIcon.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);

            loadThumbnail(item, holder.image);
            convertView.setOnClickListener(v -> {
                if (isSelectMode) {
                    toggleSelection(position);
                } else {
                    handleFileClick(position);
                }
            });
            // 添加长按进入选择模式
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

        private void loadThumbnail(FileItem item, ImageView imageView) {
            try {
                SmbFile smbFile = new SmbFile(item.getPath());
                RequestOptions options = new RequestOptions()
                        .placeholder(R.drawable.ic_broken_image)
                        .error(R.drawable.ic_broken_image);

                if (item.isVideo()) {
                    Glide.with(context)
                            .load(new SmbFileInputStream(smbFile))
                            .apply(options.frame(1000000))
                            .into(imageView);
                } else {
                    Glide.with(context)
                            .load(new SmbFileInputStream(smbFile))
                            .apply(options)
                            .into(imageView);
                }
            } catch (Exception e) {
                Glide.with(context).load(R.drawable.ic_broken_image).into(imageView);
            }
        }

        class ViewHolder {
            ImageView image;
            TextView name;
            CheckBox checkbox;
            ImageView videoIcon;
        }
    }

    public void onDestroyView() {
        super.onDestroyView();
        exitSelectMode(); // 确保视图销毁时清理状态
    }

    private static class FileItem {
        private String name;
        private String path;
        private boolean isVideo;

        FileItem(String name, String path, boolean isVideo) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
        }

        String getName() {
            return name;
        }

        String getPath() {
            return path;
        }

        boolean isVideo() {
            return isVideo;
        }
    }

    private enum FileType {
        ALL, VIDEO, IMAGE
    }

    private boolean isVideoFile(String name) {
        return name.toLowerCase().matches(".*\\.(mp4|avi|mkv|mov)$");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            downloadSelectedFiles();
        }
    }
}