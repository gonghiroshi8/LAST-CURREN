package com.example.currencyconvert.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View

class FocusOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        color = 0x80000000.toInt()  // สีดำทึบ
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // กำหนดความสูง
        val width = width
        val height = height
        val rectHeight = height / 7f
        val top = (height - rectHeight) / 2f
        val bottom = top + rectHeight

        // กำหนดขอบซ้ายและขวาของพื้นที่โฟกัส
        val leftMargin = width * 0.1f // 10% ของความกว้าง
        val rightMargin = width * 0.1f // 10% ของความกว้าง

        // สร้างเลเยอร์ใหม่สำหรับการวาด
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // วาดพื้นหลังสีดำครอบคลุมทั้งหน้าจอ
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // ลบพื้นที่โฟกัสออกจากพื้นหลังสีดำ
        canvas.drawRect(leftMargin, top, width - rightMargin, bottom, clearPaint)

        // คืนค่าเลเยอร์
        canvas.restoreToCount(saveCount)
    }
}