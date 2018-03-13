package ru.nobirds.invoice.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.*
import java.io.IOException
import java.io.Reader
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine

class ApiException(private val error: String, private val request: Request) : RuntimeException(buildString {
    appendln("Request: $request")
    append("Responded with error message: $error")
})

class HttpSupport(private val client: OkHttpClient) {

    val jsonMediaType = MediaType.parse("application/json")

    val objectMapper: ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
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
        if (C::class == Unit::class) {
            return Unit as T
        }

        val reader = response.body()?.charStream() ?: throw IllegalArgumentException()
        return readValue(reader, fetcher)
    }

    inline fun <reified C : Any, T> readValue(reader: Reader, fetcher: C.() -> T) =
            objectMapper.readValue<C>(reader).fetcher()

    suspend inline fun <reified R: Any> get(url: HttpUrl, noinline auth: Request.Builder.()->Request.Builder): R {
        return sendRequest(Request.Builder().get().url(url).auth().build())
    }

    suspend inline fun <reified R: Any> post(url: HttpUrl, token: String): R {
        return post<Unit, R>(url) { withToken(token) }
    }

    suspend inline fun <reified R: Any, T: Any> post(url: HttpUrl, request: T, token: String): R {
        return post(url, request) { withToken(token) }
    }

    suspend inline fun <reified R: Any> post(url: HttpUrl, noinline auth: Request.Builder.()->Request.Builder): R {
        return post<Unit, R>(url, null, auth)
    }

    suspend inline fun <T : Any, reified R: Any> post(url: HttpUrl, request: T? = null,
                                                      noinline auth: Request.Builder.()->Request.Builder): R {
        val body = RequestBody.create(jsonMediaType,
                request?.let { objectMapper.writeValueAsBytes(it) } ?: byteArrayOf())

        return sendRequest(Request.Builder().url(url).post(body).auth().build())
    }

}

fun Request.Builder.withToken(token: String): Request.Builder = header("Authorization", "Bearer $token")
fun Request.Builder.withBasic(username: String, password: String): Request.Builder =
        header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}")
fun Request.Builder.withXAuthToken(token: String): Request.Builder = header("X-Auth-Token", token)