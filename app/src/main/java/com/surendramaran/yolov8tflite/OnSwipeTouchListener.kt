package com.surendramaran.yolov8tflite

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

open class OnSwipeTouchListener(context: Context) : View.OnTouchListener {
    
    private val gestureDetector: GestureDetector
    
    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
    
    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }

    // In OnSwipeTouchListener class
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        // Request the parent not to intercept touch events
        v.parent?.requestDisallowInterceptTouchEvent(true)
        return gestureDetector.onTouchEvent(event)
    }
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
        
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            var result = false
            try {
                val diffY = e2.y - (e1?.y ?: 0f)
                val diffX = e2.x - (e1?.x ?: 0f)
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        result = true
                    }
                } else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeDown()
                    } else {
                        onSwipeUp()
                    }
                    result = true
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return result
        }
    }
    
    open fun onSwipeRight() {}
    
    open fun onSwipeLeft() {}
    
    open fun onSwipeUp() {}
    
    open fun onSwipeDown() {}
}