// SettingsPage.cpp
#include "settingspage.h"
#include "ui_settingspage.h"

SettingsPage::SettingsPage(QWidget *parent) : QWidget(parent), ui(new Ui::SettingsPage)
{
    ui->setupUi(this);
    this->hide();
    connect(ui->returnButton, &QPushButton::clicked, this, &SettingsPage::returnToMain);
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