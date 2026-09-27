package com.ashrafali.webtoonbridge.web

import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONObject

class WebChatBridge(private val webView: WebView) {
    private var lastAssistant = ""

    fun load(provider: String) {
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.userAgentString = webView.settings.userAgentString + " WebtoonBridge/0.2"
        webView.loadUrl(if (provider == "gemini") "https://gemini.google.com/app" else "https://chatgpt.com/")
    }

    suspend fun send(prompt: String): String = suspendCancellableCoroutine { cont ->
        webView.post {
            webView.evaluateJavascript(readAssistantJs()) { raw ->
                lastAssistant = decode(raw)
                webView.evaluateJavascript(buildSendJs(prompt)) { _ ->
                    webView.postDelayed(object : Runnable {
                        override fun run() {
                            if (cont.isCompleted) return
                            webView.evaluateJavascript(readAssistantJs()) { value ->
                                val current = decode(value)
                                if (current.length > 10 && current != lastAssistant && looksLikeCompletedResponse(current)) {
                                    lastAssistant = current
                                    cont.resume(current)
                                } else webView.postDelayed(this, 450)
                            }
                        }
                    }, 650)
                }
            }
        }
    }

    private fun buildSendJs(prompt: String): String {
        val p = JSONObject.quote(prompt)
        return """(function(){
          const p=$p;
          const els=[...document.querySelectorAll('textarea,[contenteditable="true"]')].filter(e=>e.offsetParent!==null);
          const box=els[els.length-1]; if(!box) return 'NO_INPUT'; box.focus();
          if(box.tagName==='TEXTAREA'){
            const s=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');
            if(s&&s.set)s.set.call(box,p); else box.value=p;
            box.dispatchEvent(new Event('input',{bubbles:true})); box.dispatchEvent(new Event('change',{bubbles:true}));
          }else{
            document.execCommand('insertText',false,p);
            box.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));
          }
          const btn=[...document.querySelectorAll('button')].find(b=>/send|submit/i.test((b.getAttribute('aria-label')||'')+' '+b.innerText) && !b.disabled);
          if(btn) btn.click(); else box.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));
          return 'SENT';
        })();"""
    }

    private fun readAssistantJs() = """(function(){
      const sels=['main [data-message-author-role="assistant"]','main article','main .markdown','main .prose'];
      let nodes=[]; for(const s of sels) nodes.push(...document.querySelectorAll(s));
      nodes=nodes.filter(x=>x.offsetParent!==null && (x.innerText||'').trim());
      return JSON.stringify(nodes.length ? nodes[nodes.length-1].innerText : '');
    })()"""

    private fun decode(raw: String): String = try { JSONObject.parse(raw).toString() }
    catch (_: Exception) { raw.trim('"').replace("\\n","
").replace("\\"",""") }

    private fun looksLikeCompletedResponse(text: String): Boolean =
        !text.endsWith("…") && !text.endsWith("...") && text.contains(Regex("\\[?\\d+\\]?"))
}