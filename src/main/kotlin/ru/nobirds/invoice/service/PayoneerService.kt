package ru.nobirds.invoice.service

import com.lowagie.text.pdf.codec.Base64
import java.io.File

class PayoneerService {

    fun prepareMail(encodedMailText: String, output: File) {
        val (_, body) = encodedMailText.split("\r\n\r\n")
        val decoded = String(Base64.decode(body))

        val result = decoded
                .replace("=\"\"(.+?)\"\"".toRegex()) { r ->
                    val (attributeValue) = r.destructured
                    "=\"$attributeValue\""
                }
                .replace("_x000D_", "")

        output.writeText(result)
    }

}