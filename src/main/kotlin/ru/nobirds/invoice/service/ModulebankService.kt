package ru.nobirds.invoice.service

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class ModulebankCompany(val companyId: String, val companyName: String, val bankAccounts: List<ModulebankAccount>)
data class ModulebankAccount(val id: String, val accountName: String, val number: BigInteger,
                             val currency: String, val balance: BigDecimal, val category: ModulebankAccountCategory)

data class ModulebankOperation(val id: String, val companyId: String, val status: ModulebankOperationStatus,
                               val category: ModulebankOperationsCategory, val currency: String, val amount: BigDecimal,
                               val executed: Date, val contragentName: String, val paymentPurpose: String)

enum class ModulebankOperationStatus {
    Received
}

enum class ModulebankAccountCategory {
    CheckingAccount, TransitAccount
}

data class ModulebankOperationsRequest(val category: ModulebankOperationsCategory? = null,
                                       val records: Int? = null, val skip: Int? = null,
                                       val from: LocalDate? = null, val till: LocalDate? = null)

enum class ModulebankOperationsCategory {
    Debet, Credit
}

class ModulebankService(private val httpSupport: ModulebankHttpSupport) {

    suspend fun findAccounts(token: String): List<ModulebankAccount> {
        val url = httpSupport.url().addPathSegment("account-info").build()
        val companies: List<ModulebankCompany> = httpSupport.post(url, token)

        return companies.flatMap { it.bankAccounts }
    }

    suspend fun findOperations(token: String, account: ModulebankAccount): List<ModulebankOperation> {
        val url = httpSupport.url().addPathSegment("operation-history").addPathSegment(account.id).build()

        val operations: List<ModulebankOperation> = httpSupport.post(url, token,
                ModulebankOperationsRequest(category = ModulebankOperationsCategory.Debet, records = 50)
        )

        return operations
    }
}
