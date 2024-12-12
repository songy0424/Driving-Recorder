#include "camera.h"

Camera::Camera(QObject *parent) : QObject(parent) {}

Camera::~Camera() {
    closeCamera();
}

bool Camera::openCamera(const std::string &pipeline) {
    capture.open(pipeline, cv::CAP_GSTREAMER);
    return capture.isOpened();
}

void Camera::closeCamera() {
    if (capture.isOpened()) {
        capture.release();
    }
}

bool Camera::grabFrame(cv::Mat &frame) {
    return capture.read(frame);
}