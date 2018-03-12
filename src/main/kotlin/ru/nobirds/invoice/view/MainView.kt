package ru.nobirds.invoice.view

import javafx.beans.property.*
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.stage.FileChooser
import javafx.util.Duration
import javafx.util.StringConverter
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
import java.time.LocalDate
import java.util.*

class MainView : View("Invoice generator") {

    private val fontAwesome: FontAwesome by di()

    private val invoiceService: InvoiceService by di()

    private val modulebankService: ModulebankService by di()

    private val crossoverTimesheetService: CrossoverTimesheetService by di()

    private var processNode: Node? = null

    private val bankTokenProperty = SimpleStringProperty().persistent(config, "bankToken", null)
    private var bankToken: String? by bankTokenProperty

    private val connectedToBankProperty = SimpleBooleanProperty()
    private var connectedToBank by connectedToBankProperty

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

    private val crossoverConnectedProperty = SimpleBooleanProperty()
    private var crossoverConnected by crossoverConnectedProperty
    private val crossoverConnectedIcon = crossoverConnectedProperty.objectBinding {
        when(it) {
            null -> fontAwesome[QUESTION_CIRCLE]
            true -> fontAwesome[CHECK_CIRCLE]
            false -> fontAwesome[BAN]
        }
    }

    private val templatePathProperty = SimpleObjectProperty<File?>().persistent(config, "templatePath", null)
    private var templatePath: File? by templatePathProperty

    private val invoiceNumberProperty = SimpleIntegerProperty().persistent(config, "invoiceNumber", 1)
    private var invoiceNumber: Int by invoiceNumberProperty

    private val selectedWeekProperty = SimpleObjectProperty<LocalDate>().persistent(config, "selectedWeek", null)
    private var selectedWeek: LocalDate? by selectedWeekProperty

    private val generationEnabled = selectedOperationProperty.isNotNull and
            (crossoverLoginProperty.isNotNull and crossoverPasswordProperty.isNotNull) and
            templatePathProperty.booleanBinding { it?.exists() == true } and
            invoiceNumberProperty.greaterThan(0) and
            selectedWeekProperty.isNotNull and
            crossoverConnectedProperty

    private val invoiceGenerationIcon = SimpleObjectProperty(fontAwesome[QUESTION_CIRCLE])
    private val timesheetGenerationIcon = SimpleObjectProperty(fontAwesome[QUESTION_CIRCLE])

    override val root = form {
        fieldset("Module bank", fontAwesome[BANK]) {
            field("Token") {
                passwordfield(bankTokenProperty)
                button("", fontAwesome[ARROW_CIRCLE_O_RIGHT]) {
                    enableWhen { bankTokenProperty.isNotEmpty }
                    action {
                        connect()
                    }
                }
                processNode = fontAwesome[GEAR]
                add(processNode!!)
            }

            field("Accounts") {
                tableview(accountsProperty) {
                    enableWhen { connectedToBankProperty }
                    column("Name", ModulebankAccount::accountName)
                    // column("Type", ModulebankAccount::category)
                    column("Currency", ModulebankAccount::currency)
                    column("Balance", ModulebankAccount::balance)

                    columnResizePolicy = SmartResize.POLICY
                    selectedAccountProperty.bind(selectionModel.selectedItemProperty())

                    prefHeight = 100.0
                }
            }

            field("Operations") {
                tableview(operationsProperty) {
                    enableWhen { connectedToBankProperty and selectedAccountProperty.isNotNull }
                    // column("Id", ModulebankOperation::id)
                    column("Amount", ModulebankOperation::amount)
                    column("Currency", ModulebankOperation::currency)
                    column("Date", ModulebankOperation::executed)
                    column("Source", ModulebankOperation::contragentName)

                    columnResizePolicy = SmartResize.POLICY
                    selectedOperationProperty.bind(selectionModel.selectedItemProperty())

                    prefHeight = 200.0
                }
            }
        }

        fieldset("Crossover", fontAwesome[CIRCLE_ALT]) {
            field("Login") {
                textfield(crossoverLoginProperty)
            }
            field("Password") {
                passwordfield(crossoverPasswordProperty)
                button("", fontAwesome[ARROW_CIRCLE_O_RIGHT]) {
                    enableWhen { crossoverLoginProperty.isNotEmpty and crossoverPasswordProperty.isNotEmpty }
                    action {
                        checkCrossoverConnection()
                    }
                }
            }
            field("Connected") {
                label(crossoverConnectedProperty, converter = converter { if(it) "Connected" else "Not connected" }) {
                    graphicProperty().bind(crossoverConnectedIcon)
                }
            }
        }

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
            field("Number") {
                textfield(invoiceNumberProperty, NumberStringConverter())
            }
            field("Week") {
                datepicker(selectedWeekProperty) {
                    enableWhen { templatePathProperty.isNotNull }
                }
            }
            field {
                vbox {
                    alignment = Pos.CENTER_RIGHT
                    button("Generate", fontAwesome[GEAR]) {
                        enableWhen { generationEnabled }
                        action {
                            selectTargetFile()?.let {
                                generate(it)
                            }
                        }
                    }
                }
            }
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
        }
    }

    init {
        checkCrossoverConnection()
    }

    private fun checkCrossoverConnection() {
        if (crossoverLogin?.isNotBlank() == true && crossoverPassword?.isNotBlank() == true) {
            launch(JavaFx) {
                crossoverConnected = crossoverTimesheetService.check(crossoverLogin, crossoverPassword)
            }
        }
    }

    private fun generate(outputFile: File) {
        invoiceGenerationIcon.animate {
            val money = Money(selectedOperation.amount, Currency.getInstance(selectedOperation.currency))
            invoiceService.generate(templatePath!!, invoiceNumber, selectedWeek!!, money,
                    outputFile.resolve("invoice-$selectedWeek.pdf"))
        }

        timesheetGenerationIcon.animate {
            crossoverTimesheetService.generate(crossoverLogin, crossoverPassword,
                    selectedWeek!!, outputFile.resolve("timesheet-$selectedWeek.png"))
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

    init {
        runLater {
            connect()
        }
    }

    private fun connect() = launch(JavaFx) {
        bankToken?.let { token ->
            processNode?.withRotation {
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
            processNode?.withRotation {
                try {
                    operations = modulebankService.findOperations(token, account).observable()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // oops
                }
            }
        }
    }

    private inline fun <R> Node.withRotation(block: () -> R): R {
        isDisable = true
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
            isDisable = false
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


