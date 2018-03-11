package ru.nobirds.invoice

import javafx.scene.Node
import org.controlsfx.glyphfont.FontAwesome

operator fun FontAwesome.get(glyph: FontAwesome.Glyph): Node = create(glyph)
operator fun FontAwesome.Glyph.invoke(font: FontAwesome): Node = font.create(this)