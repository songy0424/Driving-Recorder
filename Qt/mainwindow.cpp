#include "mainwindow.h"
#include "ui_mainwindow.h"
#include <QVBoxLayout>
#include <QFileDialog>
#include <QMessageBox>
#include <QDateTime>
#include <QPainter>
#include <QProcess>
#include <opencv2/opencv.hpp>
#include <opencv2/cudaarithm.hpp>
#include <opencv2/cudafilters.hpp>
#include <QtConcurrent/QtConcurrent>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent),
                                          ui(new Ui::MainWindow),
                                          camera(new Camera(this)),
                                          timer(new QTimer(this)),
                                          isSaveImage(false),
                                          isRecordVideo(false),
                                          videoTimer(new QTimer(this)),
                                          timeTimer(new QTimer(this)),
                                          camera_id(0),
                                          width(1280),   // 默认分辨率宽度
                                          height(720),   // 默认分辨率高度
                                          frameRate(30), // 默认帧率
                                          rtspServer(nullptr),
                                          factory(nullptr),
                                          appsrc(nullptr)
{
    ui->setupUi(this);
    ui->label->setStyleSheet("QLabel{background-color:rgb(255,0,0);}");

    settingsPage = new SettingsPage(this);
    connect(settingsPage, &SettingsPage::returnToMainWindow, this, &MainWindow::showMain);
    connect(settingsPage, &SettingsPage::resolutionChanged, this, &MainWindow::updateResolution);
    connect(settingsPage, &SettingsPage::nightModeChanged, this, [this](int camera_id) {
        updateResolution(camera_id, width, height, frameRate);
    });
    connect(settingsPage, &SettingsPage::resolutionChanged, this, &MainWindow::updateStreamResolution);
    connect(settingsPage, &SettingsPage::photoIntervalChanged, this, &MainWindow::updatePhotoInterval);
    connect(settingsPage, &SettingsPage::saveImageTriggered, this, &MainWindow::slot_Photograph);
    connect(settingsPage, &SettingsPage::RecordVideoTriggered, this, &MainWindow::slot_RecordVideo);
    connect(ui->testButton, &QPushButton::clicked, this, &MainWindow::showSettings);
    connect(ui->snapshotButton, &QPushButton::clicked, this, &MainWindow::slot_Photograph);
    connect(ui->recordButton, &QPushButton::clicked, this, &MainWindow::slot_RecordVideo);
    connect(timer, &QTimer::timeout, this, &MainWindow::processFrame);

    settingsPage->loadInitialConfig(); // 加载配置文件，更改设置选项

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

    startIcon = QIcon("/home/nvidia/my_project/new_camera/image/start.png");
    stopIcon = QIcon("/home/nvidia/my_project/new_camera/image/stop.png");
    takePhotoIcon = QIcon("/home/nvidia/my_project/new_camera/image/takePhoto.png");
    settingIcon = QIcon("/home/nvidia/my_project/new_camera/image/setting.png");

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
    clahe_cpu = cv::createCLAHE(1.3, cv::Size(5, 5));
    clahe_gpu = cv::cuda::createCLAHE(1.3, cv::Size(5, 5));
    laplacian_filter_gpu = cv::cuda::createLaplacianFilter(CV_8U, CV_8U, 1);
    bgr_planes_cpu.resize(3);
    bgr_planes_gpu.resize(3);
    clahe_planes_cpu.resize(3);
    clahe_planes_gpu.resize(3);
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
    stopRTSPServer();
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
    cv::Mat tmpframe;
    if (camera->grabFrame(tmpframe))
    {
        cv::Mat& frame = tmpframe;
        cv::Mat processedFrame;
        if (settingsPage->EnhancementActive()) {
            processedFrame = applyCLAHEAndSharpening(tmpframe);
            frame = processedFrame;
        }
        
        if (settingsPage->TimeStampActive()) {
            addTimestamp(frame);
        }

        if (settingsPage->EnhancementActive()) {
            frame = applyCLAHEAndSharpening(tmpframe);
        }
        if (settingsPage->TimeStampActive()) {
            addTimestamp(frame);
        }
        if (appsrc)
        {
            GstBuffer *buffer = gst_buffer_new_wrapped_full(GST_MEMORY_FLAG_READONLY,
                                                            frame.data,
                                                            frame.total() * frame.elemSize(),
                                                            0,
                                                            frame.total() * frame.elemSize(),
                                                            nullptr,
                                                            nullptr);

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
            //     if (ret)
            //         qDebug() << "Push buffer error";
        }

        cv::cvtColor(frame, bgrFrame, cv::COLOR_RGB2BGR); // 确保颜色空间转换正确
        QImage swappedImage(bgrFrame.data, bgrFrame.cols, bgrFrame.rows, static_cast<int>(bgrFrame.step), QImage::Format_RGB888);

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

cv::Mat MainWindow::applyCLAHEAndSharpening(const cv::Mat &frame)
{
    gpu_frame.upload(frame);

    // 分离通道
    cv::cuda::split(gpu_frame, bgr_planes_gpu);

    // 对每个通道应用 CLAHE
    clahe_gpu->apply(bgr_planes_gpu[0], blue_clahe_gpu);
    clahe_gpu->apply(bgr_planes_gpu[1], green_clahe_gpu);
    clahe_gpu->apply(bgr_planes_gpu[2], red_clahe_gpu);

    clahe_planes_gpu[0] = blue_clahe_gpu;
    clahe_planes_gpu[1] = green_clahe_gpu;
    clahe_planes_gpu[2] = red_clahe_gpu;
    cv::cuda::merge(clahe_planes_gpu, clahe_image_gpu);

    clahe_image_gpu.download(sharpened);

    return !sharpened.empty() ? sharpened : cv::Mat();
}

void MainWindow::displayFrameOnLabel(const QImage &qImage)
{
    QPixmap pixmap = QPixmap::fromImage(qImage);
    QPixmap scaledPixmap = pixmap.scaled(ui->imageLabel->size(), Qt::KeepAspectRatio, Qt::FastTransformation);
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

        QString video_name = directory + "/" + QDateTime::currentDateTime().toString("yyyyMMdd_HHmmss") + ".mp4"; // 拼接完整路径
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
            videoTimer->start(interval * 1000); // 设置定时器时间
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
    QDir().mkpath(directory);                                                                                 // 确保目录存在
    QString video_name = directory + "/" + QDateTime::currentDateTime().toString("yyyyMMdd_HHmmss") + ".mp4"; // 拼接完整路径
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

void MainWindow::updateResolution(int camera_id, int width, int height, int frameRate)
{
    // 更新摄像头分辨率
    this->width = width;
    this->height = height;
    this->frameRate = frameRate;
    std::string pipeline = "nvarguscamerasrc sensor-id=" + std::to_string(camera_id) + " ee-mode=1 wbmode=1 ispdigitalgainrange=\"1 16\" ! video/x-raw(memory:NVMM), width=(int)" +
                           std::to_string(width) + ", height=(int)" +
                           std::to_string(height) + ", format=(string)NV12, framerate=(fraction)" +
                           std::to_string(frameRate) + "/1 ! "
                                                       "nvvidconv flip-method=0 ! video/x-raw, width=(int)" +
                           std::to_string(width) + ", height=(int)" +
                           std::to_string(height) + ", format=(string)BGRx ! "
                                                    "videoconvert ! video/x-raw, format=(string)BGR ! appsink";
    /*基础捕获参数
    wbmode：
    说明：调整白平衡以影响照片的色温度。
    设置建议：选择 auto（值为 1），行车过程中光线变化频繁，自动白平衡能让相机根据环境自动调整色彩，保证图像色彩还原准确。
    ispdigitalgainrange：
    说明：调整数字增益范围。
    设置建议：例如设置为 "1 16" ，在低光照环境下提高亮度，同时避免增益过高产生过多噪声。
    exposurecompensation：
    说明：调整曝光补偿。
    设置建议：默认设为 0，遇到特殊光照情况（过亮或过暗），可在 -2 到 2 之间适当调整。
    降噪和增强参数
    ee - mode：
    说明：选择边缘增强模式。
    设置建议：使用 EdgeEnhancement_Fast（值为 1），在不影响处理速度的前提下，增强图像边缘清晰度。
    锁定参数
    */
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
    this->interval = interval;
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

    factory = gst_rtsp_media_factory_new();
    const gchar *launch =
        "appsrc name=mysrc is-live=true format=time ! "
        "videoconvert ! nvvidconv ! "
        "nvv4l2h264enc preset-level=1 bitrate=4000000 insert-sps-pps=true insert-vui=true ! "
        "h264parse config-interval=1 ! "
        "rtph264pay config-interval=1 name=pay0 pt=96 max-nals-per-socket=1";

    // 关键修改：通过媒体对象获取管道
    gst_rtsp_media_factory_set_launch(factory, launch);
    gst_rtsp_media_factory_set_shared(factory, TRUE);

    g_signal_connect(factory, "media-configure", G_CALLBACK(MainWindow::media_configure), this);

    // 挂载到服务器
    GstRTSPMountPoints *mounts = gst_rtsp_server_get_mount_points(rtspServer);
    gst_rtsp_mount_points_add_factory(mounts, "/stream", factory);
    g_object_unref(mounts);

    if (gst_rtsp_server_attach(rtspServer, nullptr) == 0)
    {
        qWarning() << "Failed to attach RTSP server";
    }

    qDebug() << "RTSP server at rtsp://192.168.1.1:8554/stream\n";
}

void MainWindow::media_configure(GstRTSPMediaFactory *factory,
                                 GstRTSPMedia *media,
                                 gpointer user_data)
{
    MainWindow *self = static_cast<MainWindow *>(user_data);
    GstElement *element = gst_rtsp_media_get_element(media);
    GstElement *appsrc = gst_bin_get_by_name_recurse_up(GST_BIN(element), "mysrc");

    if (appsrc)
    {
        // 确保清理旧appsrc
        if (self->appsrc)
        {
            gst_element_set_state(self->appsrc, GST_STATE_NULL);
            gst_object_unref(self->appsrc);
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
        GstCaps *caps = gst_caps_new_simple("video/x-raw",
                                            "format", G_TYPE_STRING, "BGR",
                                            "width", G_TYPE_INT, self->width,
                                            "height", G_TYPE_INT, self->height,
                                            "framerate", GST_TYPE_FRACTION, self->frameRate, 1,
                                            nullptr);
        g_object_set(appsrc,
                     "caps", caps,
                     "stream-type", 0, // GST_APP_STREAM_TYPE_STREAM
                     nullptr);
        gst_caps_unref(caps);

        // 保存 appsrc 指针
        self->appsrc = appsrc;

        // 释放媒体元素引用
        gst_object_unref(element);
    }
}

void MainWindow::updateStreamResolution() 
{
    if (appsrc) {
        // 暂停推流
        gst_element_set_state(appsrc, GST_STATE_PAUSED);
        
        // 更新Caps
        GstCaps *caps = gst_caps_new_simple("video/x-raw",
                                            "format", G_TYPE_STRING, "BGR",
                                            "width", G_TYPE_INT, this->width,
                                            "height", G_TYPE_INT, this->height,
                                            "framerate", GST_TYPE_FRACTION, this->frameRate, 1,
                                            nullptr);
        g_object_set(appsrc,
                     "caps", caps,
                     "stream-type", 0, // GST_APP_STREAM_TYPE_STREAM
                     nullptr);
        gst_caps_unref(caps);
        // 恢复推流
        gst_element_set_state(appsrc, GST_STATE_PLAYING);
    }
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
        g_signal_handlers_disconnect_by_data(factory, this);
        g_object_unref(factory);
        factory = nullptr;
    }
    if (rtspServer)
    {
        GstRTSPMountPoints *mounts = gst_rtsp_server_get_mount_points(rtspServer);
        g_object_unref(mounts);
        g_object_unref(rtspServer);
        rtspServer = nullptr;
    }

    qDebug() << "RTSP server stoped!\n";
}