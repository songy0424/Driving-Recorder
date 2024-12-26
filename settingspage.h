#ifndef SETTINGSPAGE_H
#define SETTINGSPAGE_H

#include <QWidget>
#include <QComboBox>

namespace Ui
{
    class SettingsPage; // 前向声明
}
class SettingsPage : public QWidget
{
    Q_OBJECT

public:
    explicit SettingsPage(QWidget *parent = nullptr);
    ~SettingsPage();

signals:
    void returnToMainWindow();
    void resolutionChanged(int width, int height, int frameRate); // 发出分辨率改变的信号

private slots:
    void returnToMain(); // 返回主界面的槽函数
    void slot_resolutionChanged(int index);

private:
    Ui::SettingsPage *ui;
    QComboBox *resolutionComboBox;
};

#endif // SETTINGSPAGE_H