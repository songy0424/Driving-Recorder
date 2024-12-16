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
    void takeSnapshot(const QImage &qImage); // 拍照槽函数
    void updateTime();
    void processFrame();
    void displayFrameOnLabel(const QImage &qImage);
    void slot_Photograph();
    void slot_RecordVideo();
    void slot_SaveVideo(cv::Mat image);
    void updateVideoFile();

private:
    Ui::MainWindow *ui;
    Camera *camera;
    QLabel *imageLabel;
    QTimer *timer;
    QLabel *timeLabel; // 用于显示时间的QLabel
    QTimer *timeTimer; // 用于更新时间的QTimer
    bool isSaveImage;
    bool isRecordVideo;
    cv::VideoWriter videorecord;
};

#endif // MAINWINDOW_H