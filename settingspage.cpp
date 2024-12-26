// SettingsPage.cpp
#include "settingspage.h"
#include "ui_settingspage.h"

SettingsPage::SettingsPage(QWidget *parent) : QWidget(parent), ui(new Ui::SettingsPage)
{
    ui->setupUi(this);
    this->hide();
    connect(ui->returnButton, &QPushButton::clicked, this, &SettingsPage::returnToMain);

    // 初始化分辨率下拉框
    ui->resolutionComboBox->addItem("1280x720 30FPS");
    ui->resolutionComboBox->addItem("1920x1080 30FPS");

    connect(ui->resolutionComboBox, static_cast<void (QComboBox::*)(int)>(&QComboBox::currentIndexChanged), this, &SettingsPage::slot_resolutionChanged);
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