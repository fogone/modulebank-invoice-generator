package ru.nobirds.invoice.view

import javafx.beans.property.*
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.stage.FileChooser
import javafx.util.Duration
import javafx.util.converter.NumberStringConverter
import kotlinx.coroutines.experimental.javafx.JavaFx
import kotlinx.coroutines.experimental.launch
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
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

    private val templatePathProperty = SimpleObjectProperty<File?>().persistent(config, "templatePath", null)
    private var templatePath: File? by templatePathProperty

    private val invoiceNumberProperty = SimpleIntegerProperty().persistent(config, "invoiceNumber", 1)
    private var invoiceNumber: Int by invoiceNumberProperty

    private val selectedWeekProperty = SimpleObjectProperty<LocalDate>().persistent(config, "selectedWeek", null)
    private var selectedWeek: LocalDate? by selectedWeekProperty

    private val sumProperty = SimpleLongProperty().persistent(config, "sum", 2000L)
    private val sum: Long by sumProperty

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
                        enableWhen { templatePathProperty.isNotNull and selectedWeekProperty.isNotNull and selectedOperationProperty.isNotNull }
                        action {
                            selectTargetFile()?.let {
                                generate(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun generate(outputFile: File) {
        launch {
            val money = Money(selectedOperation.amount, Currency.getInstance(selectedOperation.currency))
            invoiceService.generate(templatePath!!, invoiceNumber, selectedWeek!!, money, outputFile)
        }
        invoiceNumber++
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

    private inline fun Node.withRotation(block: () -> Unit) {
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
            block()
        } finally {
            timeline.stop()
            isDisable = false
        }
    }

    private fun selectTargetFile(): File? {
        return selectFile("Select target", emptyArray(), FileChooserMode.Save)
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


