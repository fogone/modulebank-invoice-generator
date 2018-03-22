package ru.nobirds.invoice.view

import javafx.beans.binding.Binding
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.geometry.Orientation
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*

class MainView : View("Invoice generator") {

    private val fontAwesome: FontAwesome by di()

    private val invoiceService: InvoiceService by di()

    private val modulebankService: ModulebankService by di()

    private val crossoverService: CrossoverService by di()

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

    private val invoiceDateProperty = SimpleObjectProperty(LocalDate.now().minusDays(14))
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
                            // column("Id", ModulebankOperation::id)
                            column("Amount", ModulebankOperation::amount)
                            column("Currency", ModulebankOperation::currency)
                            val dateColumn = column("Date", ModulebankOperation::executed)
                            column("Prupose", ModulebankOperation::paymentPurpose)

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
                            column("Amount", CrossoverPayment::amount)
                            // column("Type", ModulebankAccount::category)
                            column("Status", CrossoverPayment::status)
                            column("Billed", CrossoverPayment::timeSheet).value {
                                it.value.timeSheet.billed_minutes
                            }
                            column("Overtime", CrossoverPayment::timeSheet).value {
                                it.value.timeSheet.overtime_minutes
                            }
                            val fromColumn = column("From", CrossoverPayment::timeSheet)
                            fromColumn.value {
                                it.value.timeSheet.start_date
                            }
                            column("To",CrossoverPayment::timeSheet).value {
                                it.value.timeSheet.end_date
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
                fieldset("Invoice", fontAwesome[FILE]) {
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
                    field("Number") {
                        textfield(invoiceNumberProperty, NumberStringConverter())
                    }
                }
            }
            form {
                hboxConstraints {
                    hgrow = Priority.ALWAYS
                }

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

    private fun findCrossoverPayment(operation: ModulebankOperation): CrossoverPayment? {
        val from = operation.created.minusDays(14).withDayOfWeek(DayOfWeek.MONDAY)
        val to = from.plusDays(7)

        println("$from - $to")

        return crossoverPayments?.firstOrNull { it.timeSheet.start_date == from && it.timeSheet.end_date == to }
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
        val selectedWeek = selectedCrossoverPayment.timeSheet.start_date

        val weekDirectory = outputPath!!.resolve(selectedWeek.toString())

        weekDirectory.mkdirs()

        val number = invoiceNumber

        invoiceGenerationIcon.animate {
            invoiceService.generate(templatePath!!, weekDirectory.resolve("invoice-$selectedWeek.$number.pdf"),
                    number, selectedAccount!!, selectedOperation!!, selectedCrossoverPayment)
        }

        timesheetGenerationIcon.animate {
            crossoverService.grabTimesheetScreenTo(crossoverLogin, crossoverPassword,
                    selectedWeek, weekDirectory.resolve("timesheet-$selectedWeek.$number.png"))
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
                        .observable()
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


