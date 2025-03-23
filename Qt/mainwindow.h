#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <gst/gst.h>
#include <gst/rtsp-server/rtsp-media.h>
#include <gst/rtsp-server/rtsp-server.h>
#include <gst/app/gstappsrc.h>
#include <gst/app/app.h>
#include <QMainWindow>
#include "camera.h"
#include <QLabel>
#include <QTimer>
#include <QPushButton>
#include "settingspage.h"

namespace Ui
{
    class MainWindow;
}

class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);
    ~MainWindow();
    static void media_configure(GstRTSPMediaFactory *factory, GstRTSPMedia *media, gpointer user_data);

private slots:
    void takeSnapshot(const QImage &qImage); // 拍照槽函数
    void updateTime();
    void processFrame();
    void displayFrameOnLabel(const QImage &qImage);
    void slot_Photograph();
    void slot_RecordVideo();
    void slot_SaveVideo(const cv::Mat &image);
    void updateVideoFile();
    void showSettings(); // 切换到设置页面的槽函数
    void showMain();     // 显示主窗口的槽函数
    void updateResolution(int width, int height, int frameRate);
    void addTimestamp(cv::Mat &frame);
    void updatePhotoInterval(int interval);
    void onWifiStateChanged(bool isActive, const QString &ipAddress);

private:
    int width;      // 类的成员变量，用于宽度
    int height;     // 类的成员变量，用于高度
    int frameRate;  // 类的成员变量，用于帧率
    int frameCount; // 声明 frameCount 成员变量
    Ui::MainWindow *ui;
    Camera *camera;
    QLabel *imageLabel;       // 用于显示图片的QTimer
    QTimer *timer;            // 用于捕获帧的QTimer
    QTimer *videoTimer;       // 用于循环录制的QTimer
    QLabel *timeLabel;        // 用于显示时间的QLabel
    QTimer *timeTimer;        // 用于更新时间的QTimer
    QLabel *recordingLabel_1; // 用于显示录制中的QLabel
    QLabel *recordingLabel_2; // 用于显示录制中的QLabel
    QIcon startIcon;
    QIcon stopIcon;
    QIcon takePhotoIcon;
    QIcon settingIcon;
    bool isSaveImage;
    bool isRecordVideo;
    cv::VideoWriter videorecord;
    SettingsPage *settingsPage; // 设置页面的指针
    GstElement *appsrc;
    GstRTSPServer *rtspServer;
    GstRTSPMediaFactory *factory;
    void startRTSPServer(const QString &ipAddress);
    void stopRTSPServer();
    // cv::Mat applyCLAHE(const cv::Mat &frame);
    cv::cuda::GpuMat gpu_frame, gpu_sharpened;
    cv::cuda::GpuMat gpu_kernel;
    cv::Ptr<cv::cuda::Filter> filter;
    cv::Mat sharpened;
    cv::Mat applySharpening(const cv::Mat &frame);
};

// ffmpeg -i rtsp://127.0.0.1:8554/test -vf "scale=640:480" -f null -
// ./test-launch2 "( videotestsrc ! x264enc ! rtph264pay name=pay0 pt=96 )"
// gcc test-launch.c -o test-launch2 $(pkg-config --cflags --libs gstreamer-rtsp-server-1.0 gstreamer-1.0)

#endif // MAINWINDOW_H