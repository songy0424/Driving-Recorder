#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include "camera.h"
#include <QLabel>
#include <QTimer>

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

private:
    Ui::MainWindow *ui;
    Camera *camera;
    QLabel *imageLabel;
    QTimer *timer;
    void setupUi();
};

#endif // MAINWINDOW_H