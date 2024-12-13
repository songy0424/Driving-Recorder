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

    std::string pipeline = "nvarguscamerasrc ! video/x-raw(memory:NVMM), width=(int)1920, height=(int)1080, format=(string)NV12, framerate=(fraction)30/1 ! "
                           "nvvidconv flip-method=0 ! video/x-raw, width=(int)1920, height=(int)1080, format=(string)BGRx ! "
                           "videoconvert ! video/x-raw, format=(string)BGR ! appsink";
    // usb camera
    // std::string pipeline = "v4l2src device=/dev/video1 ! video/x-raw, format=YUY2, width=640, height=480, framerate=30/1 ! videoconvert ! appsink";

    if (!camera->openCamera(pipeline))
    {
        qDebug("�޷�������ͷ");
    }
    connect(timer, &QTimer::timeout, this, &MainWindow::processFrame);
    connect(camera, &Camera::imageCaptured, this, &MainWindow::onCameraImageCaptured);
    timer->start(33); // Capture a new frame every 33ms

    timeLabel = new QLabel(this);
    timeLabel->setFixedSize(150, 30);                                                                                        // ���ù̶���С
    timeLabel->move(ui->imageLabel->width() - timeLabel->width() - 10, ui->imageLabel->height() - timeLabel->height() - 10); // �ƶ���imageLabel�����½�
    timeLabel->setStyleSheet("QLabel { color: white; font-size: 12pt; background-color: transparent; }");                    // ������ʽ
    timeLabel->hide();                                                                                                       // ��ʼʱ����

    connect(timeTimer, &QTimer::timeout, this, &MainWindow::updateTime); // �����źźͲ�
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
    timeLabel->adjustSize(); // ������С����Ӧ�ı�
    timeLabel->show();       // ��ʾʱ���ǩ
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
        // ��֡�����㷨����
        // performAlgorithmOnFrame(frame);
        QImage swappedImage = qImage.rgbSwapped(); // ԭ��֡��RGB��ʽ��������������BGR��ʽ

        // ��������֡��ʾ��QLabel��
        displayFrameOnLabel(swappedImage);

        // ����Ƿ���Ҫ����
        if (isSaveImage)
        {
            takeSnapshot(swappedImage);
        }
    }
}

void MainWindow::displayFrameOnLabel(QImage qImage)
{
    QPixmap pixmap = QPixmap::fromImage(qImage);
    QPixmap scaledPixmap = pixmap.scaled(ui->imageLabel->size(), Qt::KeepAspectRatio, Qt::SmoothTransformation);
    ui->imageLabel->setPixmap(scaledPixmap);
    updateTime();
}

void MainWindow::takeSnapshot(QImage qImage)
{
    QString directory = "/home/nvidia/my_project/new_camera"; // �̶������ַ
    QDir().mkpath(directory);
    QDateTime dateTime = QDateTime::currentDateTime();                // ʹ��QDateTime                          // ȷ��Ŀ¼����
    QString fileName = dateTime.toString("yyyyMMdd_HHmmss") + ".jpg"; // �ļ���Ϊ��ǰʱ�䣬��ʽΪ YYMMDD_HHmmss

    QString filePath = directory + "/" + fileName;

    // const QPixmap *pixmap = ui->imageLabel->pixmap();
    qImage.save(filePath, "JPEG");
    isSaveImage = false;
    // updateTime(); // ������ʱ����ʱ��
}