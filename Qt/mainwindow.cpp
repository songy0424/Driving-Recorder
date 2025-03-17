#include "mainwindow.h"
#include "ui_mainwindow.h"
#include <QVBoxLayout>
#include <QFileDialog>
#include <QMessageBox>
#include <QDateTime>
#include <QPainter>
#include <QProcess>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent),
                                          ui(new Ui::MainWindow),
                                          camera(new Camera(this)),
                                          timer(new QTimer(this)),
                                          isSaveImage(false),
                                          isRecordVideo(false),
                                          videoTimer(new QTimer(this)),
                                          timeTimer(new QTimer(this)),
                                          width(1280),   // 默认分辨率宽度
                                          height(720),   // 默认分辨率高度
                                          frameRate(30), // 默认帧率
                                          rtspServer(nullptr),
                                          factory(nullptr),
                                          appsrc(nullptr)
{
    ui->setupUi(this);
    ui->label->setStyleSheet("QLabel{background-color:rgb(255,0,0);}");

    settingsPage = new SettingsPage(this);                                                 // 创建设置页面实例
    connect(settingsPage, &SettingsPage::returnToMainWindow, this, &MainWindow::showMain); // 连接信号和槽
    connect(settingsPage, &SettingsPage::resolutionChanged, this, &MainWindow::updateResolution);
    connect(settingsPage, &SettingsPage::photoIntervalChanged, this, &MainWindow::updatePhotoInterval);
    connect(ui->testButton, &QPushButton::clicked, this, &MainWindow::showSettings); // 连接信号和槽
    connect(ui->snapshotButton, &QPushButton::clicked, this, &MainWindow::slot_Photograph);
    connect(ui->recordButton, &QPushButton::clicked, this, &MainWindow::slot_RecordVideo);

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
    recordingLabel_1->move(15, 40); // 移动到imageLabel的左上角
    recordingLabel_2->move(45, 35); // 移动到imageLabel的左上角
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

    // 初始化 GStreamer
    gst_init(nullptr, nullptr);
    // 连接WiFi状态变化信号
    connect(settingsPage, &SettingsPage::wifiStateChanged, this, &MainWindow::onWifiStateChanged);
    this->showFullScreen();
    connect(timeTimer, &QTimer::timeout, this, &MainWindow::updateTime); // 连接信号和槽
    connect(ui->testButton, &QPushButton::clicked, this, &MainWindow::showNormal);
    timer->start(int(1000 / frameRate)); // 捕获一帧的定时器
    timeTimer->start(1000);
}

MainWindow::~MainWindow()
{
    camera->closeCamera();
    if (appsrc)
    {
        gst_element_set_state(appsrc, GST_STATE_NULL);
        gst_object_unref(appsrc);
    }
    if (factory)
    {
        g_object_unref(factory);
    }
    if (rtspServer)
    {
        g_object_unref(rtspServer);
    }
    delete settingsPage; // 确保删除设置页面
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
        addTimestamp(frame);
        if (appsrc)
        {
            GstBuffer *buffer = gst_buffer_new_allocate(nullptr, frame.total() * frame.elemSize(), nullptr);

            GstMapInfo map;
            gst_buffer_map(buffer, &map, GST_MAP_WRITE);
            memcpy(map.data, frame.data, map.size);
            gst_buffer_unmap(buffer, &map);

            // 设置时间戳（单位：纳秒）
            GST_BUFFER_PTS(buffer) = gst_util_uint64_scale(frameCount, GST_SECOND, 30);
            GST_BUFFER_DURATION(buffer) = gst_util_uint64_scale(1, GST_SECOND, 30);

            frameCount++;

            // 推送缓冲区
            GstFlowReturn ret;
            g_signal_emit_by_name(appsrc, "push-buffer", buffer, &ret);
            gst_buffer_unref(buffer);
        }
        // else
        //     qDebug() << "Push buffer to appsrc Success";
        // }
        // else
        // {
        //     qDebug() << "can not open src";
        // }

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
    QProcess process;
    // QString directory = "/home/nvidia/my_project/new_camera";         // 固定保存地址
    QString directory = "/mnt/myvideo/Picture";
    QDir().mkpath(directory);                                         // 确保目录存在
    QDateTime dateTime = QDateTime::currentDateTime();                // 使用QDateTime
    QString fileName = dateTime.toString("yyyyMMdd_HHmmss") + ".jpg"; // 文件名为当前时间，格式为 YYMMDD_HHmmss

    QString filePath = directory + "/" + fileName;

    qImage.save(filePath, "JPEG");
    QString command = "sudo chmod 777 /mnt/myvideo/Picture -R";
    process.start(command);
    process.waitForFinished();
}

void MainWindow::slot_RecordVideo()
{
    QProcess process;
    if (!videorecord.isOpened() && isRecordVideo == false)
    {
        QString directory = "/mnt/myvideo/Video"; // 指定视频保存目录
        QString command = "sudo chmod 777 /mnt/myvideo/Video -R";
        QDir().mkpath(directory); // 确保目录存在

        QString video_name = directory + "/" + QDateTime::currentDateTime().toString("yyyy-MM-dd_HH-mm-ss") + ".mp4"; // 拼接完整路径
        std::string gst_out = "appsrc ! video/x-raw, format=BGR ! queue ! videoconvert ! video/x-raw,format=BGRx ! nvvidconv ! nvv4l2h264enc ! h264parse ! qtmux ! filesink location=" + video_name.toStdString();
        videorecord.open(gst_out, cv::CAP_GSTREAMER, 0, frameRate, cv::Size(width, height));
        process.start(command);
        process.waitForFinished();
        if (videorecord.isOpened())
        {
            isRecordVideo = true;
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
    QProcess process;
    QString directory = "/mnt/myvideo/Video"; // 指定视频保存目录
    QString command = "sudo chmod 777 /mnt/myvideo/Video -R";
    QDir().mkpath(directory);                                                                                     // 确保目录存在
    QString video_name = directory + "/" + QDateTime::currentDateTime().toString("yyyy-MM-dd_HH-mm-ss") + ".mp4"; // 拼接完整路径
    std::string gst_out = "appsrc ! video/x-raw, format=BGR ! queue ! videoconvert ! video/x-raw,format=BGRx ! nvvidconv ! nvv4l2h264enc ! h264parse ! qtmux ! filesink location=" + video_name.toStdString();

    // 关闭当前视频文件
    videorecord.release();

    // 重新打开新的视频文件
    videorecord.open(gst_out, cv::CAP_GSTREAMER, 0, frameRate, cv::Size(width, height));
    process.start(command);
    process.waitForFinished();
}

void MainWindow::showSettings()
{
    this->hide();                                                       // 隐藏主窗口
    settingsPage->setWindowFlags(Qt::FramelessWindowHint | Qt::Window); // 设置无边框窗口
    settingsPage->show();                                               // 显示设置页面
    settingsPage->showFullScreen();                                     // 设置窗口为全屏
}

void MainWindow::showMain()
{
    settingsPage->hide(); // 隐藏设置页面
    this->show();         // 显示主窗口
}

void MainWindow::updateResolution(int width, int height, int frameRate)
{
    // 更新摄像头分辨率
    this->width = width; // 这里只是示例，实际中您可能需要更新全局变量或成员变量
    this->height = height;
    this->frameRate = frameRate; // 假设帧率保持不变
    std::string pipeline = "nvarguscamerasrc sensor-id=0 ! video/x-raw(memory:NVMM), width=(int)" +
                           std::to_string(width) + ", height=(int)" +
                           std::to_string(height) + ", format=(string)NV12, framerate=(fraction)" +
                           std::to_string(frameRate) + "/1 ! "
                                                       "nvvidconv flip-method=0 ! video/x-raw, width=(int)" +
                           std::to_string(width) + ", height=(int)" +
                           std::to_string(height) + ", format=(string)BGRx ! "
                                                    "videoconvert ! video/x-raw, format=(string)BGR ! appsink";

    camera->closeCamera();
    if (!camera->openCamera(pipeline))
    {
        qDebug("无法打开摄像头");
    }
}

void MainWindow::addTimestamp(cv::Mat &frame)
{
    std::string timestamp = QDateTime::currentDateTime().toString("yyyy-MM-dd HH:mm:ss").toStdString();
    cv::putText(frame, timestamp, cv::Point(frame.cols - 385, 50), cv::FONT_HERSHEY_SIMPLEX, 1, cv::Scalar(255, 255, 255), 2);
}

void MainWindow::updatePhotoInterval(int interval)
{
    // 更新定时器时间
    videoTimer->start(interval * 1000); // 将分钟转换为毫秒
    qDebug("摄影间隔时间修改为 %d 分钟", interval / 60);
}
void MainWindow::onWifiStateChanged(bool isActive, const QString &ipAddress)
{
    if (isActive)
    {
        // WiFi已开启，启动RTSP服务器
        startRTSPServer(ipAddress);
    }
    else
    {
        // WiFi已关闭，停止RTSP服务器
        stopRTSPServer();
    }
}

void MainWindow::startRTSPServer(const QString &ipAddress)
{

    // 创建 RTSP 服务器
    rtspServer = gst_rtsp_server_new();
    g_object_set(rtspServer,
                 "address", ipAddress.toStdString().c_str(),
                 "service", "8554",
                 nullptr);

    // 创建媒体工厂
    factory = gst_rtsp_media_factory_new();
    const gchar *launch =
        "appsrc name=mysrc is-live=true format=time ! "
        "videoconvert ! nvvidconv ! "
        "nvv4l2h264enc ! h264parse ! rtph264pay config-interval=1 name=pay0 pt=96";

    // 关键修改：通过媒体对象获取管道
    gst_rtsp_media_factory_set_launch(factory, launch);
    gst_rtsp_media_factory_set_shared(factory, TRUE);

    g_signal_connect(factory, "media-configure", G_CALLBACK(MainWindow::media_configure), this);

    // 挂载到服务器
    GstRTSPMountPoints *mounts = gst_rtsp_server_get_mount_points(rtspServer);
    gst_rtsp_mount_points_add_factory(mounts, "/stream", factory);
    g_object_unref(mounts);
    // 启动服务
    gst_rtsp_server_attach(rtspServer, NULL);
    qDebug() << "RTSP server at rtsp://192.168.1.1:8554/stream\n";
}

void MainWindow::media_configure(GstRTSPMediaFactory *factory, GstRTSPMedia *media, gpointer user_data)
{
    MainWindow *self = static_cast<MainWindow *>(user_data);
    GstElement *element, *appsrc;
    GstCaps *caps;

    // 获取媒体元素
    element = gst_rtsp_media_get_element(media);

    // 查找 appsrc 元素
    appsrc = gst_bin_get_by_name_recurse_up(GST_BIN(element), "mysrc");
    if (!appsrc)
    {
        qDebug() << "Failed to find appsrc in media element";
        gst_object_unref(element);
        return;
    }

    // 设置 appsrc 属性
    g_object_set(appsrc,
                 "is-live", TRUE,
                 "format", GST_FORMAT_TIME,
                 nullptr);
    g_object_set(appsrc,
                 "max-latency", 1 * GST_MSECOND, // 最大延迟1毫秒
                 "max-bytes", 0,                 // 禁用缓冲区大小限制
                 nullptr);
    // 设置视频能力
    caps = gst_caps_new_simple("video/x-raw",
                               "format", G_TYPE_STRING, "BGR",
                               "width", G_TYPE_INT, self->width,
                               "height", G_TYPE_INT, self->height,
                               "framerate", GST_TYPE_FRACTION, 30, 1,
                               nullptr);
    g_object_set(appsrc, "caps", caps, nullptr);
    gst_caps_unref(caps);

    // 保存 appsrc 指针
    self->appsrc = appsrc;

    // 释放媒体元素引用
    gst_object_unref(element);
}

void MainWindow::stopRTSPServer()
{
    if (appsrc)
    {
        gst_element_set_state(appsrc, GST_STATE_NULL);
        gst_object_unref(appsrc);
        appsrc = nullptr;
    }
    if (factory)
    {
        g_object_unref(factory);
        factory = nullptr;
    }
    if (rtspServer)
    {
        g_object_unref(rtspServer);
        rtspServer = nullptr;
    }

    qDebug() << "RTSP server stoped!\n";
}