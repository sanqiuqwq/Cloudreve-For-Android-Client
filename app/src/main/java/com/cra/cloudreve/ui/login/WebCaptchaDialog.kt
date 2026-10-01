package com.cra.cloudreve.ui.login

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/**
 * 内嵌 WebView 加载 reCAPTCHA / Turnstile / hCaptcha，
 * 用户在页面内完成人机验证后，通过 JS 桥把 token 回传给 App，作为登录的 ticket 字段。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCaptchaDialog(
    type: String,
    siteKey: String,
    serverOrigin: String,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "人机验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在加载验证组件…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    loading = false
                                    onError("验证组件加载失败，请检查网络")
                                }
                            }
                            addJavascriptInterface(
                                Bridge { json -> handleBridgeMessage(json, onToken, onError, onDismiss) },
                                "AndroidBridge"
                            )
                            loadDataWithBaseURL(serverOrigin, captchaHtml(type, siteKey), "text/html", "utf-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (loading) 64.dp else 300.dp)
                )
            }
        }
    }
}

private class Bridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(json: String) = onMessage(json)
}

private fun handleBridgeMessage(
    json: String,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
    when (obj.optString("type")) {
        "token" -> {
            val token = obj.optString("token")
            if (token.isNotBlank()) onToken(token) else onError("人机验证未返回有效凭证")
        }
        "expired" -> onError("人机验证已过期，请重新验证")
        "error" -> onError(obj.optString("message").ifBlank { "人机验证失败" })
        "close" -> onDismiss()
    }
}

private fun apiScriptUrl(type: String): String = when (type) {
    "turnstile" -> "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&hl=zh-CN"
    else -> "https://www.recaptcha.net/recaptcha/api.js?render=explicit&hl=zh-CN"
}

private fun renderCall(type: String, siteKey: String): String = when (type) {
    "turnstile" -> "turnstile.render('#target', {sitekey:'$siteKey', callback:onToken, 'error-callback':onErrorMsg, 'expired-callback':onExpired});"
    else -> "grecaptcha.ready(function(){ grecaptcha.render('target', {sitekey:'$siteKey', callback:onToken, 'error-callback':onErrorMsg, 'expired-callback':onExpired}); });"
}

private fun captchaHtml(type: String, siteKey: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <style>
        html,body{margin:0;padding:0;background:transparent;height:100%;
          display:flex;align-items:center;justify-content:center;font-family:sans-serif}
        #target{display:flex;align-items:center;justify-content:center}
      </style>
    </head>
    <body>
      <div id="target"></div>
      <script>
        function post(msg){ try{ AndroidBridge.postMessage(JSON.stringify(msg)); }catch(e){} }
        function onToken(t){ if(t){ post({type:'token', token:t}); } }
        function onExpired(){ post({type:'expired'}); }
        function onErrorMsg(){ post({type:'error', message:'人机验证失败，请重试'}); }
        window.onerror = function(m){ post({type:'error', message:String(m)}); };
      </script>
      <script src="${apiScriptUrl(type)}" async defer></script>
      <script>
        window.addEventListener('load', function(){
          try {
            ${renderCall(type, siteKey)}
          } catch (e) {
            post({type:'error', message:String(e)});
          }
        });
      </script>
    </body>
    </html>
""".trimIndent()