# Module bank + Crossover invoice generator
This javafx application allow generate invoice documents from xml-based 
formats like `docx` or `odt` by replacing templates variables. Also application
grabs crossover timesheet screen to image. 

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

![template-example](https://user-images.githubusercontent.com/998781/37885444-70ba7d1c-30df-11e8-8b47-62c420287849.png)

- Select template in `Invoice` block
- Select output directory, files will be grouped by working week start date
- Select account and transaction in module bank panel. All other fields will be filled automatically
- You can change all of therm if needed
- Click `Generate` and wait while gears rotating

# Build

![Build](https://travis-ci.org/fogone/modulebank-invoice-generator.svg?branch=master)