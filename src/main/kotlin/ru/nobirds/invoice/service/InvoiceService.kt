package ru.nobirds.invoice.service

import com.ibm.icu.text.RuleBasedNumberFormat
import com.ibm.icu.util.Currency as IcuCurrency
import com.ibm.icu.util.ULocale
import fr.opensagres.xdocreport.converter.ConverterTypeTo
import fr.opensagres.xdocreport.converter.Options
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry
import fr.opensagres.xdocreport.template.freemarker.FreemarkerTemplateEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*

data class Money(val value: BigDecimal, val currency: Currency) {

    private val formatter by lazy { createMoneyFormatter(currency) }

    private fun createMoneyFormatter(currency: Currency) =
            RuleBasedNumberFormat(ULocale.forLocale(Locale.forLanguageTag("ru")),
                    RuleBasedNumberFormat.SPELLOUT).apply {
                this.currency = IcuCurrency.fromJavaCurrency(currency)
            }

    fun format(): String = formatter.format(value)

}

class InvoiceService {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun generate(template: File, number: Int, weekDate: LocalDate, sum: Money, output: File) {
        require(template.exists()) { "Template file doesn't exists" }

        val firstDayOfWeek = weekDate.withDayOfWeek(DayOfWeek.MONDAY)
        val lastDayOfWeek = weekDate.withDayOfWeek(DayOfWeek.SUNDAY)

        val longSum = sum.format()

        FileInputStream(template).use { templateStream ->
            FileOutputStream(output).use { out ->
                val report = XDocReportRegistry.getRegistry()
                        .loadReport(templateStream, FreemarkerTemplateEngine())

                val context = report.createContext(mapOf(
                        "invoiceNumber" to number,
                        "documentDate" to dateFormatter.format(LocalDate.now()),
                        "sum" to sum.value,
                        "longSum" to longSum,
                        "fromDate" to dateFormatter.format(firstDayOfWeek),
                        "toDate" to dateFormatter.format(lastDayOfWeek),
                        "currency" to sum.currency.currencyCode
                ))

                val options = Options.getTo(ConverterTypeTo.PDF)

                report.convert(context, options, out)
            }
        }
    }

}

private fun LocalDate.withDayOfWeek(dayOrWeek: DayOfWeek): LocalDate {
    return with(ChronoField.DAY_OF_WEEK, dayOrWeek.value.toLong())
}
