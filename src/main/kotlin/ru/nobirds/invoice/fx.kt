package ru.nobirds.invoice

import javafx.scene.Node
import javafx.util.StringConverter
import org.controlsfx.glyphfont.FontAwesome

operator fun FontAwesome.get(glyph: FontAwesome.Glyph): Node = create(glyph)
operator fun FontAwesome.Glyph.invoke(font: FontAwesome): Node = font.create(this)

fun <T> converter(converter: (T) -> String): StringConverter<T> = object: StringConverter<T>() {
    override fun toString(`object`: T): String = converter(`object`)
    override fun fromString(string: String?): T = throw IllegalArgumentException()
}