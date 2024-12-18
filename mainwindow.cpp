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
                                          videoTimer(new QTimer(this)),
                                          timeTimer(new QTimer(this))
{
    ui->setupUi(this);
    ui->label->setStyleSheet("QLabel{background-color:rgb(255,0,0);}");
    connect(ui->snapshotButton, &QPushButton::clicked, this, &MainWindow::slot_Photograph);
    connect(ui->recordButton, &QPushButton::clicked, this, &MainWindow::slot_RecordVideo);
    width = 1280;
    height = 720;
    frameRate = 30;
    std::string pipeline = "nvarguscamerasrc sensor-id=0 ! video/x-raw(memory:NVMM), width=(int)" +
                           std::to_string(width) + ", height=(int)" +
                           std::to_string(height) + ", format=(string)NV12, framerate=(fraction)" +
                           std::to_string(frameRate) + "/1 ! "
                                                       "nvvidconv flip-method=0 ! video/x-raw, width=(int)" +
                           std::to_string(width) + ", height=(int)" +
                           std::to_string(height) + ", format=(string)BGRx ! "
                                                    "videoconvert ! video/x-raw, format=(string)BGR ! appsink";
    // std::string pipeline = "v4l2src device=/dev/video1 ! video/x-raw, format=YUY2, width=640, height=480, framerate=30/1 ! videoconvert ! appsink";

    if (!camera->openCamera(pipeline))
    {
        qDebug("无法打开摄像头");
    }
    connect(timer, &QTimer::timeout, this, &MainWindow::processFrame);

    timeLabel = new QLabel(this);
    timeLabel->setFixedSize(150, 30);                                                                     // 设置固定大小
    timeLabel->move(ui->imageLabel->width() - timeLabel->width() - 10, 0);                                // 移动到imageLabel的右下角
    timeLabel->setStyleSheet("QLabel { color: white; font-size: 12pt; background-color: transparent; }"); // 设置样式
    timeLabel->hide();                                                                                    // 初始时隐藏

    recordingLabel_1 = new QLabel(this); // 初始化录制中的标签
    recordingLabel_2 = new QLabel(this); // 初始化录制中的标签
    recordingLabel_2->setText("录像中");
    recordingLabel_2->setFixedSize(150, 30);
    recordingLabel_1->move(15, 15); // 移动到imageLabel的左上角
    recordingLabel_2->move(45, 10); // 移动到imageLabel的左上角
    const QString label_style =
        "min-width:20px;min-height:20px;max-width:20px;max-height:20px;border-radius:10px;border:1px solid black;background:red";
    recordingLabel_1->setStyleSheet(label_style);
    recordingLabel_2->setStyleSheet("QLabel { color: white; font-size: 15pt; background-color: transparent; }");
    recordingLabel_1->hide(); // 初始时隐藏
    recordingLabel_2->hide(); // 初始时隐藏

    startIcon = QIcon("/home/nvidia/my_project/new_camera/image/start.png");         // 假设图标文件名为 start.png
    stopIcon = QIcon("/home/nvidia/my_project/new_camera/image/stop.png");           // 假设图标文件名为 stop.png
    takePhotoIcon = QIcon("/home/nvidia/my_project/new_camera/image/takePhoto.png"); // 假设图标文件名为 stop.png
    settingIcon = QIcon("/home/nvidia/my_project/new_camera/image/setting.png");     // 假设图标文件名为 stop.png

    ui->recordButton->setStyleSheet("");
    ui->recordButton->setStyleSheet("QPushButton { border:none; background-color: transparent; }");
    ui->recordButton->setIcon(startIcon);
    ui->recordButton->setIconSize(QSize(80, 80)); // 设置图标大小

    ui->snapshotButton->setStyleSheet("");
    ui->snapshotButton->setStyleSheet("QPushButton { border:none; background-color: transparent; }");
    ui->snapshotButton->setIcon(takePhotoIcon);
    ui->snapshotButton->setIconSize(QSize(80, 80));

    ui->testButton->setStyleSheet("");
    ui->testButton->setStyleSheet("QPushButton { border:none; background-color: transparent; }");
    ui->testButton->setIcon(settingIcon);
    ui->testButton->setIconSize(QSize(80, 80));

    this->showFullScreen();
    connect(timeTimer, &QTimer::timeout, this, &MainWindow::updateTime); // 连接信号和槽
    connect(ui->testButton, &QPushButton::clicked, this, &MainWindow::showNormal);
    timer->start(int(1000 / frameRate)); // 捕获一帧的定时器
    timeTimer->start(1000);
}

MainWindow::~MainWindow()
{
    camera->closeCamera();
    delete ui;
}

void MainWindow::updateTime()
{
    QString timeString = QDateTime::currentDateTime().toString("yyyy/MM/dd HH:mm:ss");
    timeLabel->setText(timeString);
    timeLabel->adjustSize(); // 调整大小以适应文本
    timeLabel->show();       // 显示时间标签
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
        QString video_name = QString("%1.mp4").arg(QDateTime::currentDateTime().toString("yyyy-MM-dd_HH-mm-ss"));
        std::string gst_out = "appsrc ! video/x-raw, format=BGR ! queue ! videoconvert ! video/x-raw,format=BGRx ! nvvidconv ! nvv4l2h264enc ! h264parse ! qtmux ! filesink location=" + video_name.toStdString();

        videorecord.open(gst_out, cv::CAP_GSTREAMER, 0, frameRate, cv::Size(width, height));

        if (videorecord.isOpened())
        {
            isRecordVideo = true;
            // ui->recordButton->setText("结束录制");
            ui->recordButton->setIcon(stopIcon);

            recordingLabel_1->show(); // 显示录制中的标签
            recordingLabel_2->show(); // 显示录制中的标签

            connect(videoTimer, &QTimer::timeout, this, &MainWindow::updateVideoFile);
            videoTimer->start(60000); // 设置定时器时间为60000毫秒（1分钟）
        }
    }
    else if (videorecord.isOpened() && isRecordVideo)
    {
        // ui->recordButton->setText("视频录制");
        ui->recordButton->setIcon(startIcon);
        videorecord.release();
        videoTimer->stop(); // 停止定时器
        isRecordVideo = false;
        recordingLabel_1->hide(); // 隐藏录制中的标签
        recordingLabel_2->hide(); // 隐藏录制中的标签
    }
}

void MainWindow::slot_SaveVideo(const cv::Mat &image)
{
    if (isRecordVideo && videorecord.isOpened())
    {
        videorecord.write(image);
    }
}
void MainWindow::updateVideoFile()
{
    // 生成新的视频文件名
    QString video_name = QString("%1.mp4").arg(QDateTime::currentDateTime().toString("yyyy-MM-dd_HH-mm-ss"));
    std::string gst_out = "appsrc ! video/x-raw, format=BGR ! queue ! videoconvert ! video/x-raw,format=BGRx ! nvvidconv ! nvv4l2h264enc ! h264parse ! qtmux ! filesink location=" + video_name.toStdString();

    // 关闭当前视频文件
    videorecord.release();

    // 重新打开新的视频文件
    videorecord.open(gst_out, cv::CAP_GSTREAMER, 0, frameRate, cv::Size(width, height));
}
