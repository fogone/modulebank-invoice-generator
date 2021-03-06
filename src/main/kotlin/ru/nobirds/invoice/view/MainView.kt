package ru.nobirds.invoice.view

import javafx.beans.binding.Binding
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.util.Duration
import javafx.util.converter.NumberStringConverter
import kotlinx.coroutines.experimental.javafx.JavaFx
import kotlinx.coroutines.experimental.launch
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import ru.nobirds.invoice.converter
import ru.nobirds.invoice.get
import ru.nobirds.invoice.persistent
import ru.nobirds.invoice.service.*
import tornadofx.*
import java.io.File
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class MainView : View("Invoice generator") {

    private val fontAwesome: FontAwesome by di()

    private val invoiceService: InvoiceService by di()

    private val modulebankService: ModulebankService by di()

    private val crossoverService: CrossoverService by di()

    private val payoneerService: PayoneerService by di()

    private var bankProcessNode: Node = fontAwesome[GEAR]
    private var crossoverProcessNode: Node = fontAwesome[GEAR]

    private val bankTokenProperty = SimpleStringProperty().persistent(config, "bankToken", null)
    private var bankToken: String? by bankTokenProperty

    private val connectedToBankProperty = SimpleBooleanProperty()
    private var connectedToBank by connectedToBankProperty
    private val connectedToBankIcon = createStateIcon(connectedToBankProperty)

    private val accountsProperty = SimpleListProperty<ModulebankAccount>(emptyList<ModulebankAccount>().observable())
    private var accounts by accountsProperty

    private val selectedAccountProperty = SimpleObjectProperty<ModulebankAccount>().apply {
        onChange {
            it?.let {
                updateOperations(it)
            }
        }
    }

    private var selectedAccount: ModulebankAccount? by selectedAccountProperty

    private val operationsProperty = SimpleListProperty<ModulebankOperation>(emptyList<ModulebankOperation>().observable())
    private var operations by operationsProperty

    private val selectedOperationProperty = SimpleObjectProperty<ModulebankOperation>()
    private var selectedOperation by selectedOperationProperty

    private val crossoverLoginProperty = SimpleStringProperty().persistent(config, "crossoverLogin", null)
    private var crossoverLogin by crossoverLoginProperty

    private val crossoverPasswordProperty = SimpleStringProperty().persistent(config, "crossoverPassword", null)
    private var crossoverPassword by crossoverPasswordProperty

    private val crossoverConnectionTokenProperty = SimpleStringProperty()
    private var crossoverConnectionToken by crossoverConnectionTokenProperty

    private val crossoverConnectedProperty = crossoverConnectionTokenProperty.isNotEmpty.apply {
        onChange {
            if (it) {
                updateCrossoverPayments()
            } else {
                crossoverPayments = emptyList<CrossoverPayment>().observable()
            }
        }
    }

    private val selectedCrossoverPaymentProperty = SimpleObjectProperty<CrossoverPayment>()
    private var selectedCrossoverPayment by selectedCrossoverPaymentProperty

    private val crossoverConnected by crossoverConnectedProperty

    private val crossoverConnectedIcon = createStateIcon(crossoverConnectedProperty)

    private fun createStateIcon(value: ObservableValue<Boolean>): Binding<Node?> {
        return value.objectBinding {
            when (it) {
                null -> fontAwesome[QUESTION_CIRCLE]
                true -> fontAwesome[CHECK_CIRCLE]
                false -> fontAwesome[BAN]
            }
        }
    }

    private val crossoverPaymentsProperty = SimpleListProperty<CrossoverPayment>()
    private var crossoverPayments by crossoverPaymentsProperty

    private val templatePathProperty = SimpleObjectProperty<File?>().persistent(config, "templatePath", null)
    private var templatePath: File? by templatePathProperty

    private val outputPathProperty = SimpleObjectProperty<File?>().persistent(config, "outputPath", null)
    private var outputPath: File? by outputPathProperty

    private val invoiceNumberProperty = SimpleIntegerProperty().persistent(config, "invoiceNumber", 1)
    private var invoiceNumber: Int by invoiceNumberProperty

    private val invoiceDateProperty = SimpleObjectProperty(LocalDate.now())
    private var invoiceDate by invoiceDateProperty

    private val generationEnabled = selectedOperationProperty.isNotNull and
            (crossoverLoginProperty.isNotNull and crossoverPasswordProperty.isNotNull) and
            templatePathProperty.booleanBinding { it?.exists() == true } and
            outputPathProperty.booleanBinding { it?.exists() == true && it.isDirectory == true } and
            invoiceNumberProperty.greaterThan(0) and
            selectedCrossoverPaymentProperty.isNotNull and
            crossoverConnectedProperty

    private val invoiceGenerationIcon = SimpleObjectProperty(fontAwesome[QUESTION_CIRCLE])
    private val timesheetGenerationIcon = SimpleObjectProperty(fontAwesome[QUESTION_CIRCLE])
    private val mailEncodingIcon = SimpleObjectProperty(fontAwesome[QUESTION_CIRCLE])

    private val payoneerMailProperty = SimpleObjectProperty<File?>()
    private var payoneerMail by payoneerMailProperty

    override val root = vbox {
        splitpane(Orientation.HORIZONTAL) {
            vboxConstraints {
                vgrow = Priority.ALWAYS
            }
            form {
                fieldset("Module bank", fontAwesome[BANK], Orientation.VERTICAL) {
                    field("Token") {
                        passwordfield(bankTokenProperty)
                        button("", fontAwesome[ARROW_CIRCLE_O_RIGHT]) {
                            enableWhen { bankTokenProperty.isNotEmpty }
                            action {
                                connectToBank()
                            }
                        }
                        add(bankProcessNode)
                    }

                    field {
                        label(connectedToBankProperty, converter = converter { if(it) "State: connected" else "State: not connected" }) {
                            graphicProperty().bind(connectedToBankIcon)
                        }
                    }

                    field("Accounts", Orientation.VERTICAL) {
                        tableview(accountsProperty) {
                            enableWhen { connectedToBankProperty }
                            column("Name", ModulebankAccount::accountName)
                            // column("Type", ModulebankAccount::category)
                            column("Currency", ModulebankAccount::currency)
                            column("Balance", ModulebankAccount::balance)

                            columnResizePolicy = SmartResize.POLICY
                            selectedAccountProperty.bind(selectionModel.selectedItemProperty())

                            prefHeight = 200.0
                        }
                    }

                    field("Operations", Orientation.VERTICAL) {
                        tableview(operationsProperty) {
                            enableWhen { connectedToBankProperty and selectedAccountProperty.isNotNull and Bindings.isNotEmpty(crossoverPaymentsProperty) }
                            column("Amount", ModulebankOperation::amount) {
                                style {
                                    alignment = Pos.CENTER_RIGHT
                                }
                            }
                            column("Currency", ModulebankOperation::currency)
                            column("Created", ModulebankOperation::created)
                            val dateColumn = column("Executed", ModulebankOperation::executed)

                            columnResizePolicy = SmartResize.POLICY
                            selectedOperationProperty.bind(selectionModel.selectedItemProperty())

                            sortOrder.add(dateColumn)

                            vgrow = Priority.ALWAYS
                        }
                    }
                }

            }
            form {
                fieldset("Crossover", fontAwesome[CIRCLE_ALT], Orientation.VERTICAL) {
                    field("Login") {
                        textfield(crossoverLoginProperty)
                    }
                    field("Password") {
                        passwordfield(crossoverPasswordProperty)
                        button("", fontAwesome[ARROW_CIRCLE_O_RIGHT]) {
                            enableWhen { crossoverLoginProperty.isNotEmpty and crossoverPasswordProperty.isNotEmpty }
                            action {
                                connectToCrossover()
                            }
                        }
                        add(crossoverProcessNode)
                    }
                    field {
                        label(crossoverConnectedProperty, converter = converter { if(it) "State: connected" else "State: not connected" }) {
                            graphicProperty().bind(crossoverConnectedIcon)
                        }
                    }
                    field("Payments", Orientation.VERTICAL) {
                        tableview(crossoverPaymentsProperty) {
                            enableWhen { crossoverConnectedProperty }
                            column("Amount", CrossoverPayment::amount) {
                                style {
                                    alignment = Pos.CENTER_RIGHT
                                }
                            }
                            // column("Type", ModulebankAccount::category)
                            column("Status", CrossoverPayment::status)
                            column("Billed", CrossoverPayment::paidHours) {
                                style {
                                    alignment = Pos.CENTER_RIGHT
                                }
                            }.value {
                                formatDuration(it.value.paidHours)
                            }

                            column("Overtime", CrossoverPayment::paidHours) {
                                style {
                                    alignment = Pos.CENTER_RIGHT
                                }
                            }.value {
                                formatDuration(it.value.paidHours - it.value.weeklyHourLimit)
                            }

                            val fromColumn = column("From", CrossoverPayment::periodStartDate)
                            fromColumn.value {
                                it.value.periodStartDate
                            }
                            column("To",CrossoverPayment::periodEndDate).value {
                                it.value.periodEndDate
                            }

                            columnResizePolicy = SmartResize.POLICY

                            selectedCrossoverPaymentProperty.bind(selectionModel.selectedItemProperty())

                            selectedOperationProperty.onChange {
                                if (it != null) {
                                    findCrossoverPayment(it)?.let {
                                        selectionModel.select(it)
                                    }
                                }
                            }

                            sortOrder.add(fromColumn)

                            vgrow = Priority.ALWAYS
                        }
                    }
                }
            }
        }
        hbox {
            form {
                fieldset("Template", fontAwesome[FILE_TEXT]) {
                    field("Template") {
                        label(templatePathProperty.stringBinding { it?.toString() ?: "[Please select]" })
                        button("", fontAwesome[FILE]) {
                            action {
                                chooseTemplate()?.let {
                                    templatePath = it
                                }
                            }
                        }
                    }
                    field("Output dir") {
                        label(outputPathProperty.stringBinding { it?.toString() ?: "[Please select]" })
                        button("", fontAwesome[FILE]) {
                            action {
                                selectTargetFile()?.let {
                                    outputPath = it
                                }
                            }
                        }
                    }
                }
            }

            separator(Orientation.VERTICAL)

            form {
                fieldset("Invoice", fontAwesome[BITCOIN]) {
                    field("Number") {
                        textfield(invoiceNumberProperty, NumberStringConverter())
                    }
                    field("Date") {
                        datepicker(invoiceDateProperty) {
                            selectedOperationProperty.onChange {
                                if (it != null) {
                                    value = it.created
                                }
                            }
                        }
                    }
                }
            }

            separator(Orientation.VERTICAL)

            form {
                fieldset("Payoneer", fontAwesome[MONEY]) {
                    field("Encoded mail") {
                        label(payoneerMailProperty.stringBinding { it?.toString() ?: "[Please select]" })
                        button("", fontAwesome[FILE]) {
                            action {
                                chooseTemplate()?.let {
                                    payoneerMail = it
                                }
                            }
                        }
                    }
                }
            }

            separator(Orientation.VERTICAL)

            region {
                hboxConstraints {
                    hgrow = Priority.ALWAYS
                }
            }

            separator(Orientation.VERTICAL)

            form {
                fieldset("Generation", fontAwesome[COMMENTS]) {
                    field {
                        label("Invoice generation") {
                            graphicProperty().bind(invoiceGenerationIcon)
                        }
                    }
                    field {
                        label("Timesheet grab") {
                            graphicProperty().bind(timesheetGenerationIcon)
                        }
                    }
                    field {
                        label("Mail encoding") {
                            graphicProperty().bind(mailEncodingIcon)
                        }
                    }
                    field {
                        vbox {
                            button("Generate", fontAwesome[GEAR]) {
                                enableWhen { generationEnabled }
                                action {
                                    generate()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatDuration(hours: BigDecimal): String = buildString {
        val minutes = (hours * 60.toBigDecimal()).toInt()
        val hours = minutes / 60

        if (hours > 0) {
            append("${hours}h")
        }

        val mins = minutes % 60

        if (mins > 0) {
            if (hours > 0) {
                append(" ")
            }
            append("${mins}m")
        }
    }

    private fun findCrossoverPayment(operation: ModulebankOperation): CrossoverPayment? {
        val from = operation.created.minusDays(14).withDayOfWeek(DayOfWeek.MONDAY)

        return crossoverPayments?.firstOrNull { it.periodStartDate == from }
    }

    private fun Region.enableDebugBorders() {
        border = Border(BorderStroke(Color.RED, BorderStrokeStyle.DOTTED, CornerRadii.EMPTY, BorderWidths.DEFAULT))
    }

    init {
        runLater {
            connectToBank()
            connectToCrossover()
        }
    }

    private fun connectToCrossover() {
        if (crossoverLogin?.isNotBlank() == true && crossoverPassword?.isNotBlank() == true) {
            launch(JavaFx) {
                crossoverProcessNode.withRotation {
                    crossoverConnectionToken = crossoverService.authenticate(crossoverLogin, crossoverPassword)
                }
            }
        }
    }

    private fun generate() {
        val selectedWeek = selectedCrossoverPayment.periodStartDate

        val weekDirectory = outputPath!!.resolve(selectedWeek.toString())

        weekDirectory.mkdirs()

        val number = invoiceNumber

        invoiceGenerationIcon.animate {
            invoiceService.generate(templatePath!!, weekDirectory.resolve("invoice-$selectedWeek.$number.pdf"),
                    number, invoiceDate, selectedAccount!!, selectedOperation!!, selectedCrossoverPayment)
        }

        timesheetGenerationIcon.animate {
            crossoverService.grabTimesheetScreenTo(crossoverLogin, crossoverPassword,
                    selectedWeek, weekDirectory.resolve("timesheet-$selectedWeek.$number.png"))
        }

        if (payoneerMail?.exists() == true) {
            mailEncodingIcon.animate {
                payoneerService.prepareMail(payoneerMail?.readText() ?: "",
                        weekDirectory.resolve("payoneer-$selectedWeek.$number.html"))
            }
        }

        invoiceNumber++
    }

    private fun setResult(icon: ObjectProperty<Node>, result: Boolean) {
        icon.value =  when(result) {
            true -> fontAwesome[CHECK_CIRCLE]
            false -> fontAwesome[BAN]
        }
        icon.value.rotate = 0.0
    }

    private fun connectToBank() = launch(JavaFx) {
        bankToken?.let { token ->
            bankProcessNode.withRotation {
                try {
                    accounts = modulebankService.findAccounts(token).observable()
                    connectedToBank = true
                } catch (e: Exception) {
                    connectedToBank = false
                }
            }
        }
    }

    private fun updateOperations(account: ModulebankAccount) = launch(JavaFx) {
        bankToken?.let { token ->
            bankProcessNode.withRotation {
                try {
                    operations = modulebankService.findOperations(token, account).observable()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // oops
                }
            }
        }
    }

    private fun updateCrossoverPayments() {
        launch(JavaFx) {
            crossoverProcessNode.withRotation {
                crossoverPayments = crossoverService
                        .findPayments(crossoverConnectionToken, LocalDate.now().minusMonths(3), LocalDate.now())
                        .payments.observable()
            }
        }
    }

    private inline fun <R> Node.withRotation(block: () -> R): R {
        val timeline = timeline {
            keyframe(Duration.seconds(2.0)) {
                keyvalue(rotateProperty(), 360)
            }
            keyframe(Duration.seconds(0.0)) {
                keyvalue(rotateProperty(), 0)
            }
            cycleCount = Int.MAX_VALUE
        }
        try {
            return block()
        } finally {
            timeline.stop()
        }
    }

    private fun ObjectProperty<Node>.animate(block: () -> Unit) {
        value = fontAwesome[GEAR]

        launch {
            val result = value.withRotation {
                try {
                    block()
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            runLater {
                setResult(this@animate, result)
            }
        }
    }

    private fun selectTargetFile(): File? {
        return chooseDirectory("Select target directory")
    }

    private fun chooseTemplate(): File? {
        return selectFile("Select template", emptyArray(), FileChooserMode.Single)
    }

    private fun selectFile(message: String, filter: Array<FileChooser.ExtensionFilter>, chooserMode: FileChooserMode): File? {
        val choosedFiles = chooseFile(message, filter, chooserMode)

        if (choosedFiles.isNotEmpty()) {
            return choosedFiles.first()
        }

        return null
    }

}


