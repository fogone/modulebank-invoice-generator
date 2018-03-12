package ru.nobirds.invoice.service

import io.github.bonigarcia.wdm.WebDriverManager
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.FluentWait
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class CrossoverTimesheetService(val httpSupport: HttpSupport) {

    companion object {
        private const val TAB = '\t'

        private val DEFAULT_CHROME_OPTIONS = ChromeOptions().apply {
            addArguments("--window-size=1920,1080", "--headless", "--disable-gpu", "--no-sandbox")
        }
    }

    private val email = By.name("email")

    init {
        WebDriverManager.chromedriver().setup()
    }

    suspend fun check(username: String, password: String): Boolean {
        return try {
            httpSupport.post<Unit>(HttpUrl.parse("https://api.crossover.com/api/identity/authentication")!!) {
                withBasic(username, password)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun generate(username: String, password: String, weekDate: LocalDate, output: File) {
        val driver = ChromeDriver(DEFAULT_CHROME_OPTIONS)

        try {
            val fluentWait = FluentWait(driver)
                    .withTimeout(300, TimeUnit.SECONDS)
                    .pollingEvery(200, TimeUnit.MILLISECONDS)
                    .ignoring(NoSuchElementException::class.java)

            driver.get("https://app.crossover.com/x/dashboard/contractor/my-dashboard?date=$weekDate")

            // wait for login form
            fluentWait.until(ExpectedConditions.elementToBeClickable(email))

            // fill credentials and login
            driver.findElement(email).sendKeys("$username$TAB$password$TAB${Keys.RETURN}")

            // wait for dashboard
            fluentWait.until(this::dashboardIsLoaded)

            // take screenshot
            val screenshot = (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE)

            Files.copy(screenshot.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            driver.close()
        }
    }

    private fun dashboardIsLoaded(driver: WebDriver): Boolean {
        return driver.isTextVisible("Spotlight on your top activities")
                && (driver.isTextVisible("in the week") || driver.isTextVisible("so far this week"))
    }
}

private fun WebDriver.isTextVisible(textToFind: String): Boolean {
    return try {
        findElement(By.xpath("//*[contains(text(), '$textToFind')]")).isDisplayed
    } catch (e: NoSuchElementException) {
        false
    }
}
