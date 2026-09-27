package com.ashrafali.webtoonbridge.data

data class OcrBlock(val id:Int,val text:String,val left:Float,val top:Float,val right:Float,val bottom:Float)
data class TranslationBatch(val id:Int,val blocks:List<OcrBlock>)
data class AppSettings(var provider:String="chatgpt",var prompt:String="Translate the numbered webtoon dialogue into natural literary Bengali. Keep the numbering exactly. Output only the translated lines.")