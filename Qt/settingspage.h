#ifndef SETTINGSPAGE_H
#define SETTINGSPAGE_H

#include <QWidget>
#include <QPushButton>
#include <QLabel>
#include <QHBoxLayout>
#include <QStackedWidget>
#include <QRadioButton>
#include <QGroupBox>
#include <QSpacerItem>
#include <QTcpServer>
#include <QTcpSocket>

namespace Ui
{
    class SettingsPage; // 前向声明
}
class SettingsPage : public QWidget
{
    Q_OBJECT
    QPushButton *resolutionSelectionButton;
    QPushButton *photoIntervalButton;

public:
    explicit SettingsPage(QWidget *parent = nullptr);
    ~SettingsPage();

signals:
    void returnToMainWindow();
    void resolutionChanged(int width, int height, int frameRate); // 发出分辨率改变的信号
    void photoIntervalChanged(int interval);                      // 发出摄影间隔改变的信号
    void wifiStateChanged(bool isActive, const QString &ipAddress);

private slots:
    void returnToMain(); // 返回主界面的槽函数
    void slot_resolutionChanged(int index);
    void createWiFiHotspot();
    void newConnection();
    void readData();

private:
    Ui::SettingsPage *ui;
    QStackedWidget *stackedWidget;
    QPushButton *wifiHotspotButton; // 定义为成员变量
    bool isHotspotActive;           // 添加热点状态变量

    QWidget *createBooleanSelectionPage(const QString &title, QPushButton *mainButton);
    QWidget *createTimeoutSelectionPage();
    QWidget *createResolutionSelectionPage();
    QTcpServer *tcpServer;
    QTcpSocket *clientSocket;
};

#endif // SETTINGSPAGE_H