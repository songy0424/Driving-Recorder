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
                                          timeTimer(new QTimer(this))
{
    ui->setupUi(this);
    connect(ui->snapshotButton, &QPushButton::clicked, this, &MainWindow::takeSnapshot);
    // 每秒更新时间
    // ui->imageLabel->setAlignment(Qt::AlignCenter);
    // ui->imageLabel->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    // setCentralWidget(imageLabel);
    std::string pipeline = "nvarguscamerasrc ! video/x-raw(memory:NVMM), width=(int)1920, height=(int)1080, format=(string)NV12, framerate=(fraction)30/1 ! "
                           "nvvidconv flip-method=0 ! video/x-raw, width=(int)1920, height=(int)1080, format=(string)BGRx ! "
                           "videoconvert ! video/x-raw, format=(string)BGR ! appsink";
    /*
    std::string pipeline = "v4l2src device=/dev/video1 ! video/x-raw, format=YUY2, width=640, height=480, framerate=30/1 ! videoconvert ! appsink";
    */
    if (!camera->openCamera(pipeline))
    {
        qDebug("无法打开摄像头");
    }
    else
    {
        connect(camera, &Camera::imageCaptured, this, &MainWindow::onCameraImageCaptured);
        connect(timer, &QTimer::timeout, [this]()
                {
            cv::Mat frame;
            if (camera->grabFrame(frame)) {
                emit camera->imageCaptured(frame);
            } });
        timer->start(33); // Capture a new frame every 33ms
    }
    timeLabel = new QLabel(this);
    timeLabel->setFixedSize(150, 30);                                                                                        // 设置固定大小
    timeLabel->move(ui->imageLabel->width() - timeLabel->width() - 10, ui->imageLabel->height() - timeLabel->height() - 10); // 移动到imageLabel的右下角
    timeLabel->setStyleSheet("QLabel { color: white; font-size: 12pt; background-color: transparent; }");                    // 设置样式
    timeLabel->hide();                                                                                                       // 初始时隐藏

    connect(timeTimer, &QTimer::timeout, this, &MainWindow::updateTime); // 连接信号和槽
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
    QImage qImage(image.data, image.cols, image.rows, static_cast<int>(image.step), QImage::Format_RGB888);

    // 然后调用 rgbSwapped() 方法
    QImage swappedImage = qImage.rgbSwapped();

    // 最后将转换后的 QImage 设置到 QLabel 上
    ui->imageLabel->setPixmap(QPixmap::fromImage(swappedImage));
    updateTime(); // 在图像捕获时更新时间
}

void MainWindow::takeSnapshot()
{
    QString directory = "/home/nvidia/my_project/new_camera"; // 固定保存地址
    QDir().mkpath(directory);
    QDateTime dateTime = QDateTime::currentDateTime();                // 使用QDateTime                          // 确保目录存在
    QString fileName = dateTime.toString("yyyyMMdd_HHmmss") + ".jpg"; // 文件名为当前时间，格式为 YYMMDD_HHmmss

    QString filePath = directory + "/" + fileName;

    const QPixmap *pixmap = ui->imageLabel->pixmap();
    (*pixmap).save(filePath, "JPEG");
    updateTime(); // 在拍照时更新时间
}