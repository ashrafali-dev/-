package com.ashrafali.webtoonbridge.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.ashrafali.webtoonbridge.data.OcrBlock

class TranslationOverlay(context:Context):FrameLayout(context){
    init{setBackgroundColor(Color.TRANSPARENT);isClickable=false}
    fun show(blocks:List<OcrBlock>,translations:List<String>){removeAllViews();post{
        blocks.forEachIndexed{i,b->val text=translations.getOrNull(i).orEmpty();if(text.isBlank())return@forEachIndexed
            val v=TextView(context).apply{this.text=text;setTextColor(Color.BLACK);setBackgroundColor(0xEFFFFFFF.toInt());setPadding(8,4,8,4);textSize=15f;gravity=Gravity.CENTER}
            val lp=LayoutParams(maxOf(80,((b.right-b.left)*width).toInt()),maxOf(48,((b.bottom-b.top)*height).toInt())).apply{leftMargin=(b.left*width).toInt();topMargin=(b.top*height).toInt()};addView(v,lp)
        }
    }}
}