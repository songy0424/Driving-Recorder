#-------------------------------------------------
#
# Project created by QtCreator 2024-12-11T19:10:17
#
#-------------------------------------------------

QT       += core gui
QT       += multimedia
QT       += multimediawidgets
greaterThan(QT_MAJOR_VERSION, 4): QT += widgets

TARGET = driving-recorder
TEMPLATE = app


INCLUDEPATH += /usr/include/opencv2
INCLUDEPATH += /usr/include/glib-2.0
INCLUDEPATH += /usr/lib/aarch64-linux-gnu/glib-2.0/include
INCLUDEPATH += /usr/include/gstreamer-1.0
LIBS += -L/usr/lib/aarch64-linux-gnu/gstreamer-1.0
LIBS += -L/usr/lib/aarch64-linux-gnu -lopencv_core -lopencv_highgui -lopencv_imgproc -lopencv_imgcodecs -lopencv_videoio
LIBS += -pthread -lgstrtspserver-1.0 -lgstbase-1.0 -lgstreamer-1.0 -lgobject-2.0 -lglib-2.0

# The following define makes your compiler emit warnings if you use
# any feature of Qt which has been marked as deprecated (the exact warnings
# depend on your compiler). Please consult the documentation of the
# deprecated API in order to know how to port your code away from it.
DEFINES += QT_DEPRECATED_WARNINGS

# You can also make your code fail to compile if you use deprecated APIs.
# In order to do so, uncomment the following line.
# You can also select to disable deprecated APIs only up to a certain version of Qt.
#DEFINES += QT_DISABLE_DEPRECATED_BEFORE=0x060000    # disables all the APIs deprecated before Qt 6.0.0


SOURCES += \
        main.cpp \
        mainwindow.cpp \
        camera.cpp \
        settingspage.cpp

HEADERS += \
        mainwindow.h \
        camera.h \
        settingspage.h

FORMS += \
        mainwindow.ui \
        settingspage.ui
