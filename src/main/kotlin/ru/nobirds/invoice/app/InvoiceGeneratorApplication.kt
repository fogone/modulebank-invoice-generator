package ru.nobirds.invoice.app

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.controlsfx.glyphfont.FontAwesome
import ru.nobirds.invoice.service.CrossoverService
import ru.nobirds.invoice.service.HttpSupport
import ru.nobirds.invoice.service.InvoiceService
import ru.nobirds.invoice.service.ModulebankService
import ru.nobirds.invoice.service.PayoneerService
import ru.nobirds.invoice.view.MainView
import tornadofx.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class InvoiceGeneratorApplication : App(MainView::class, Styles::class) {

    init {
        FX.dicontainer = context {
            val fontAwesome by register { FontAwesome() }
            val invoiceService by register { InvoiceService() }

            val loggingInterceptor by register {
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            }
            val okHttpClient by register {
                OkHttpClient.Builder()
                        .addInterceptor(loggingInterceptor)
                        .connectTimeout(600, TimeUnit.SECONDS)
                        .readTimeout(600, TimeUnit.SECONDS)
                        .writeTimeout(600, TimeUnit.SECONDS)
                        .build()
            }

            val httpSupport by register { HttpSupport(okHttpClient) }
            val modulebankService by register { ModulebankService(httpSupport) }

            val crossoverTimesheetService by register { CrossoverService(httpSupport) }

            val payoneerService by register { PayoneerService() }
        }
    }

}

fun context(builder: Context.() -> Unit): DIContainer {
    return Context().apply(builder)
}

class Context : DIContainer {

    data class Definition<T : Any>(val type: KClass<T>, val value: Lazy<T>)

    private val definitions = mutableMapOf<KClass<*>, Definition<*>>()

    fun <T : Any> register(type: KClass<T>, factory: () -> T): Lazy<T> {
        val result = lazy(factory)
        definitions[type] = Definition(type, result)
        return result
    }

    inline fun <reified T : Any> register(noinline factory: () -> T): Lazy<T> {
        return register(T::class, factory)
    }

    override fun <T : Any> getInstance(type: KClass<T>): T {
        return definitions[type]?.value?.value as? T ?: throw IllegalArgumentException("Definition for $type not found")
    }
}

fun main(args: Array<String>) {
    launch<InvoiceGeneratorApplication>(args)
    System.exit(0)
}