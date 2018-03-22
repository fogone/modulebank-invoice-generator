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
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*

data class Money(val value: BigDecimal, val currency: Currency) {

    private val locale = Locale.forLanguageTag("ru")
    private val spellOutFormatter by lazy { createSpellOutFormatter(currency) }
    private val numberFormatter = NumberFormat.getInstance(locale)

    private fun createSpellOutFormatter(currency: Currency) =
            RuleBasedNumberFormat(ULocale.forLocale(locale),
                    RuleBasedNumberFormat.SPELLOUT).apply {
                this.currency = IcuCurrency.fromJavaCurrency(currency)
            }

    fun formatNumber(): String = numberFormatter.format(value)

    fun formatSpellOut(): String = spellOutFormatter.format(value)
}

class InvoiceService {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun generate(template: File, output: File, invoiceNumber: Int,
                 selectedAccount: ModulebankAccount, selectedOperation: ModulebankOperation,
                 selectedCrossoverPayment: CrossoverPayment) {

        require(template.exists()) { "Template file doesn't exists" }

        val weekDate = selectedCrossoverPayment.timeSheet.start_date

        val money = Money(selectedOperation.amount, Currency.getInstance(selectedOperation.currency))

        val firstDayOfWeek = weekDate.withDayOfWeek(DayOfWeek.MONDAY)
        val lastDayOfWeek = weekDate.withDayOfWeek(DayOfWeek.SUNDAY)

        val sum = money.formatNumber()
        val longSum = money.formatSpellOut()

        FileInputStream(template).use { templateStream ->
            FileOutputStream(output).use { out ->
                val report = XDocReportRegistry.getRegistry()
                        .loadReport(templateStream, FreemarkerTemplateEngine())

                val context = report.createContext(mapOf(
                        "accountNumber" to selectedAccount.number.toString(),
                        "invoiceNumber" to invoiceNumber,
                        "documentDate" to dateFormatter.format(selectedOperation.created),
                        "sum" to sum,
                        "longSum" to longSum,
                        "fromDate" to dateFormatter.format(firstDayOfWeek),
                        "toDate" to dateFormatter.format(lastDayOfWeek),
                        "currency" to money.currency.currencyCode
                ))

                val options = Options.getTo(ConverterTypeTo.PDF)

                report.convert(context, options, out)
            }
        }
    }

}

fun LocalDate.withDayOfWeek(dayOrWeek: DayOfWeek): LocalDate {
    return with(ChronoField.DAY_OF_WEEK, dayOrWeek.value.toLong())
}
