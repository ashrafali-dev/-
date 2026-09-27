package com.ashrafali.webtoonbridge

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.ashrafali.webtoonbridge.data.OcrBlock
import com.ashrafali.webtoonbridge.ocr.LocalOcr
import com.ashrafali.webtoonbridge.ui.TranslationOverlay
import com.ashrafali.webtoonbridge.web.WebChatBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {
    private lateinit var webtoon: WebView
    private lateinit var chat: WebView
    private lateinit var overlay: TranslationOverlay
    private lateinit var chatBridge: WebChatBridge
    private val ocr = LocalOcr()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = ConcurrentHashMap<String, List<String>>()
    private val blocksCache = ConcurrentHashMap<String, List<OcrBlock>>()
    private val pending = ConcurrentHashMap<String, Deferred<List<String>>>()
    private val aiMutex = Mutex()
    private var provider = "chatgpt"
    private var sourceLang = "ko"
    private var layoutMode = 0
    private var customPrompt = "Translate only the provided webtoon dialogue into natural, fluent literary Bengali. Preserve meaning, emotion, character voice, humor and pacing. Do not explain anything. Return exactly one line for every numbered item using the same number."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webtoon = WebView(this); chat = WebView(this); overlay = TranslationOverlay(this)
        configureWebView(webtoon); configureWebView(chat)
        findViewById<FrameLayout>(R.id.webtoonPane).apply { addView(webtoon); addView(overlay) }
        findViewById<FrameLayout>(R.id.chatPane).addView(chat)
        chatBridge = WebChatBridge(chat); chatBridge.load(provider)
        webtoon.loadUrl("https://www.google.com/")
        setupControls()
        webtoon.setOnScrollChangeListener { _, _, scrollY, _, oldY -> if (kotlin.math.abs(scrollY-oldY)>6) prefetchAhead(scrollY) }
    }

    private fun setupControls() {
        findViewById<Spinner>(R.id.providerSpinner).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("ChatGPT","Gemini"))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(p:AdapterView<*>?)=Unit
                override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){val n=if(pos==0)"chatgpt" else "gemini";if(n!=provider){provider=n;chatBridge.load(provider)}}
            }
        }
        findViewById<Spinner>(R.id.langSpinner).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("KO","JA","ZH","EN"))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(p:AdapterView<*>?)=Unit
                override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){sourceLang=listOf("ko","ja","zh","en")[pos]}
            }
        }
        findViewById<Button>(R.id.layoutButton).setOnClickListener{cycleLayout()}
        findViewById<Button>(R.id.ocrButton).setOnClickListener{translateViewport()}
        findViewById<Button>(R.id.promptButton).setOnClickListener{editPrompt()}
    }

    private fun configureWebView(v:WebView){
        v.settings.javaScriptEnabled=true;v.settings.domStorageEnabled=true;v.settings.databaseEnabled=true
        v.settings.cacheMode=WebSettings.LOAD_DEFAULT;v.settings.useWideViewPort=true;v.settings.loadWithOverviewMode=false
        v.settings.allowFileAccess=false;v.settings.mediaPlaybackRequiresUserGesture=false;v.webViewClient=WebViewClient()
    }

    private fun cycleLayout(){
        layoutMode=(layoutMode+1)%3
        val wp=findViewById<FrameLayout>(R.id.webtoonPane);val cp=findViewById<FrameLayout>(R.id.chatPane)
        when(layoutMode){0->{wp.visibility=View.VISIBLE;cp.visibility=View.VISIBLE};1->{wp.visibility=View.VISIBLE;cp.visibility=View.GONE};else->{wp.visibility=View.GONE;cp.visibility=View.VISIBLE}}
    }

    private fun editPrompt(){
        val input=EditText(this).apply{setText(customPrompt);setSelection(text.length);minLines=5;gravity=android.view.Gravity.TOP}
        AlertDialog.Builder(this).setTitle("Translation prompt").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Save"){_,_->customPrompt=input.text.toString().trim().ifBlank{customPrompt}}.show()
    }

    private fun viewportBitmap():Bitmap=Bitmap.createBitmap(webtoon.width.coerceAtLeast(1),webtoon.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888).also{webtoon.draw(android.graphics.Canvas(it))}

    private fun keyFor(bitmap:Bitmap):String{
        val tiny=Bitmap.createScaledBitmap(bitmap,32,32,true);val md=MessageDigest.getInstance("SHA-256")
        val bytes=ByteArray(32*32*4);tiny.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bytes))
        return md.digest(bytes).joinToString(""){"%02x".format(it)}
    }

    private fun prefetchAhead(scrollY:Int){
        val step=(webtoon.height*0.82f).toInt().coerceAtLeast(400)
        listOf(scrollY,scrollY+step,scrollY+step*2).forEachIndexed{i,y->webtoon.postDelayed({prefetchAt(y)},i*120L)}
    }

    private fun prefetchAt(scrollY:Int){
        val max=webtoon.contentHeight*webtoon.scale-webtoon.height
        if(max<=0)return
        val target=scrollY.coerceIn(0,max.toInt())
        webtoon.post{
            val old=webtoon.scrollY;webtoon.scrollTo(0,target);val bmp=viewportBitmap();webtoon.scrollTo(0,old)
            val key=keyFor(bmp);if(cache.containsKey(key)||pending.containsKey(key))return@post
            pending[key]=scope.async{translateBitmap(key,bmp)}
        }
    }

    private suspend fun translateBitmap(key:String,bmp:Bitmap):List<String>{
        val blocks=withContext(Dispatchers.Default){ocr.recognize(bmp,sourceLang).mapIndexed{i,b->b.copy(id=i+1)}}
        blocksCache[key]=blocks
        if(blocks.isEmpty())return emptyList()
        val result=aiMutex.withLock{chatBridge.send(buildPrompt(blocks))}
        return parse(result,blocks.size).also{cache[key]=it}
    }

    private fun translateViewport(){
        val bmp=viewportBitmap();val key=keyFor(bmp)
        scope.launch{
            try{
                val translations=cache[key] ?: (pending[key]?.await() ?: async{translateBitmap(key,bmp)}.await())
                val blocks=blocksCache[key] ?: withContext(Dispatchers.Default){ocr.recognize(bmp,sourceLang).mapIndexed{i,b->b.copy(id=i+1)}}
                blocksCache[key]=blocks;overlay.show(blocks,translations);prefetchAhead(webtoon.scrollY+webtoon.height)
            }catch(e:Exception){Toast.makeText(this@MainActivity,"Translation failed: ${e.message}",Toast.LENGTH_LONG).show()}
        }
    }

    private fun buildPrompt(blocks:List<OcrBlock>)=buildString{
        append(customPrompt);append("\n\nNumbered source text:\n")
        blocks.forEach{append("[").append(it.id).append("] ").append(it.text).append('\n')}
    }

    private fun parse(s:String,n:Int):List<String>{
        val out=MutableList(n){""}
        s.lines().forEach{line->val r=Regex("^\\s*\\[?(\\d+)\\]?\\s*[:.)-]?\\s*(.*)$").find(line)?:return@forEach
            val i=r.groupValues[1].toIntOrNull()?:return@forEach;if(i in 1..n)out[i-1]=r.groupValues[2].trim()}
        return out
    }

    override fun onDestroy(){scope.cancel();webtoon.destroy();chat.destroy();super.onDestroy()}
}