/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.selenium;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public final class SeleniumCardsUtils
{
    public enum Browser
    {
        CHROME,
        EDGE,
        FIREFOX,
        SAFARI
    }

    private static Browser defaultBrowser = Browser.CHROME;

    // Hide the constructor
    private SeleniumCardsUtils()
    {
    }

    public static WebDriver getDriver()
    {
        String driverProp = System.getProperty("seleniumDriver");

        final WebDriver driver;

        if (driverProp == null) {
            driver = getDriver(defaultBrowser);
        } else {
            switch (driverProp.toLowerCase()) {
                case "safari":
                    driver = getDriver(Browser.SAFARI);
                    break;
                case "edge":
                    driver = getDriver(Browser.EDGE);
                    break;
                case "firefox":
                    driver = getDriver(Browser.FIREFOX);
                    break;
                case "chrome":
                    driver = getDriver(Browser.CHROME);
                    break;
                default:
                    driver = null;
                    break;
            }
        }

        return driver;
    }

    public static WebDriver getDriver(Browser browser)
    {
        WebDriver driver = null;
        switch (browser) {
            case SAFARI:
                WebDriverManager.safaridriver().setup();
                driver = new SafariDriver();
                break;
            case FIREFOX:
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                firefoxOptions.addArguments("-headless");
                driver = new FirefoxDriver(firefoxOptions);
                break;
            case EDGE:
                // TODO: Edge is instead launching Google Chrome and faling to correct as a result.
                WebDriverManager.edgedriver().setup();
                EdgeOptions edgeOptions = new EdgeOptions();
                edgeOptions.addArguments("start-maximized", "enable-automation");
                driver = new EdgeDriver(edgeOptions);
                break;
            case CHROME:
            default:
                WebDriverManager.chromedriver().setup();
                ChromeOptions chromeOptions = new ChromeOptions();
                chromeOptions.addArguments("start-maximized", "enable-automation");
                driver = new ChromeDriver(chromeOptions);
                break;
        }

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        return driver;
    }

    public static void login(WebDriver driver)
    {
        login(driver, "admin", "admin");
    }

    public static void login(WebDriver driver, String username, String password)
    {
        driver.get("http://localhost:8080");

        driver.findElement(By.name("j_username")).sendKeys(username);
        driver.findElement(By.name("j_password")).sendKeys(password);
        driver.findElement(By.name("j_password")).submit();

        // TODO: Firefox is not waiting for the dashboard to load before checking for
        // the avatar so is failing every time. This can be resolved by a sleep() but should be
        // fixed properly
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("adminnavbaravatar")));
    }
}
