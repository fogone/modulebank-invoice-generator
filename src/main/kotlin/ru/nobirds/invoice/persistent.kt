package ru.nobirds.invoice

import javafx.beans.property.Property
import tornadofx.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.reflect.KClass

inline fun <reified T : Any, P : Property<T?>> P.persistent(config: ConfigProperties, name: String, defaultValue: T?): P {
    value = config.read(name, defaultValue)
    onChange {
        if (it != null) {
            config.write(name, it)
        } else {
            config.delete(name)
        }
    }
    return this
}

interface StringSerializer<T : Any> {

    val type: KClass<T>

    fun serialize(value: T): String

    fun deserialize(text: String): T

}

inline fun <reified T : Any> stringSerializer(crossinline deserializer: (String) -> T): StringSerializer<T> =
        stringSerializer(Any::toString, deserializer)

inline fun <reified T : Any> stringSerializer(crossinline serializer: (T) -> String,
                                              crossinline deserializer: (String) -> T) = object : StringSerializer<T> {
    override val type: KClass<T>
        get() = T::class

    override fun serialize(value: T): String = serializer(value)
    override fun deserialize(text: String): T = deserializer(text)
}

object StringSerializers {

    val DEFAULT = listOf(
            stringSerializer { it },
            stringSerializer(String::toInt),
            stringSerializer(String::toLong),
            stringSerializer(String::toDouble),
            stringSerializer<Number>(String::toDouble),
            stringSerializer(String::toBoolean),
            stringSerializer(::BigDecimal),
            stringSerializer(::File),
            stringSerializer { LocalDate.parse(it) }
    ).associateBy { it.type as KClass<out Any> }

    inline fun <reified T : Any> find(): StringSerializer<T> {
        return DEFAULT[T::class] as? StringSerializer<T>
                ?: throw IllegalArgumentException("For type ${T::class} not found serializer")
    }

}

inline fun <reified T: Any> ConfigProperties.read(name: String, defaultValue: T?): T? {
    return string(name)?.let { StringSerializers.find<T>().deserialize(it) } ?: defaultValue
}

inline fun <reified T : Any> ConfigProperties.write(name: String, value: T) {
    set(name, StringSerializers.find<T>().serialize(value))
    save()
}

fun ConfigProperties.delete(name: String) {
    remove(name)
    save()
}