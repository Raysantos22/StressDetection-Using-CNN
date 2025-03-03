package com.surendramaran.yolov8tflite

object Constants {
    const val MODEL_PATH = "yolov11v1.tflite"
    const val LABELS_PATH = "label1.txt"
}
//!yolo task=detect mode=train model=yolov8l.pt data={dataset.location}/data.yaml epochs=400 imgsz=640 batch=16 patience=100 lr0=0.001 lrf=0.0001 warmup_epochs=5 hsv_h=0.2 hsv_s=0.7 hsv_v=0.4 degrees=10 translate=0.1 scale=0.5 fliplr=0.5 mosaic=1.0 copy_paste=0.1 augment=True save_period=50 save=True