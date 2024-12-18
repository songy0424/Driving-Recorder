#ifndef SETTINGSPAGE_H
#define SETTINGSPAGE_H

#include <QWidget>

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

private slots:
    void returnToMain(); // 返回主界面的槽函数

private:
    Ui::SettingsPage *ui;
};

#endif // SETTINGSPAGE_H