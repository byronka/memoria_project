package com.renomad.inmra.uitests;

import com.renomad.minum.utils.MyThread;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.renomad.inmra.uitests.Utilities.waitForUi;
import static com.renomad.minum.testing.TestFramework.assertFalse;
import static com.renomad.minum.testing.TestFramework.assertTrue;

/**
 * Tests for the navigation menu that appears in the top left corner
 * when logged in as an administrator - it opens by clicking the burger
 * button, and closes by clicking the button again, by clicking anywhere
 * outside of it, or by pressing the escape key.
 */
public class MenuTest {

  private WebDriver driver;

  @Test
  public void testMenu() throws IOException {
    driver = new ChromeDriver();
    driver.manage().window().setSize(new Dimension(1200, 1100));
    login();
    driver.get("http://localhost:8080/");
    MyThread.sleep(1000);

    // the menu opens when clicking the burger button
    toggleMenu();
    assertTrue(isMenuOpen(), "the menu should open when the burger button is clicked");

    // pressing escape closes it
    new Actions(driver).sendKeys(Keys.ESCAPE).perform(); waitForUi();
    MyThread.sleep(200);
    assertFalse(isMenuOpen(), "the menu should close when escape is pressed");

    // clicking on the page content outside of the menu closes it, even where
    // the content is painted on top of the overlay
    toggleMenu();
    assertTrue(isMenuOpen(), "the menu should open when the burger button is clicked");
    clickAtPageCoordinates(900, 400);
    assertFalse(isMenuOpen(), "the menu should close when clicking outside of it");

    // clicking inside the menu leaves it open
    toggleMenu();
    assertTrue(isMenuOpen(), "the menu should open when the burger button is clicked");
    clickAtPageCoordinates(350, 260);
    assertTrue(isMenuOpen(), "the menu should stay open when clicking inside of it");

    // clicking the burger button again closes it
    toggleMenu();
    assertFalse(isMenuOpen(), "the menu should close when the burger button is clicked again");

    // the links in the menu still work
    toggleMenu();
    driver.findElement(By.linkText("Administration page")).click(); waitForUi();
    assertTrue(driver.getCurrentUrl().contains("/admin"), "the menu links should still navigate");

    driver.quit();
  }

  private boolean isMenuOpen() {
    WebElement menu = driver.findElement(By.cssSelector(".responsive-menu"));
    return menu.getDomAttribute("class").contains("expand");
  }

  private void toggleMenu() {
    driver.findElement(By.id("menuToggle")).click(); waitForUi();
    MyThread.sleep(200);
  }

  /**
   * Clicks at a location on the page, rather than on a particular element,
   * so that we exercise whatever happens to be painted on top there.
   */
  private void clickAtPageCoordinates(int x, int y) {
    WebElement body = driver.findElement(By.tagName("body"));
    new Actions(driver)
            .moveToElement(body, x - body.getSize().getWidth() / 2, y - body.getSize().getHeight() / 2)
            .click()
            .perform();
    waitForUi();
    MyThread.sleep(200);
  }

  private void login() throws IOException {
    driver.get("http://localhost:8080/login");
    driver.findElement(By.id("username")).sendKeys("admin"); waitForUi();
    String adminPassword = Files.readString(Path.of("../admin_password"));
    driver.findElement(By.id("password")).sendKeys(adminPassword); waitForUi();
    driver.findElement(By.id("login_button")).click(); waitForUi();
  }
}
