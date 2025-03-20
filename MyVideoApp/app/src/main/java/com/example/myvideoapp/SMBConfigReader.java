package com.example.myvideoapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

public class SMBConfigReader {
    private static final String SMB_CONFIG_URL = "smb://192.168.1.1/SharedFolder/config.json";
    private static final String CHARSET = "UTF-8";

    public static JSONObject loadConfig(Context context) {
        try {
            SmbFile configFile = new SmbFile(SMB_CONFIG_URL);
            if (!configFile.exists()) {
                return new JSONObject();
            }

            try (InputStream in = new SmbFileInputStream(configFile);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                String content = new String(out.toByteArray(), CHARSET);
                Log.d("SMBConfig", "读取到的文件内容: " + content);

                return new JSONObject(content);
            }
        } catch (IOException e) {
            Log.e("SMBConfig", "网络错误: " + e.getMessage());
            return new JSONObject();
        } catch (JSONException e) {
            Log.e("SMBConfig", "JSON解析错误: " + e.getMessage());
            return new JSONObject();
        }
    }
}