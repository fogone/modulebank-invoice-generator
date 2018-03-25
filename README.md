# Configuration

- Go to Module bank and generate token with account info and operations access
- Use generated token in generator ui
- Prepare docx or odt document with invoice template using variables like ${someName} for replacement
- Available variables:
    - accountNumber
    - invoiceNumber
    - documentDate
    - sum
    - longSum
    - fromDate
    - toDate
    - currency
    - contragentName 
    - contragentBankName 
    - contragentBankAccountNumber 

# Build

[Build](https://travis-ci.org/fogone/modulebank-invoice-generator.svg?branch=master)