package com.renomad.inmra.uitests;

import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.MyThread;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.Select;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.renomad.inmra.uitests.SimpleClient.post;
import static com.renomad.minum.testing.TestFramework.*;
import static org.openqa.selenium.Keys.DOWN;
import static org.openqa.selenium.Keys.ENTER;

public class SmokeTest {

  private WebDriver driver;

  @Before
  public void setUp() {
    WebDriverManager.chromedriver().setup();
    driver = new ChromeDriver();
  }

  @After
  public void tearDown() {
    driver.quit();
  }

  @Test
  public void smoketest() {
    testSearches();
    login();
    registerNewUser();
    login();
    resetPassword();
    deleteMichelle();
    modifyEllisPhotos();
    modifyMarjoriePhotos();

    createNewGuy();
    checkAdministrationPage();
    driver.findElement(By.xpath("//button[contains(.,'Logout')]")).click();
    driver.findElement(By.linkText("Index")).click();
    login();
    playWithDates();
  }

  @Test
  public void smoketest2() {
    openApplication();
    testDetailsAndLinks();
    login();
    TestFramework.assertTrue(driver.findElement(By.cssSelector("nav")).getText().contains(
                    "Create New Person\n" +
                    "List all Persons\n" +
                    "All Photos\n" +
                    "Administration page\n" +
                    "Register user\n" +
                    "Reset password\n" +
                    "Logout"));
    TestFramework.assertEquals(driver.findElement(By.linkText("Create New Person")).getText(), "Create New Person");
    TestFramework.assertEquals(driver.findElement(By.linkText("List all Persons")).getText(), "List all Persons");
    TestFramework.assertEquals(driver.findElement(By.xpath("//button[contains(.,'Logout')]")).getText(), "Logout");
    // click on "Persons"
    driver.findElement(By.linkText("List all Persons")).click();


    // choose to sort by birth date ascending
    driver.findElement(By.id("sort-select")).click();
    {
      WebElement dropdown = driver.findElement(By.id("sort-select"));
      dropdown.findElement(By.xpath("//option[. = 'birth date ascending']")).click();
    }

    // confirm the first person is Ron
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).get(0).getText(), "Ron");
    driver.findElement(By.id("sort-select")).click();
    {
      WebElement dropdown = driver.findElement(By.id("sort-select"));
      dropdown.findElement(By.xpath("//option[. = 'birth date descending']")).click();
    }

    // confirm the first person is Margie
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).get(0).getText(), "Margie Sylvia Evensky Goodman");

    // clear the sorting
    driver.findElement(By.id("clear-sort-submit")).click();

    // confirm the first person is now Ellis
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).get(0).getText(), "Ellis Katz");
    driver.findElement(By.id("search_field")).click();
    driver.findElement(By.id("search_field")).sendKeys("ellis");
    driver.findElement(By.id("search-submit")).click();

    // confirm we found Ellis when we searched
    assertEquals(driver.findElement(By.cssSelector(".name")).getText(), "Ellis Katz");

    // search for marj and assert we don't see Ellis
    driver.findElement(By.id("search_field")).sendKeys("marj");
    driver.findElement(By.id("search-submit")).click();
    assertFalse(driver.getPageSource().contains("Ellis"));

    // clear the sorting
    driver.findElement(By.id("clear-sort-submit")).click();
    // clear the search
    driver.findElement(By.id("clear-search-submit")).click();

    // confirm we see Ron, Susan, Dan...
    List<WebElement> elements = driver.findElements(By.cssSelector(".name-and-lifespan > .name"));
    assertTrue(elements.stream().anyMatch(x -> x.getText().equals("Ron")));
    assertTrue(elements.stream().anyMatch(x -> x.getText().equals("Susan")));
    assertTrue(elements.stream().anyMatch(x -> x.getText().equals("Dan")));

    // Run the command to search by a person's id: Marjorie's:
    driver.get("http://localhost:8080/editpersons?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d");

    // confirm we get what's expected - just Marjorie:
    List<WebElement> marjorieElements = driver.findElements(By.cssSelector(".name-and-lifespan > .name"));
    assertTrue(marjorieElements.size() == 1);
    assertEquals(marjorieElements.get(0).getText(), "Marjorie Katz");

    // reset the page by resetting the search
    driver.findElement(By.id("clear-search-submit")).click();

    // Click the edit button for Ellis
    driver.findElement(By.cssSelector("#\\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div:nth-child(1) > a:nth-child(2)")).click();
    driver.findElement(By.linkText("Cancel")).click();

    // check that we see nav items we expect
    assertTrue(driver.findElement(By.cssSelector("nav")).getText().contains("ⓘ\n" +
            "Create New Person\n" +
            "List all Persons\n" +
            "Edit Person\n" +
            "Edit Photos\n" +
            "In list\n" +
            "All Photos\n" +
            "Administration page\n" +
            "Register user\n" +
            "Reset password\n" +
            "Logout"));

    // navigate around a bit
    driver.findElement(By.id("logo")).click();
    driver.get("http://localhost:8080/person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f"); // Ellis
    driver.findElement(By.linkText("Ron")).click();
    driver.findElement(By.linkText("Dan")).click();
    driver.findElement(By.linkText("Paul")).click();
    driver.findElement(By.linkText("Tina")).click();
    driver.get("http://localhost:8080/index?search=katz");
    assertTrue(driver.findElement(By.id("ab1e7835-e6df-49ac-8492-9cf8b1686d7d_details")).isDisplayed());

    logout();
  }

  /**
   * There is some interesting functionality for the dates
   * - the date can be empty, unknown, or year-only
   * - if the user checks the box for unknown, the input and year-only checkbox should become disabled
   * - if the user checks the box for year-only, the year-value of the date input should copy over to the number input
   * - conversely, if the user unchecks year-only, the date input should have a year set
   * - if the user sets a value as unknown, it should display as "unknown". If empty, it should display as empty string
   * - if the date is set as just a year, it should simply show as that year: e.g. 1984
   */
  private void playWithDates() {
    driver.get("http://localhost:8080/editpersons?search=john");
    driver.findElement(By.linkText("view")).click();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "January 4, 1987 to January 4, 2097 (110 years)");
    driver.navigate().back();
    driver.findElement(By.linkText("edit")).click();
    driver.findElement(By.id("death_date_unknown_checkbox")).click();
    driver.findElement(By.id("born_date_year_only_checkbox")).click();
    driver.findElement(By.id("form_submit_button")).click();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "1987 to Unknown");
    driver.navigate().back();
    driver.findElement(By.id("born_date_unknown_checkbox")).click();
    driver.findElement(By.id("form_submit_button")).click();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "Unknown to Unknown");
    driver.navigate().back();
    driver.findElement(By.id("death_date_unknown_checkbox")).click();
    {
      WebElement element = driver.findElement(By.id("born_date_year_only_checkbox"));
      boolean isEditable = element.isEnabled() && element.getAttribute("readonly") == null;
      assertFalse(isEditable);
    }
    {
      WebElement element = driver.findElement(By.id("born_input"));
      boolean isEditable = element.isEnabled() && element.getAttribute("readonly") == null;
      assertFalse(isEditable);
    }
    driver.findElement(By.id("died_input")).clear();
    driver.findElement(By.id("form_submit_button")).click();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "Unknown to");
  }

  private void checkAdministrationPage() {
    // go to admin page
    driver.findElement(By.linkText("Administration page")).click();
    {
      List<WebElement> elements = driver.findElements(By.xpath("//h2[contains(.,'Log settings')]"));
      assert(!elements.isEmpty());
    }
    {
      List<WebElement> elements = driver.findElements(By.xpath("//h1[contains(.,'Administration')]"));
      assert(!elements.isEmpty());
    }
  }

  private void createNewGuy() {
    // go to create page
    driver.findElement(By.linkText("Create New Person")).click();
    // provide a person's name
    driver.findElement(By.id("name_input")).sendKeys("john doe");
    // enter their birthdate
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"born_input\").value = \"1987-01-04\"");
    // enter their deathdate
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"died_input\").value = \"2087-01-04\"");
    // enter a gender
    driver.findElement(By.xpath("//label[contains(.,'male')]")).click();

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a sibling using the search capability.
    driver.findElement(By.id("add_sibling")).clear();
    driver.findElement(By.id("add_sibling")).sendKeys("Ellis");
    MyThread.sleep(50);
    driver.findElement(By.id("add_sibling")).sendKeys(Keys.DOWN);
    driver.findElement(By.id("add_sibling")).sendKeys(ENTER);
    String siblingsInput = driver.findElement(By.id("add_sibling")).getAttribute("value");
    assertTrue(siblingsInput.contains("Ellis Katz"));

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a spouse using the search capability.
    driver.findElement(By.id("add_spouse")).clear();
    driver.findElement(By.id("add_spouse")).sendKeys("Ellis");
    MyThread.sleep(50);
    driver.findElement(By.id("add_spouse")).sendKeys(Keys.DOWN);
    driver.findElement(By.id("add_spouse")).sendKeys(ENTER);
    String spousesInput = driver.findElement(By.id("add_spouse")).getAttribute("value");
    assertTrue(spousesInput.contains("Ellis Katz"));

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a parent using the search capability.
    driver.findElement(By.id("add_parent")).clear();
    driver.findElement(By.id("add_parent")).sendKeys("Ellis");
    MyThread.sleep(50);
    driver.findElement(By.id("add_parent")).sendKeys(Keys.DOWN);
    driver.findElement(By.id("add_parent")).sendKeys(ENTER);
    String parentsInput = driver.findElement(By.id("add_parent")).getAttribute("value");
    assertTrue(parentsInput.contains("Ellis Katz"));

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a child using the search capability.
    driver.findElement(By.id("add_child")).sendKeys(Keys.END);
    driver.findElement(By.id("add_child")).sendKeys("Ellis");
    MyThread.sleep(50);
    driver.findElement(By.id("add_child")).sendKeys(Keys.DOWN);
    driver.findElement(By.id("add_child")).sendKeys(ENTER);
    String childrenInput = driver.findElement(By.id("add_child")).getAttribute("value");
    assertTrue(childrenInput.contains("Ellis Katz"));

    // add a bio
    driver.findElement(By.id("biography_input")).sendKeys("<p>\\nIt was the best of times, it was the worst of times.\\n</p>");
    // add notes
    driver.findElement(By.id("notes_input")).sendKeys("Here are some secret notes about John.  Don't let anyone see this.");

    // Interesting.  I needed to switch to a JavaScript click instead of a normal
    // Selenium click.  Not sure why this was necessary.  The button in question
    // *is* connected to Javascript code - maybe that is a reason.
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"add_extra_field_button\").click()");

    driver.findElement(By.id("extra_data_key_1")).click();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_1")));
      dropdown.selectByValue("Wedding date");
    }
    driver.findElement(By.id("extra_data_value_1")).click();
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"extra_data_value_1\").value = \"1999-01-04\"");


    // add extra - graduation
    driver.findElement(By.id("add_extra_field_button")).click();

    driver.findElement(By.id("extra_data_key_2")).click();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_2")));
      dropdown.selectByValue("Graduation date");
    }
    driver.findElement(By.id("extra_data_value_2")).click();
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"extra_data_value_2\").value = \"1999-01-04\"");


    // add extra - birthplace
    driver.findElement(By.id("add_extra_field_button")).click();

    driver.findElement(By.id("extra_data_key_3")).click();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_3")));
      dropdown.selectByValue("Birthplace");
    }
    driver.findElement(By.id("extra_data_value_3")).click();
    driver.findElement(By.id("extra_data_value_3")).sendKeys("Anytown, USA");


    // add extra - deathplace
    driver.findElement(By.id("add_extra_field_button")).click();

    driver.findElement(By.id("extra_data_key_4")).click();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_4")));
      dropdown.selectByValue("Deathplace");
    }
    driver.findElement(By.id("extra_data_value_4")).click();
    driver.findElement(By.id("extra_data_value_4")).sendKeys("St. Louis, Missouri");

    // add extra - graduation date
    driver.findElement(By.id("add_extra_field_button")).click();

    driver.findElement(By.id("extra_data_key_5")).click();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_5")));
      dropdown.selectByValue("Graduation date");
    }
    // make it year-only
    driver.findElement(By.id("year_only_for_extra_value_5")).click();
    driver.findElement(By.id("extra_data_value_5")).click();
    driver.findElement(By.id("extra_data_value_5")).sendKeys("1988");

    // delete an extra field
    driver.findElement(By.cssSelector("#extra_data_item_3 > button.extra_data_item_delete")).click();

    // enter the data
    driver.findElement(By.id("form_submit_button")).click();


    // open editing for John Doe's details
    driver.findElement(By.linkText("List all Persons")).click();
    // click the edit button on John Doe - the 13th row, 6th column button "edit"
    driver.findElement(By.cssSelector("#search_field")).sendKeys("john doe");
    driver.findElement(By.cssSelector("#search-submit")).click();
    driver.findElement(By.cssSelector("div.other_components > div:nth-child(1) > a:nth-child(2)")).click();
    // delete an extra item
    driver.findElement(By.id("extra_data_item_2")).click();
    // update death year
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"died_input\").value = \"2097-01-04\"");
    // update notes
    driver.findElement(By.id("notes_input")).sendKeys("Here are some secret notes about John. ");
    driver.findElement(By.id("spouses_input")).sendKeys(
            "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a>");
    // enter data
    driver.findElement(By.id("form_submit_button")).click();
    assertEquals(driver.findElement(By.cssSelector(".spouses")).getText(), "Ellis Katz");
  }

  private void modifyEllisPhotos() {
    // click on ellis's photos #\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div.photos > a
    driver.findElement(By.cssSelector("#\\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div.photos > a")).click();
    // add a description for one of ellis's photos
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).click();
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).sendKeys("Ellis was always smoking at home, during dinner");
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description_save_button")).click();

    // modify a caption for a photo
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).clear();
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).sendKeys("Ellis at dinner smoking, as was his wont.");
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description_save_button")).click();
    // give a second to finish saving the data
    MyThread.sleep(100);
    driver.navigate().refresh();

    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).getText(), "Ellis at dinner smoking, as was his wont.");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).getText(), "Ellis was always smoking at home, during dinner");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(3) .short_description")).getText(), "Ellis lounging by the pool");

    // prepare to take action by POST instead of ordinary (JavaScript) approach
    Cookie cookie = driver.manage().getCookies().stream().filter(x -> x.getName().equals("sessionid")).findFirst().orElseThrow();
    String photoId = driver.findElement(By.cssSelector("tbody > tr:nth-of-type(1)")).getAttribute("data-photoid");

    // modify the description and caption by POST request instead - as though JavaScript was disabled.
    post("http://localhost:8080/photolongdescupdate", "long_description=foo&photoid="+photoId, List.of("Cookie", cookie.toString()));
    post("http://localhost:8080/photocaptionupdate", "caption=foo&photoid="+photoId, List.of("Cookie", cookie.toString()));

    driver.navigate().refresh();

    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-of-type(1) .short_description")).getText(), "foo");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).getText(), "foo");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(3) .short_description")).getText(), "Ellis lounging by the pool");

    // delete a photo, confirm it is gone
    driver.findElement(By.cssSelector("tbody > tr:nth-child(1) > td:nth-child(7) > details")).click();
    // give the details control a bit of time to open
    MyThread.sleep(200);
    driver.findElement(By.cssSelector("tbody > tr:nth-child(1) > td:nth-child(7) > details .delete_button")).click();
    MyThread.sleep(50);
    {
      List<WebElement> elements = driver.findElements(By.cssSelector("tr:nth-child(3) .short_description"));
      assert(elements.isEmpty());
    }

    // delete a photo, using a POST
    String photoIdForDeletion = driver.findElement(By.cssSelector("tbody > tr:nth-of-type(1)")).getAttribute("data-photoid");
    post("http://localhost:8080/deletephoto", "photoid="+photoIdForDeletion, List.of("Cookie", cookie.toString()));
    driver.navigate().refresh();
    List<WebElement> elements = driver.findElements(By.cssSelector("tr:nth-child(2) .short_description"));
    assert(elements.isEmpty());
  }

  private void modifyMarjoriePhotos() {
    driver.findElement(By.linkText("List all Persons")).click();
    // search for Marjorie
    driver.findElement(By.id("search_field")).sendKeys("marjorie");
    driver.findElement(By.id("search-submit")).click();

    // go to Marjorie's photos
    driver.findElement(By.cssSelector(".photos a")).click();

    // click to copy first photo to someone else - does not really matter which photo we pick
    driver.findElement(By.cssSelector("button.copy_to_other_person_button")).click();

    // search for Ellis, we'll copy the photo to him
    driver.findElement(By.id("person_selection_input")).sendKeys("Ellis");
    // wait a bit for the browser to get Ellis's info
    MyThread.sleep(50);
    driver.findElement(By.id("person_selection_input")).sendKeys(DOWN);
    driver.findElement(By.id("person_selection_input")).sendKeys(ENTER);
    driver.findElement(By.id("short_description")).sendKeys("This is a caption for a photo I am copying from Marjorie to Ellis");
    driver.findElement(By.id("copy_photo_button")).click();

    // now we should find ourselves on Ellis's photo page, with this new photo
    assertEquals(driver.findElement(By.id("view-person-detail-link")).getText(), "Ellis Katz");
    assertTrue(driver.getPageSource().contains("This is a caption for a photo I am copying"));
  }

  private void deleteMichelle() {
    // view the List all Persons page
    driver.findElement(By.linkText("List all Persons")).click();
    // make sure we see michelle
    {
      List<WebElement> elements = driver.findElements(By.xpath("//span[contains(.,'Michelle')]"));
      assert(!elements.isEmpty());
    }
    // open the delete button
    driver.findElement(By.cssSelector("#a5e8e11c-26d2-484f-954d-8cc001330f10_details .delete > details")).click();
    // give the control a bit of time to open.
    MyThread.sleep(200);
    // click the delete button
    driver.findElement(By.cssSelector("#a5e8e11c-26d2-484f-954d-8cc001330f10_details button.delete_button")).click();
    // wait a bit
    MyThread.sleep(500);
    // confirm we don't see michelle
    {
      List<WebElement> elements = driver.findElements(By.xpath("//span[contains(.,'Michelle')]"));
      assert(elements.isEmpty());
    }
  }

  private void testSearches() {
    // open the homepage
    driver.get("http://localhost:8080/");
    // set a decent window size
    driver.manage().window().setSize(new Dimension(1200, 800));
    TestFramework.assertEquals(driver.findElement(By.cssSelector("label")).getText(), "Search by name");
    // click in the search box
    driver.findElement(By.id("search_by_name")).click();
    // enter a search term
    driver.findElement(By.id("search_by_name")).sendKeys("lou");
    MyThread.sleep(80);
    driver.findElement(By.xpath("//span[contains(.,'Louis Harold Goodman')]")).click();
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-era")).getText(), "January 30, 1919 to July 7, 2007 (88 years)");
    // go back to the homepage
    driver.findElement(By.cssSelector("#logo > img")).click();
    // search for nobody
    driver.findElement(By.id("search_by_name")).sendKeys("nobody");
    // press enter
    driver.findElement(By.id("search_by_name")).sendKeys(ENTER);
    // make sure it shows our search
    TestFramework.assertEquals(driver.findElement(By.id("search_query")).getText(), "You searched for: nobody");
    // no one should be found
    TestFramework.assertEquals(driver.findElement(By.cssSelector("#random_persons_container > ul > li")).getText(), "No persons found");
  }

  private void testDetailsAndLinks() {
    // search for the first letters in Marjorie
    driver.findElement(By.id("search_by_name")).sendKeys("mar");
    MyThread.sleep(20);
    // Click Marjorie's name
    driver.findElement(By.xpath("//span[contains(.,'Marjorie Katz')]")).click();
    // Make sure we're on her detail page
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-name")).getText(), "Marjorie Katz");
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-era")).getText(), "February 3, 1925 to July 13, 2020 (95 years)");
    // Check that we see her brother listed
    TestFramework.assertEquals(driver.findElement(By.cssSelector(".siblings")).getText(), "Herbert Blumberg");
    // Go to her husband's page
    driver.findElement(By.linkText("Ellis Katz")).click();
    // check that Ellis's details are as expected
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-name")).getText(), "Ellis Katz");
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-era")).getText(), "November 21, 1921 to March 12, 2020 (98 years)");
  }

  private void openApplication() {
    // Open the home page
    driver.get("http://localhost:8080/");
    driver.manage().window().setSize(new Dimension(1200, 980));
  }

  private void logout() {
    driver.findElement(By.xpath("//button[contains(.,'Logout')]")).click();
    TestFramework.assertEquals(driver.findElement(By.xpath("//p")).getText(), "You've been logged out");
  }

  private void login() {
    // open the login page
    driver.get("http://localhost:8080/login");
    // enter username
    driver.findElement(By.id("username")).click();
    driver.findElement(By.id("username")).sendKeys("admin");
    // enter password
    driver.findElement(By.id("password")).click();
    String adminPassword;
    try {
      adminPassword = Files.readString(Path.of("../admin_password"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    driver.findElement(By.id("password")).sendKeys(adminPassword);
    // login
    driver.findElement(By.id("login_button")).click();
  }

  private void resetPassword() {
    // open the reset page
    driver.get("http://localhost:8080/resetpassword");
    // get the new suggested password
    String newPassword = driver.findElement(By.id("new_password")).getText();
    // reset the password
    driver.findElement(By.id("change_password")).click();

    try {
      Files.writeString(Path.of("../admin_password"), newPassword);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    driver.findElement(By.linkText("Home")).click();
  }

  private void registerNewUser() {
    // open the register page
    driver.get("http://localhost:8080/register");
    // set a new username and password
    String newUsername = "authperson";
    String newPassword = "abcdef123567yay";
    driver.findElement(By.id("username")).sendKeys(newUsername);
    driver.findElement(By.id("password")).sendKeys(newPassword);
    // enter the new data
    driver.findElement(By.id("register_user_button")).click();

    driver.findElement(By.linkText("Home")).click();

    logout();

    // open the login page
    driver.get("http://localhost:8080/login");
    // enter username
    driver.findElement(By.id("username")).sendKeys(newUsername);
    // enter password
    driver.findElement(By.id("password")).sendKeys(newPassword);
    // login
    driver.findElement(By.id("login_button")).click();

    logout();
  }
}
