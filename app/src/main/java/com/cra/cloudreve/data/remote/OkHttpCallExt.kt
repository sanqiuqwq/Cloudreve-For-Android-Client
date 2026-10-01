package com.cra.cloudreve.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 把 OkHttp 的异步调用桥接为协程，取消时立即中断底层请求。
 *
 * 不能用 `Job.invokeOnCompletion` 注册中断：它默认只在协程**结束**时回调，
 * 而协程此时正阻塞在同步请求上，会与中断逻辑互相等待，导致「暂停/取消」失效。
 * `CancellableContinuation.invokeOnCancellation` 在取消发生的那一刻就会回调，是稳定的公开 API。
 *
 * @param consume 在响应可用时消费响应体（内部会自动关闭 response）
 */
internal suspend fun Call.await(consume: (Response) -> Unit) {
    suspendCancellableCoroutine<Unit> { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { consume(it) }
                    if (cont.isActive) cont.resume(Unit)
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }
}