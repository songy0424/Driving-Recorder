#include "mainwindow.h"
#include "ui_mainwindow.h"
#include <QVBoxLayout>
#include <QFileDialog>
#include <QMessageBox>
#include <QDateTime>
#include <QPainter>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent),
                                          ui(new Ui::MainWindow),
                                          camera(new Camera(this)),
                                          timer(new QTimer(this)),
                                          isSaveImage(false),
                                          isRecordVideo(false),
                                          timeTimer(new QTimer(this))
{
    ui->setupUi(this);

    connect(ui->snapshotButton, &QPushButton::clicked, this, &MainWindow::slot_Photograph);
    connect(ui->recordButton, &QPushButton::clicked, this, &MainWindow::slot_RecordVideo);
    std::string pipeline = "nvarguscamerasrc sensor-id=0 ! video/x-raw(memory:NVMM), width=(int)1920, height=(int)1080, format=(string)NV12, framerate=(fraction)30/1 ! "
                           "nvvidconv flip-method=0 ! video/x-raw, width=(int)1920, height=(int)1080, format=(string)BGRx ! "
                           "videoconvert ! video/x-raw, format=(string)BGR ! appsink";
    // usb camera
    // std::string pipeline = "v4l2src device=/dev/video1 ! video/x-raw, format=YUY2, width=640, height=480, framerate=30/1 ! videoconvert ! appsink";

    if (!camera->openCamera(pipeline))
    {
        qDebug("无法打开摄像头");
    }
    connect(timer, &QTimer::timeout, this, &MainWindow::processFrame);
    connect(camera, &Camera::imageCaptured, this, &MainWindow::onCameraImageCaptured);
    timer->start(33); // Capture a new frame every 33ms

    timeLabel = new QLabel(this);
    timeLabel->setFixedSize(150, 30);                                                                                        // 设置固定大小
    timeLabel->move(ui->imageLabel->width() - timeLabel->width() - 10, ui->imageLabel->height() - timeLabel->height() - 10); // 移动到imageLabel的右下角
    timeLabel->setStyleSheet("QLabel { color: white; font-size: 12pt; background-color: transparent; }");                    // 设置样式
    timeLabel->hide();                                                                                                       // 初始时隐藏

    connect(timeTimer, &QTimer::timeout, this, &MainWindow::updateTime); // 连接信号和槽
    this->showFullScreen();
    connect(ui->testButton, &QPushButton::clicked, this, &MainWindow::showNormal);
    timeTimer->start(1000);
}

MainWindow::~MainWindow()
{
    delete ui;
}

void MainWindow::updateTime()
{
    QString timeString = QDateTime::currentDateTime().toString("yyyy/MM/dd HH:mm:ss");
    timeLabel->setText(timeString);
    timeLabel->adjustSize(); // 调整大小以适应文本
    timeLabel->show();       // 显示时间标签
}

void MainWindow::onCameraImageCaptured(const cv::Mat &image)
{
}

void MainWindow::slot_Photograph()
{
    isSaveImage = true;
}

void MainWindow::processFrame()
{
    cv::Mat frame;
    if (camera->grabFrame(frame))
    {
        QImage qImage(frame.data, frame.cols, frame.rows, static_cast<int>(frame.step), QImage::Format_RGB888);
        // 对帧进行算法操作
        // performAlgorithmOnFrame(frame);
        QImage swappedImage = qImage.rgbSwapped(); // 原本帧是RGB格式，经过函数后编程BGR格式

        // 将处理后的帧显示在QLabel上
        displayFrameOnLabel(swappedImage);

        // 检查是否需要拍照
        if (isSaveImage)
        {
            takeSnapshot(swappedImage);
            isSaveImage = false;
        }
        if (isRecordVideo)
        {
            slot_SaveVideo(frame); // 将原始帧保存到视频文件
        }
    }
}

void MainWindow::displayFrameOnLabel(const QImage &qImage)
{
    QPixmap pixmap = QPixmap::fromImage(qImage);
    QPixmap scaledPixmap = pixmap.scaled(ui->imageLabel->size(), Qt::KeepAspectRatio, Qt::SmoothTransformation);
    ui->imageLabel->setPixmap(scaledPixmap);
    updateTime();
}

void MainWindow::takeSnapshot(const QImage &qImage)
{
    QString directory = "/home/nvidia/my_project/new_camera";         // 固定保存地址
    QDir().mkpath(directory);                                         // 确保目录存在
    QDateTime dateTime = QDateTime::currentDateTime();                // 使用QDateTime
    QString fileName = dateTime.toString("yyyyMMdd_HHmmss") + ".jpg"; // 文件名为当前时间，格式为 YYMMDD_HHmmss

    QString filePath = directory + "/" + fileName;

    qImage.save(filePath, "JPEG");
}

void MainWindow::slot_RecordVideo()
{
    if (!videorecord.isOpened() && isRecordVideo == false)
    {
        // QString resolution = ui->comboBox_resolution->currentText();
        // int width = resolution.split("X").at(0).toInt();
        // int height = resolution.split("X").at(1).toInt();
        int width = 1920;
        int height = 1080;
        cv::Size _size = cv::Size(width, height);

        QString video_name = QString("%1.mp4").arg(QDateTime::currentDateTime().toString("yyyy-MM-dd_HH-mm-ss"));

        videorecord.open(video_name.toStdString(), cv::VideoWriter::fourcc('m', 'p', '4', 'v'), 30, _size, true);
        if (videorecord.isOpened())
        {
            isRecordVideo = true;
            ui->recordButton->setText("结束录制");
            // ui->pushButton_recordvideo->setText("结束录制");
        }
    }
    else if (videorecord.isOpened() && isRecordVideo)
    {
        ui->recordButton->setText("视频录制");
        // ui->pushButton_recordvideo->setText("视频录制");
        videorecord.release();
        isRecordVideo = false;
    }
}

void MainWindow::slot_SaveVideo(cv::Mat image)
{
    if (isRecordVideo && videorecord.isOpened())
    {
        videorecord.write(image);
    }
}