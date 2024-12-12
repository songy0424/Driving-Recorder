#ifndef CAMERA_H
#define CAMERA_H

#include <QObject>
#include <opencv2/opencv.hpp>

class Camera : public QObject {
    Q_OBJECT

public:
    explicit Camera(QObject *parent = nullptr);
    ~Camera();

    bool openCamera(const std::string &pipeline);
    void closeCamera();
    bool grabFrame(cv::Mat &frame);

signals:
    void imageCaptured(const cv::Mat &image);

private:
    cv::VideoCapture capture;
};

#endif // CAMERA_H