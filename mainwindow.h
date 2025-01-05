#ifndef MAINWINDOW_H
#define MAINWINDOW_H

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

private:
    int width;     // 类的成员变量，用于宽度
    int height;    // 类的成员变量，用于高度
    int frameRate; // 类的成员变量，用于帧率
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
};

#endif // MAINWINDOW_H