package jp.sd.lifelogapp

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

private val gasHttpClient = HttpClient(OkHttp) {
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
}

suspend fun postToGas(body: String): String {
    val source = Regex("\"source\"\\s*:\\s*\"([a-z_]+)\"").find(body)?.groupValues?.get(1) ?: "?"
    val t0 = System.currentTimeMillis()
    var response: HttpResponse = gasHttpClient.post(BuildConfig.GAS_WEBAPP_URL) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    val t1 = System.currentTimeMillis()
    if (response.status.value in 300..399) {
        val location = response.headers[HttpHeaders.Location]
        if (location != null) {
            response = gasHttpClient.get(location)
        }
    }
    val text = response.bodyAsText()
    val t2 = System.currentTimeMillis()
    Log.d("GasTiming", "$source post=${t1 - t0}ms redirect=${t2 - t1}ms total=${t2 - t0}ms bytes=${text.length}")
    return text
}
