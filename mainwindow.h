#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include "camera.h"
#include <QLabel>
#include <QTimer>
#include <QPushButton>

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
    void onCameraImageCaptured(const cv::Mat &image);
    void takeSnapshot(QImage qImage); // 拍照槽函数
    void updateTime();
    void processFrame();
    void displayFrameOnLabel(QImage qImage);
    void slot_Photograph();

private:
    Ui::MainWindow *ui;
    Camera *camera;
    QLabel *imageLabel;
    QTimer *timer;
    QLabel *timeLabel; // 用于显示时间的QLabel
    QTimer *timeTimer; // 用于更新时间的QTimer
    bool isSaveImage;
    bool isRecordVideo;
};

#endif // MAINWINDOW_H