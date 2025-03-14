package com.example.myvideoapp;

public class FileInfo {
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