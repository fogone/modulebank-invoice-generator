package ru.nobirds.invoice.app

import okhttp3.OkHttpClient
import org.controlsfx.glyphfont.FontAwesome
import ru.nobirds.invoice.service.InvoiceService
import ru.nobirds.invoice.service.ModulebankService
import ru.nobirds.invoice.service.ModulebankHttpSupport
import ru.nobirds.invoice.view.MainView
import tornadofx.*
import kotlin.reflect.KClass

class InvoiceGeneratorApplication: App(MainView::class, Styles::class) {

    init {
        FX.dicontainer = context {
            val fontAwesome by register { FontAwesome() }
            val invoiceService by register { InvoiceService() }

            val okHttpClient by register { OkHttpClient() }

            val modulebankHttpSupport by register { ModulebankHttpSupport(okHttpClient) }
            val modulebankService by register { ModulebankService(modulebankHttpSupport) }
        }
    }

}

fun context(builder: Context.() -> Unit): DIContainer {
    return Context().apply(builder)
}

class Context : DIContainer {

    data class Definition<T: Any>(val type: KClass<T>, val value: Lazy<T>)

    private val definitions = mutableMapOf<KClass<*>, Definition<*>>()

    fun <T: Any> register(type: KClass<T>, factory: () -> T): Lazy<T> {
        val result = lazy(factory)
        definitions[type] = Definition(type, result)
        return result
    }

    inline fun <reified T: Any> register(noinline factory: () -> T): Lazy<T> {
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