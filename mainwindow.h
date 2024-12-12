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
private:
    Ui::MainWindow *ui;
    Camera *camera;
    QLabel *imageLabel;
    QTimer *timer;
    QPushButton *snapshotButton; // 拍照按钮
    void setupUi();
};

#endif // MAINWINDOW_H