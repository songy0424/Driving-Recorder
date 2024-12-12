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
    void takeSnapshot(); // 拍照槽函数
    void updateTime();

private:
    Ui::MainWindow *ui;
    Camera *camera;
    QLabel *imageLabel;
    QTimer *timer;
    QLabel *timeLabel;           // 用于显示时间的QLabel
    QTimer *timeTimer;           // 用于更新时间的QTimer
    QPushButton *snapshotButton; // 拍照按钮
};

#endif // MAINWINDOW_H