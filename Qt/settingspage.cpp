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
                                              photoIntervalButton(nullptr),
                                              resolutionSelectionButton(nullptr),
                                              isHotspotActive(false),
                                              startupWifiPage(nullptr),
                                              photoIntervalPage(nullptr),
                                              resolutionSelectionPage(nullptr)
{
    ui->setupUi(this);
    this->hide();

    // 主布局
    QWidget *mainPage = new QWidget(this);
    QVBoxLayout *mainLayout = new QVBoxLayout(mainPage);
    wifiHotspotButton->setText("Wi-Fi 热点: 关 >");
    QPushButton *startupWifiButton = new QPushButton("开机 Wi-Fi 设置: 关 >", this);
    photoIntervalButton = new QPushButton("摄影间隔时间: 1分钟 >", this);
    resolutionSelectionButton = new QPushButton("分辨率: 1280x720 30FPS >", this);
    QPushButton *restoreButton = new QPushButton("恢复出厂设置", this);
    restoreButton->setMinimumSize(100, 70);
    restoreButton->setStyleSheet("QPushButton { font-size: 16px; }");

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

    mainLayout->addWidget(wifiHotspotButton);
    mainLayout->addWidget(startupWifiButton);
    mainLayout->addWidget(photoIntervalButton);
    mainLayout->addWidget(resolutionSelectionButton);
    mainLayout->addWidget(restoreButton);
    mainLayout->addWidget(ui->returnButton);
    mainLayout->addStretch();

    mainPage->setLayout(mainLayout);
    stackedWidget->addWidget(mainPage);

    startupWifiPage = createBooleanSelectionPage("开机 Wi-Fi 设置", startupWifiButton);
    photoIntervalPage = createTimeoutSelectionPage();
    resolutionSelectionPage = createResolutionSelectionPage();

    stackedWidget->addWidget(startupWifiPage);
    stackedWidget->addWidget(photoIntervalPage);
    stackedWidget->addWidget(resolutionSelectionPage);

    QVBoxLayout *layout = new QVBoxLayout(this);
    layout->addWidget(stackedWidget);
    setLayout(layout);

    connect(restoreButton, &QPushButton::clicked, this, &SettingsPage::restoreDefaultConfig);
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

    connect(this, &SettingsPage::configUpdated, this, &SettingsPage::onConfigUpdated);
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

    if (data.startsWith("resolution:"))
    {
        QRadioButton *res720p = resolutionSelectionPage->findChild<QRadioButton *>("res720pButton");
        QRadioButton *res1080p = resolutionSelectionPage->findChild<QRadioButton *>("res1080pButton");
        QString res = data.split(":")[1];

        res720p->setChecked(res == "1280p\n");
        res1080p->setChecked(res == "1920p\n");
        resolutionSelectionButton->setText(res == "1920p\n" ? "分辨率: 1920x1080 30FPS >" : "分辨率: 1280x720 30FPS >");
        slot_resolutionChanged(res == "1920p\n");
    }
    else if (data.startsWith("interval:"))
    {
        QString res = data.split(":")[1];
        QRadioButton *oneMin = photoIntervalPage->findChild<QRadioButton *>("oneMinButton");
        QRadioButton *threeMin = photoIntervalPage->findChild<QRadioButton *>("threeMinButton");
        QRadioButton *fiveMin = photoIntervalPage->findChild<QRadioButton *>("fiveMinButton");
        QRadioButton *tenMin = photoIntervalPage->findChild<QRadioButton *>("tenMinButton");
        static int interval;

        if (res == "1分钟\n")
        {
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
            interval = 600;
        }
        oneMin->setChecked(interval == 60);
        threeMin->setChecked(interval == 180);
        fiveMin->setChecked(interval == 300);
        tenMin->setChecked(interval == 600);

        emit photoIntervalChanged(interval);
    }
    else if (data == "reset:factory\n")
    {
        restoreDefaultConfig();
        return;
    }
    else if (data == "SAVE_IMAGE\n")
    {
        emit saveImageTriggered();
    }
    else if (data == "START_RECORD_VIDEO\n" || data == "STOP_RECORD_VIDEO\n")
    {
        emit RecordVideoTriggered();
    }

    saveCurrentConfig();
}

QWidget *SettingsPage::createBooleanSelectionPage(const QString &title, QPushButton *mainButton)
{
    QWidget *page = new QWidget(this);
    QVBoxLayout *layout = new QVBoxLayout(page);

    QGroupBox *groupBox = new QGroupBox(title, this);
    QVBoxLayout *groupLayout = new QVBoxLayout(groupBox);

    QRadioButton *onButton = new QRadioButton("开", this);
    onButton->setObjectName("onButton");
    QRadioButton *offButton = new QRadioButton("关", this);
    offButton->setObjectName("offButton");
    offButton->setChecked(true);

    groupLayout->addWidget(onButton);
    groupLayout->addWidget(offButton);
    groupBox->setLayout(groupLayout);

    QPushButton *confirmButton = new QPushButton("确定", this);

    onButton->setMinimumSize(100, 70);
    offButton->setMinimumSize(100, 70);
    confirmButton->setMinimumSize(100, 70);
    onButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    offButton->setStyleSheet("QRadioButton { font-size: 16px; }");
    confirmButton->setStyleSheet("QPushButton { font-size: 16px;}");

    layout->addWidget(groupBox);
    layout->addStretch();
    layout->addWidget(confirmButton);
    page->setLayout(layout);

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
                stackedWidget->setCurrentIndex(0);
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
    oneMinButton->setObjectName("oneMinButton");
    QRadioButton *threeMinButton = new QRadioButton("3分钟", this);
    threeMinButton->setObjectName("threeMinButton");
    QRadioButton *fiveMinButton = new QRadioButton("5分钟", this);
    fiveMinButton->setObjectName("fiveMinButton");
    QRadioButton *tenMinButton = new QRadioButton("10分钟", this);
    tenMinButton->setObjectName("tenMinButton");
    oneMinButton->setChecked(true);

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
    layout->addStretch();
    layout->addWidget(confirmButton);
    page->setLayout(layout);

    connect(confirmButton, &QPushButton::clicked, this, [=]()
            {
                int interval = 60;
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
                stackedWidget->setCurrentIndex(0);
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
    res720pButton->setObjectName("res720pButton");
    QRadioButton *res1080pButton = new QRadioButton("1920x1080 30FPS", this);
    res1080pButton->setObjectName("res1080pButton");
    res720pButton->setChecked(true);

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
    layout->addStretch();
    layout->addWidget(confirmButton);
    page->setLayout(layout);

    connect(confirmButton, &QPushButton::clicked, this, [=]()
            {
                int selectedResolution = -1;
                if (res720pButton->isChecked())
                {
                    selectedResolution = 0;
                }
                else if (res1080pButton->isChecked())
                {
                    selectedResolution = 1;
                }

                if (selectedResolution == 0)
                {
                    resolutionSelectionButton->setText("分辨率: 1280x720 30FPS >");
                }
                else if (selectedResolution == 1)
                {
                    resolutionSelectionButton->setText("分辨率: 1920x1080 30FPS >");
                }

                slot_resolutionChanged(selectedResolution);
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
    this->close();
    emit returnToMainWindow();
}

void SettingsPage::slot_resolutionChanged(int index)
{
    switch (index)
    {
    case 0:
        emit resolutionChanged(1280, 720, 30);
        break;
    case 1:
        emit resolutionChanged(1920, 1080, 30);
        break;
    default:
        break;
    }
}

void SettingsPage::createWiFiHotspot()
{
    if (isHotspotActive)
    {
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

void SettingsPage::restoreDefaultConfig()
{
    QJsonObject defaultConfig;
    defaultConfig["resolution/index"] = 0;
    defaultConfig["capture/interval"] = 60;
    defaultConfig["network/hotspot"] = false;

    resolutionSelectionButton->setText("分辨率: 1280x720 30FPS >");
    slot_resolutionChanged(0);
    photoIntervalButton->setText("摄影间隔时间: 1分钟 >");
    isHotspotActive = false;
    wifiHotspotButton->setText("Wi-Fi 热点: 关 >");

    QRadioButton *res720p = resolutionSelectionPage->findChild<QRadioButton *>("res720pButton");
    QRadioButton *res1080p = resolutionSelectionPage->findChild<QRadioButton *>("res1080pButton");
    res720p->setChecked(true);
    res1080p->setChecked(false);

    QRadioButton *oneMin = photoIntervalPage->findChild<QRadioButton *>("oneMinButton");
    QRadioButton *threeMin = photoIntervalPage->findChild<QRadioButton *>("threeMinButton");
    QRadioButton *fiveMin = photoIntervalPage->findChild<QRadioButton *>("fiveMinButton");
    QRadioButton *tenMin = photoIntervalPage->findChild<QRadioButton *>("tenMinButton");
    oneMin->setChecked(true);
    threeMin->setChecked(false);
    fiveMin->setChecked(false);
    tenMin->setChecked(false);

    QRadioButton *onBtn = startupWifiPage->findChild<QRadioButton *>("onButton");
    QRadioButton *offBtn = startupWifiPage->findChild<QRadioButton *>("offButton");
    offBtn->setChecked(true);
    onBtn->setChecked(false);

    saveCurrentConfig();
}

void SettingsPage::loadInitialConfig()
{
    QFile file("/mnt/myvideo/config.json");
    if (file.open(QIODevice::ReadOnly | QIODevice::Text))
    {
        QByteArray data = file.readAll();
        QJsonDocument doc = QJsonDocument::fromJson(data);
        QJsonObject obj = doc.object();

        int resIndex = obj["resolution/index"].toInt(0);
        QRadioButton *res720p = resolutionSelectionPage->findChild<QRadioButton *>("res720pButton");
        QRadioButton *res1080p = resolutionSelectionPage->findChild<QRadioButton *>("res1080pButton");

        if (res720p && res1080p)
        {
            res720p->setChecked(resIndex == 0);
            res1080p->setChecked(resIndex == 1);
            resolutionSelectionButton->setText(resIndex ? "分辨率: 1920x1080 30FPS >" : "分辨率: 1280x720 30FPS >");
            slot_resolutionChanged(resIndex);
        }

        int interval = obj["capture/interval"].toInt(60);
        photoIntervalButton->setText(QString("摄影间隔时间: %1分钟 >").arg(interval / 60));
        QRadioButton *oneMin = photoIntervalPage->findChild<QRadioButton *>("oneMinButton");
        QRadioButton *threeMin = photoIntervalPage->findChild<QRadioButton *>("threeMinButton");
        QRadioButton *fiveMin = photoIntervalPage->findChild<QRadioButton *>("fiveMinButton");
        QRadioButton *tenMin = photoIntervalPage->findChild<QRadioButton *>("tenMinButton");
        if (oneMin)
            oneMin->setChecked(interval == 60);
        if (threeMin)
            threeMin->setChecked(interval == 180);
        if (fiveMin)
            fiveMin->setChecked(interval == 300);
        if (tenMin)
            tenMin->setChecked(interval == 600);

        isHotspotActive = obj["network/hotspot"].toBool(false);
        wifiHotspotButton->setText(QString("Wi-Fi 热点: %1 >").arg(isHotspotActive ? "开" : "关"));
        QRadioButton *onBtn = startupWifiPage->findChild<QRadioButton *>("onButton");
        QRadioButton *offBtn = startupWifiPage->findChild<QRadioButton *>("offButton");
        if (onBtn && offBtn)
        {
            onBtn->setChecked(isHotspotActive);
            offBtn->setChecked(!isHotspotActive);
        }

        file.close();
    }
    else
    {
        restoreDefaultConfig();
    }
}

QJsonObject SettingsPage::generateConfig()
{
    QJsonObject obj;

    obj["resolution/index"] = resolutionSelectionButton->text().contains("1280") ? 0 : 1;
    QRegularExpression re("(\\d+)分");
    QRegularExpressionMatch match = re.match(photoIntervalButton->text());
    if (match.hasMatch())
    {
        int interval = match.captured(1).toInt();
        obj["capture/interval"] = interval * 60;
    }
    else
    {
        obj["capture/interval"] = 60;
    }

    obj["network/hotspot"] = isHotspotActive;

    return obj;
}

void SettingsPage::saveCurrentConfig()
{
    QJsonObject obj = generateConfig();
    QJsonDocument doc(obj);
    QFile file("/mnt/myvideo/config.json");

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

    emit configUpdated(obj);
}

void SettingsPage::onConfigUpdated(const QJsonObject &config)
{
    qDebug() << "Config updated:" << config;
}
