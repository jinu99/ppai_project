package com.example.myapplication.classes

import android.graphics.Rect

class YOLOResult (var classIndex: Int, var score: Float, rect: Rect) {
    var rect: Rect

    init {
        this.rect = rect
    }
}