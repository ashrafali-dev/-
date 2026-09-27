package com.ashrafali.webtoonbridge.ocr

import android.graphics.Bitmap
import com.ashrafali.webtoonbridge.data.OcrBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocalOcr {
    suspend fun recognize(bitmap: Bitmap, language:String): List<OcrBlock> = suspendCancellableCoroutine { cont ->
        val opts = when(language) {
            "ja" -> JapaneseTextRecognizerOptions.Builder().build()
            "ko" -> KoreanTextRecognizerOptions.Builder().build()
            "zh" -> ChineseTextRecognizerOptions.Builder().build()
            else -> TextRecognizerOptions.DEFAULT_OPTIONS
        }
        val recognizer = TextRecognition.getClient(opts)
        recognizer.process(InputImage.fromBitmap(bitmap,0))
            .addOnSuccessListener { result ->
                val out = result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val r = line.boundingBox ?: android.graphics.Rect(0,0,0,0)
                        OcrBlock(0,line.text,r.left.toFloat()/bitmap.width,r.top.toFloat()/bitmap.height,r.right.toFloat()/bitmap.width,r.bottom.toFloat()/bitmap.height)
                    }
                }.filter { it.text.isNotBlank() }
                recognizer.close(); cont.resume(out)
            }
            .addOnFailureListener { e -> recognizer.close(); cont.resumeWithException(e) }
        cont.invokeOnCancellation { recognizer.close() }
    }
}