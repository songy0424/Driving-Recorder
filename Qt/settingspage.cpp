#include "settingspage.h"
#include "ui_settingspage.h"
#include <QVBoxLayout>
#include <QProcess>
#include <QFile>
#include <QDebug>
#include <QRegularExpression>

SettingsPage::SettingsPage(QWidget *parent) : QWidget(parent),
                                              ui(new Ui::SettingsPage),
                                              stackedWidget(new QStackedWidget(this)),
                                              wifiHotspotButton(new QPushButton(this)),
                                              isHotspotActive(false)
{
    ui->setupUi(this);
    this->hide();

    // 主布局
    QWidget *mainPage = new QWidget(this);
    QVBoxLayout *mainLayout = new QVBoxLayout(mainPage);
    wifiHotspotButton->setText("Wi-Fi 热点: 关 >");
    QPushButton *startupWifiButton = new QPushButton("开机 Wi-Fi 设置: 关 >", this);
    photoIntervalButton = new QPushButton("摄影间隔时间: 1分钟 >", this); // 摄影间隔
    resolutionSelectionButton = new QPushButton("分辨率: 1280x720 30FPS >", this);
    wifiHotspotButton->setMinimumSize(100, 70);
    startupWifiButton->setMinimumSize(100, 70);
    photoIntervalButton->setMinimumSize(100, 70);
    resolutionSelectionButton->setMinimumSize(100, 70);
    ui->returnButton->setMinimumSize(100, 70);
    wifiHotspotButton->setStyleSheet("QPushButton { font-size: 16px;}");
    startupWifiButton->setStyleSheet("QPushButton { font-size: 16px;}");
    photoIntervalButton->setStyleSheet("QPushButton { font-size: 16px;}");
    resolutionSelectionButton->setStyleSheet("QPushButton { font-size: 16px;}");
    ui->returnButton->setStyleSheet("QPushButton { font-size: 16px;}");

    // 将按钮添加到主界面布局
    mainLayout->addWidget(wifiHotspotButton);
    mainLayout->addWidget(startupWifiButton);
    mainLayout->addWidget(photoIntervalButton);
    mainLayout->addWidget(resolutionSelectionButton);
    mainLayout->addWidget(ui->returnButton);
    mainLayout->addStretch(); // 添加弹性空间，避免按钮堆积

    mainPage->setLayout(mainLayout);
    stackedWidget->addWidget(mainPage); // 添加主界面到堆叠布局

    // 创建选择界面
    QWidget *startupWifiPage = createBooleanSelectionPage("开机 Wi-Fi 设置", startupWifiButton);
    QWidget *photoIntervalPage = createTimeoutSelectionPage();
    QWidget *resolutionSelectionPage = createResolutionSelectionPage();

    stackedWidget->addWidget(startupWifiPage);
    stackedWidget->addWidget(photoIntervalPage);
    stackedWidget->addWidget(resolutionSelectionPage);

    QVBoxLayout *layout = new QVBoxLayout(this);
    layout->addWidget(stackedWidget);
    setLayout(layout);

    // 连接按钮切换到对应页面

    connect(wifiHotspotButton, &QPushButton::clicked, this, &SettingsPage::createWiFiHotspot);
    connect(startupWifiButton, &QPushButton::clicked, this, [=]()
            { stackedWidget->setCurrentWidget(startupWifiPage); });
    connect(photoIntervalButton, &QPushButton::clicked, this, [=]()
            { stackedWidget->setCurrentWidget(photoIntervalPage); });
    connect(resolutionSelectionButton, &QPushButton::clicked, this, [=]()
            { stackedWidget->setCurrentWidget(resolutionSelectionPage); });
    connect(ui->returnButton, &QPushButton::clicked, this, &SettingsPage::returnToMain);

    tcpServer = new QTcpServer(this);
    if (!tcpServer->listen(QHostAddress::Any, 12345))
    {
        qDebug() << "Server could not start";
    }
    else
    {
        qDebug() << "Server start Success";
        connect(tcpServer, &QTcpServer::newConnection, this, &SettingsPage::newConnection);
    }

    // 关联信号和槽
    connect(this, &SettingsPage::configUpdated, this, &SettingsPage::onConfigUpdated);

    loadInitialConfig();
}

void SettingsPage::newConnection()
{
    clientSocket = tcpServer->nextPendingConnection();
    connect(clientSocket, &QTcpSocket::readyRead, this, &SettingsPage::readData);
}

void SettingsPage::readData()
{
    QString data = QString(clientSocket->readAll());
    qDebug() << "Received:" << data;

    // 解析指令
    // if (data.startsWith("GET_CONFIG"))
    // {
    //     sendConfigToClient();
    // }
    if (data.startsWith("resolution:"))
    {
        QString res = data.split(":")[1];
        qDebug() << "res:" << res;
        if (res == "1280p\n")
        {
            // 更新主界面按钮文字
            resolutionSelectionButton->setText("分辨率: 1280x720 30FPS >");
            slot_resolutionChanged(0);
        }
        else if (res == "1920p\n")
        {
            resolutionSelectionButton->setText("分辨率: 1920x1080 30FPS >");
            slot_resolutionChanged(1);
        }
        saveCurrentConfig();
    }
    else if (data.startsWith("interval:"))
    {
        QString res = data.split(":")[1];
        qDebug() << "res:" << res;
        static int interval = 60; // 默认1分钟
        if (res == "1分钟\n")
        {
            // 更新主界面按钮文字
            photoIntervalButton->setText("摄影间隔时间: 1分钟 >");
            interval = 60;
        }
        else if (res == "3分钟\n")
        {
            photoIntervalButton->setText("摄影间隔时间: 3分钟 >");
            interval = 180;
        }
        else if (res == "5分钟\n")
        {
            photoIntervalButton->setText("摄影间隔时间: 5分钟 >");
            interval = 300;
        }
        else if (res == "10分钟\n")
        {
            photoIntervalButton->setText("摄影间隔时间: 10分钟 >");
            interval = 600; // 默认1分钟
        }
        saveCurrentConfig();
        emit photoIntervalChanged(interval);
    }
    else if (data == "SAVE_IMAGE\n")
    {
        emit saveImageTriggered();
    }
    else if (data == "START_RECORD_VIDEO\n" || data == "STOP_RECORD_VIDEO\n")
    {
        emit RecordVideoTriggered();
    }
}

QWidget *SettingsPage::createBooleanSelectionPage(const QString &title, QPushButton *mainButton)
{
    QWidget *page = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(page);

    QGroupBox *groupBox = new QGroupBox(title, this); // 添加分组框
    QVBoxLayout *groupLayout = new QVBoxLayout(groupBox);

    QRadioButton *onButton = new QRadioButton("开", this);
    QRadioButton *offButton = new QRadioButton("关", this);

    offButton->setChecked(true); // 默认选择“关”

    groupLayout->addWidget(onButton);
    groupLayout->addWidget(offButton);
    groupBox->setLayout(groupLayout);

    // 返回按钮
    QPushButton *confirmButton = new QPushButton("确定", this);

    onButton->setMinimumSize(100, 70);
    offButton->setMinimumSize(100, 70);
    confirmButton->setMinimumSize(100, 70);
    onButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    offButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    confirmButton->setStyleSheet("QPushButton { font-size: 16px;}");

    layout->addWidget(groupBox);
    layout->addStretch(); // 增加弹性空间，使返回按钮居底部
    layout->addWidget(confirmButton);
    page->setLayout(layout);

    // 点击确定返回主界面并更新状态
    connect(confirmButton, &QPushButton::clicked, this, [=]()
            {
                if (onButton->isChecked())
                {
                    mainButton->setText(title + ": 开 >");
                }
                else
                {
                    mainButton->setText(title + ": 关 >");
                }
                stackedWidget->setCurrentIndex(0); // 返回主界面
                saveCurrentConfig(); });

    return page;
}

QWidget *SettingsPage::createTimeoutSelectionPage()
{
    QWidget *page = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(page);

    QGroupBox *groupBox = new QGroupBox("摄影间隔时间", this);
    QVBoxLayout *groupLayout = new QVBoxLayout(groupBox);

    QRadioButton *oneMinButton = new QRadioButton("1分钟", this);
    QRadioButton *threeMinButton = new QRadioButton("3分钟", this);
    QRadioButton *fiveMinButton = new QRadioButton("5分钟", this);
    QRadioButton *tenMinButton = new QRadioButton("10分钟", this);
    oneMinButton->setChecked(true); // 默认选择“1分钟”

    groupLayout->addWidget(oneMinButton);
    groupLayout->addWidget(threeMinButton);
    groupLayout->addWidget(fiveMinButton);
    groupLayout->addWidget(tenMinButton);
    groupBox->setLayout(groupLayout);

    QPushButton *confirmButton = new QPushButton("确定", this);

    oneMinButton->setMinimumSize(100, 70);
    threeMinButton->setMinimumSize(100, 70);
    fiveMinButton->setMinimumSize(100, 70);
    tenMinButton->setMinimumSize(100, 70);
    confirmButton->setMinimumSize(100, 70);
    oneMinButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    threeMinButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    fiveMinButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    tenMinButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    confirmButton->setStyleSheet("QPushButton { font-size: 16px;}");

    layout->addWidget(groupBox);
    layout->addStretch(); // 增加弹性空间
    layout->addWidget(confirmButton);
    page->setLayout(layout);

    // 点击确定返回主界面并更新状态
    connect(confirmButton, &QPushButton::clicked, this, [=]()
            {
                int interval = 60; // 默认1分钟
                if (oneMinButton->isChecked())
                {
                    photoIntervalButton->setText("摄影间隔时间: 1分钟 >");
                    interval = 60;
                }
                else if (threeMinButton->isChecked())
                {
                    photoIntervalButton->setText("摄影间隔时间: 3分钟 >");
                    interval = 180;
                }
                else if (fiveMinButton->isChecked())
                {
                    photoIntervalButton->setText("摄影间隔时间: 5分钟 >");
                    interval = 300;
                }
                else if (tenMinButton->isChecked())
                {
                    photoIntervalButton->setText("摄影间隔时间: 10分钟 >");
                    interval = 600;
                }
                stackedWidget->setCurrentIndex(0); // 返回主界面

                // 发射摄影间隔更改信号
                emit photoIntervalChanged(interval);
                saveCurrentConfig(); });

    return page;
}

QWidget *SettingsPage::createResolutionSelectionPage()
{
    QWidget *page = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(page);

    QGroupBox *groupBox = new QGroupBox("选择分辨率", this);
    QVBoxLayout *groupLayout = new QVBoxLayout(groupBox);

    QRadioButton *res720pButton = new QRadioButton("1280x720 30FPS", this);
    QRadioButton *res1080pButton = new QRadioButton("1920x1080 30FPS", this);
    res720pButton->setChecked(true); // 默认选中 720p

    groupLayout->addWidget(res720pButton);
    groupLayout->addWidget(res1080pButton);
    groupBox->setLayout(groupLayout);

    QPushButton *confirmButton = new QPushButton("确定", this);

    res720pButton->setMinimumSize(100, 70);
    res1080pButton->setMinimumSize(100, 70);
    confirmButton->setMinimumSize(100, 70);
    res720pButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    res1080pButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    confirmButton->setStyleSheet("QPushButton { font-size: 16px;}");

    layout->addWidget(groupBox);
    layout->addStretch(); // 弹性布局让按钮居底
    layout->addWidget(confirmButton);
    page->setLayout(layout);

    // 连接确定按钮和分辨率切换槽
    connect(confirmButton, &QPushButton::clicked, this, [=]()
            {
        int selectedResolution = -1;
        if (res720pButton->isChecked()) {
            selectedResolution = 0; // 720p
        } else if (res1080pButton->isChecked()) {
            selectedResolution = 1; // 1080p
        }

        // 更新主界面按钮文字
        if (selectedResolution == 0) {
            resolutionSelectionButton->setText("分辨率: 1280x720 30FPS >");
        } else if (selectedResolution == 1) {
            resolutionSelectionButton->setText("分辨率: 1920x1080 30FPS >");
        }

        // 发射分辨率变更信号
        slot_resolutionChanged(selectedResolution);

        // 返回主界面
        stackedWidget->setCurrentIndex(0);
        saveCurrentConfig(); });

    return page;
}

SettingsPage::~SettingsPage()
{
    delete ui;
}

void SettingsPage::returnToMain()
{
    this->close();             // 隐藏设置页面
    emit returnToMainWindow(); // 发送信号，通知主窗口显示
}

void SettingsPage::slot_resolutionChanged(int index)
{
    // 根据下拉框的当前索引获取分辨率
    switch (index)
    {
    case 0:
        emit resolutionChanged(1280, 720, 30);
        break;
    case 1:
        emit resolutionChanged(1920, 1080, 30);
        break;

    default:
        // 默认或错误处理
        break;
    }
}

void SettingsPage::createWiFiHotspot()
{
    if (isHotspotActive)
    {
        // 关闭热点
        QProcess process;
        QString command = "sudo nmcli device disconnect wlan0";
        process.start(command);
        process.waitForFinished();

        wifiHotspotButton->setText("Wi-Fi 热点: 关 >");
        isHotspotActive = false;
        emit wifiStateChanged(false, "192.168.1.1");
    }
    else
    {
        // 创建热点
        QProcess process;
        QString command1 = "sudo nmcli device wifi hotspot con-name my-hostapt ssid Jetson password 12345678";
        QString command2 = "sudo nmcli connection modify my-hostapt ipv4.addresses 192.168.1.1/24 ipv4.method manual";
        QString command3 = "sudo nmcli connection down my-hostapt";
        QString command4 = "sudo nmcli connection up my-hostapt";
        QString command5 = "sudo systemctl restart dnsmasq";
        process.start(command1);
        process.waitForFinished();
        process.start(command2);
        process.waitForFinished();
        process.start(command3);
        process.waitForFinished();
        process.start(command4);
        process.waitForFinished();
        process.start(command5);
        process.waitForFinished();

        wifiHotspotButton->setText("Wi-Fi 热点: 开 >");
        isHotspotActive = true;
        // 获取WiFi接口的IP地址
        emit wifiStateChanged(true, "192.168.1.1");
    }
    saveCurrentConfig();
}

void SettingsPage::loadInitialConfig()
{
    QFile file("config.json");
    if (file.open(QIODevice::ReadOnly | QIODevice::Text))
    {
        QByteArray data = file.readAll();
        QJsonDocument doc = QJsonDocument::fromJson(data);
        QJsonObject obj = doc.object();

        // 分辨率
        int resIndex = obj["resolution/index"].toInt(0); // 没有文件默认值为0
        slot_resolutionChanged(resIndex);

        // 摄影间隔
        int interval = obj["capture/interval"].toInt(60);
        photoIntervalButton->setText(QString("摄影间隔时间: %1分钟 >").arg(interval / 60));

        // WiFi热点状态
        isHotspotActive = obj["network/hotspot"].toBool(false);
        wifiHotspotButton->setText(QString("Wi-Fi 热点: %1 >").arg(isHotspotActive ? "开" : "关"));

        file.close();
    }
    else
    {
        qDebug() << "open error";
    }
}

QJsonObject SettingsPage::generateConfig()
{
    QJsonObject obj;

    // 分辨率
    obj["resolution/index"] = resolutionSelectionButton->text().contains("1280") ? 0 : 1;

    // 摄影间隔
    QRegularExpression re("(\\d+)分");
    QRegularExpressionMatch match = re.match(photoIntervalButton->text());
    if (match.hasMatch())
    {
        int interval = match.captured(1).toInt();
        obj["capture/interval"] = interval * 60;
    }
    else
    {
        obj["capture/interval"] = 60; // 默认1分钟
    }

    // 热点状态
    obj["network/hotspot"] = isHotspotActive;

    return obj;
}

void SettingsPage::saveCurrentConfig()
{
    QJsonObject obj = generateConfig();

    QJsonDocument doc(obj);
    QFile file("config.json");

    if (file.open(QIODevice::WriteOnly | QIODevice::Text))
    {
        if (file.write(doc.toJson()) == -1)
        {
            qDebug() << "Failed to write to file:" << file.errorString();
        }
        file.close();
    }
    else
    {
        qDebug() << "Failed to open file:" << file.errorString();
    }

    emit configUpdated(obj); // 直接发射 QJsonObject 类型的参数
}

void SettingsPage::onConfigUpdated(const QJsonObject &config)
{
    // 处理配置更新事件
    qDebug() << "Config updated:" << config;
}