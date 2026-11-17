package com.pt2vhf.speedalert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/** Native, vector-rendered reference visuals, independent of screen resolution. */
class SpeedSignView(context:Context):View(context) {
    var limit:String="—"
        set(value) {field=value;invalidate()}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        val side=min(width,height).toFloat()
        val cx=width/2f
        val cy=height/2f
        val outer=side*0.49f
        p.style=Paint.Style.FILL
        p.color=Color.WHITE
        canvas.drawCircle(cx,cy,outer,p)
        p.style=Paint.Style.STROKE
        p.color=Color.rgb(247,31,51)
        p.strokeWidth=side*0.085f
        canvas.drawCircle(cx,cy,outer-p.strokeWidth/2f,p)
        p.style=Paint.Style.FILL
        p.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        p.textAlign=Paint.Align.CENTER
        p.color=Color.rgb(7,9,13)
        p.textSize=side*(if(limit.length>3) 0.345f else 0.40f)
        canvas.drawText(limit,cx,cy+side*0.07f,p)
        p.textSize=side*0.128f
        p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        canvas.drawText("km/h",cx,cy+side*0.27f,p)
    }
}

class Pt2vhfLogoView(context:Context):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas) {
        val s=min(width,height).toFloat()
        c.save()
        c.translate((width-s)/2f,(height-s)/2f)
        p.color=Color.rgb(245,30,48)
        p.style=Paint.Style.STROKE
        p.strokeWidth=s*0.085f
        p.strokeCap=Paint.Cap.ROUND
        listOf(
            RectF(s*.07f,s*.08f,s*.93f,s*.95f),
            RectF(s*.22f,s*.29f,s*.78f,s*.99f),
            RectF(s*.37f,s*.52f,s*.63f,s*1.04f)
        ).forEach { rect -> c.drawArc(rect,190f,160f,false,p) }
        c.restore()
    }
}
class GpsBarsView(context:Context):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var level:Int=0
        set(value){field=value.coerceIn(0,4);invalidate()}
    override fun onDraw(canvas:Canvas) {
        val w=width.toFloat()
        val h=height.toFloat()
        val bw=w/7.5f
        for(i in 0..3) {
            val bh=h*(0.24f+i*0.19f)
            p.color=if(i<level)Color.rgb(0,234,77) else Color.rgb(56,79,92)
            canvas.drawRoundRect(w*0.09f+i*bw*1.75f,h-bh,
                w*0.09f+i*bw*1.75f+bw,h,3f,3f,p)
        }
    }
}
