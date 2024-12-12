#include "mainwindow.h"
#include "ui_mainwindow.h"
#include <QVBoxLayout>
#include <QFileDialog>
#include <QMessageBox>
#include <QDateTime>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent),
                                          ui(new Ui::MainWindow),
                                          camera(new Camera(this)),
                                          //   imageLabel(new QLabel(this)),
                                          timer(new QTimer(this))
{
    ui->setupUi(this);
    connect(ui->snapshotButton, &QPushButton::clicked, this, &MainWindow::takeSnapshot);
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
        qDebug("�޷�������ͷ");
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

// void MainWindow::setupUi()
// {
//     QVBoxLayout *layout = new QVBoxLayout(this);
//     imageLabel->setAlignment(Qt::AlignCenter);
//     imageLabel->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
//     layout->addWidget(imageLabel);

//     snapshotButton = new QPushButton("Take Snapshot", this);
//     layout->addWidget(snapshotButton, 0, Qt::AlignCenter);
//     connect(snapshotButton, &QPushButton::clicked, this, &MainWindow::takeSnapshot);
//     this->setLayout(layout);
// }

void MainWindow::onCameraImageCaptured(const cv::Mat &image)
{
    QImage qImage(image.data, image.cols, image.rows, static_cast<int>(image.step), QImage::Format_RGB888);

    // Ȼ����� rgbSwapped() ����
    QImage swappedImage = qImage.rgbSwapped();

    // ���ת����� QImage ���õ� QLabel ��
    ui->imageLabel->setPixmap(QPixmap::fromImage(swappedImage));
}

void MainWindow::takeSnapshot()
{
    QString directory = "/home/nvidia/my_project/new_camera"; // �̶������ַ
    QDir().mkpath(directory);
    QDateTime dateTime = QDateTime::currentDateTime();                // ʹ��QDateTime                          // ȷ��Ŀ¼����
    QString fileName = dateTime.toString("yyyyMMdd_HHmmss") + ".jpg"; // �ļ���Ϊ��ǰʱ�䣬��ʽΪ YYMMDD_HHmmss

    QString filePath = directory + "/" + fileName;

    const QPixmap *pixmap = ui->imageLabel->pixmap();
    (*pixmap).save(filePath, "JPEG");
}