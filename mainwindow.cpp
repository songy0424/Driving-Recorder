#include "mainwindow.h"
#include "ui_mainwindow.h"
#include <QVBoxLayout>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent),
                                          ui(new Ui::MainWindow),
                                          camera(new Camera(this)),
                                          imageLabel(new QLabel(this)),
                                          timer(new QTimer(this))
{
    ui->setupUi(this);
    setCentralWidget(imageLabel);
    std::string pipeline = "nvarguscamerasrc ! video/x-raw(memory:NVMM), width=(int)1920, height=(int)1080, format=(string)NV12, framerate=(fraction)30/1 ! "
                           "nvvidconv flip-method=0 ! video/x-raw, width=(int)1280, height=(int)720, format=(string)BGRx ! "
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
}

MainWindow::~MainWindow()
{
    delete ui;
}

void MainWindow::setupUi()
{
    QVBoxLayout *layout = new QVBoxLayout;
    imageLabel->setAlignment(Qt::AlignCenter);
    layout->addWidget(imageLabel);
    setLayout(layout);
}

void MainWindow::onCameraImageCaptured(const cv::Mat &image)
{
    QImage qImage(image.data, image.cols, image.rows, static_cast<int>(image.step), QImage::Format_RGB888);

    // 然后调用 rgbSwapped() 方法
    QImage swappedImage = qImage.rgbSwapped();

    // 最后将转换后的 QImage 设置到 QLabel 上
    imageLabel->setPixmap(QPixmap::fromImage(swappedImage));
}
