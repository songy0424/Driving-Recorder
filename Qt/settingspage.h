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
#include <QSettings>
#include <QJsonObject>
#include <QJsonDocument>

namespace Ui
{
    class SettingsPage;
}
class SettingsPage : public QWidget
{
    Q_OBJECT

public:
    explicit SettingsPage(QWidget *parent = nullptr);
    ~SettingsPage();
    void loadInitialConfig();
    bool TimeStampActive() const { return isTimeStampActive; }
    bool EnhancementActive() const { return isEnhancementActive; }

signals:
    void returnToMainWindow();
    void resolutionChanged(int width, int height, int frameRate);
    void photoIntervalChanged(int interval);
    void wifiStateChanged(bool isActive, const QString &ipAddress);
    void saveImageTriggered();
    void RecordVideoTriggered();
    void configUpdated(const QJsonObject &config);

private slots:
    void returnToMain();
    void slot_resolutionChanged(int index);
    void createWiFiHotspot();
    void newConnection();
    void readData();
    void restoreDefaultConfig();

private:
    Ui::SettingsPage *ui;
    QStackedWidget *stackedWidget;
    QPushButton *wifiHotspotButton;
    QPushButton *photoIntervalButton;
    QPushButton *resolutionSelectionButton;
    QPushButton *timeStampDisplayButton;
    QPushButton *imageEnhancementButton ;

    bool isHotspotActive;
    bool isTimeStampActive;
    bool isEnhancementActive;
    QTcpServer *tcpServer;
    QTcpSocket *clientSocket;
    QWidget *startupWifiPage;
    QWidget *photoIntervalPage;
    QWidget *resolutionSelectionPage;
    QWidget *timeStampDisplayPage;
    QWidget *imageEnhancementPage;
    QWidget *createBooleanSelectionPage(const QString &title, QPushButton *mainButton);
    QWidget *createTimeoutSelectionPage();
    QWidget *createResolutionSelectionPage();
    void saveCurrentConfig();
    QJsonObject generateConfig();
    void onConfigUpdated(const QJsonObject &config);
};

#endif // SETTINGSPAGE_H