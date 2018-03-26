# Configuration

- Go to Module bank and generate token with account info and operations access
- Use generated token in generator ui
- Prepare docx or odt document with invoice template using variables like ${someName} for replacement
- Available variables:

|Variable|Description|
|--------|-----------|
|`accountNumber`|Account number in module bank|
|`invoiceNumber`|Ordered number of invoice document| 
|`documentDate`|Invoice date|
|`sum`|Invoice sum|
|`longSum`|Invoice sum in words|
|`fromDate`|Start working date|
|`toDate`|ENd working date|
|`currency`|Currency|
|`contragentName`|Contragent name| 
|`contragentBankName`|Contragent bank name| 
|`contragentBankAccountNumber`|Contragent bank account number| 

# Build

![Build](https://travis-ci.org/fogone/modulebank-invoice-generator.svg?branch=master)