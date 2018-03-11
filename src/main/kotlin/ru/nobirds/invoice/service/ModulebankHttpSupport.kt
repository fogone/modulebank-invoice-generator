package ru.nobirds.invoice.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.*
import java.io.IOException
import java.io.Reader
import kotlin.coroutines.experimental.suspendCoroutine

class ApiException(private val error: String, private val request: Request) : RuntimeException(buildString {
    appendln("Request: $request")
    append("Responded with error message: $error")
})

class ModulebankHttpSupport(private val client: OkHttpClient) {

    val jsonMediaType = MediaType.parse("application/json")

    val objectMapper: ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend inline fun <reified C : Any> sendRequest(request: Request): C =
            sendRequest<C, C>(request) { this }

    suspend fun send(request: Request): Response = suspendCoroutine { c ->
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                c.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    c.resume(response)
                } else {
                    c.resumeWithException(ApiException("", request)) // todo
                }
            }
        })
    }

    suspend inline fun <reified C : Any, T> sendRequest(request: Request, crossinline fetcher: C.() -> T): T =
            readValue(send(request), fetcher)

    inline fun <reified C : Any, T> readValue(response: Response, fetcher: C.() -> T): T {
        val reader = response.body()?.charStream() ?: throw IllegalArgumentException()
        return readValue(reader, fetcher)
    }

    inline fun <reified C : Any, T> readValue(reader: Reader, fetcher: C.() -> T) =
            objectMapper.readValue<C>(reader).fetcher()

    fun createPostRequest(url: HttpUrl, token: String, body: RequestBody = RequestBody.create(jsonMediaType, "")) =
            Request.Builder().url(url).post(body).withToken(token).build()

    fun url(): HttpUrl.Builder = HttpUrl.Builder().scheme("https")
            .host("api.modulbank.ru").addPathSegment("v1")

    suspend inline fun <reified R: Any> post(url: HttpUrl, token: String): R {
        return post<Unit, R>(url, token)
    }

    suspend inline fun <T : Any, reified R: Any> post(url: HttpUrl, token: String, request: T? = null): R {
        val body = RequestBody.create(jsonMediaType,
                request?.let { objectMapper.writeValueAsBytes(it) } ?: byteArrayOf())

        return sendRequest(createPostRequest(url, token, body))
    }

}

fun Request.Builder.withToken(token: String): Request.Builder = header("Authorization", "Bearer $token")
