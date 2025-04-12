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
import static com.renomad.inmra.uitests.Utilities.waitForUi;
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
    driver.findElement(By.xpath("//button[contains(.,'Logout')]")).click(); waitForUi();
    driver.findElement(By.linkText("Index")).click(); waitForUi();
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
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();


    // choose to sort by birth date ascending
    driver.findElement(By.id("sort-select")).click(); waitForUi();
    {
      WebElement dropdown = driver.findElement(By.id("sort-select"));
      dropdown.findElement(By.xpath("//option[. = 'birth date ascending']")).click(); waitForUi();
    }

    // confirm the first person is Ron
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).get(0).getText(), "Ron");
    driver.findElement(By.id("sort-select")).click(); waitForUi();
    {
      WebElement dropdown = driver.findElement(By.id("sort-select"));
      dropdown.findElement(By.xpath("//option[. = 'birth date descending']")).click(); waitForUi();
    }

    // confirm the first person is Margie
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).get(0).getText(), "Margie Sylvia Evensky Goodman");

    // clear the sorting
    driver.findElement(By.id("clear-sort-submit")).click(); waitForUi();

    // confirm the first person is now Ellis
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).get(0).getText(), "Ellis Katz");
    driver.findElement(By.id("search_field")).click(); waitForUi();
    driver.findElement(By.id("search_field")).sendKeys("ellis"); waitForUi();
    driver.findElement(By.id("search-submit")).click(); waitForUi();

    // confirm we found Ellis when we searched
    assertEquals(driver.findElement(By.cssSelector(".name")).getText(), "Ellis Katz");

    // search for marj and assert we don't see Ellis
    driver.findElement(By.id("search_field")).sendKeys("marj"); waitForUi();
    driver.findElement(By.id("search-submit")).click(); waitForUi();
    assertFalse(driver.getPageSource().contains("Ellis"));

    // clear the sorting
    driver.findElement(By.id("clear-sort-submit")).click(); waitForUi();
    // clear the search
    driver.findElement(By.id("clear-search-submit")).click(); waitForUi();

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
    driver.findElement(By.id("clear-search-submit")).click(); waitForUi();

    // Click the edit button for Ellis
    driver.findElement(By.cssSelector("#\\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div:nth-child(1) > a:nth-child(2)")).click(); waitForUi();
    driver.findElement(By.linkText("Cancel")).click(); waitForUi();

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
    driver.findElement(By.id("logo")).click(); waitForUi();
    driver.get("http://localhost:8080/person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f"); // Ellis
    driver.findElement(By.linkText("Ron")).click(); waitForUi();
    driver.findElement(By.linkText("Dan")).click(); waitForUi();
    driver.findElement(By.linkText("Paul")).click(); waitForUi();
    driver.findElement(By.linkText("Tina")).click(); waitForUi();
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
    driver.findElement(By.linkText("view")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "January 4, 1987 to January 4, 2097 (110 years)");
    driver.navigate().back();
    driver.findElement(By.linkText("edit")).click(); waitForUi();
    driver.findElement(By.id("death_date_unknown_checkbox")).click(); waitForUi();
    driver.findElement(By.id("born_date_year_only_checkbox")).click(); waitForUi();
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "1987 to Unknown");
    driver.navigate().back();
    driver.findElement(By.id("born_date_unknown_checkbox")).click(); waitForUi();
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "Unknown to Unknown");
    driver.navigate().back();
    driver.findElement(By.id("death_date_unknown_checkbox")).click(); waitForUi();
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
    driver.findElement(By.id("died_input")).clear(); waitForUi();
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "Unknown to");
  }

  private void checkAdministrationPage() {
    // go to admin page
    driver.findElement(By.linkText("Administration page")).click(); waitForUi();
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
    driver.findElement(By.linkText("Create New Person")).click(); waitForUi();
    // provide a person's name
    driver.findElement(By.id("name_input")).sendKeys("john doe"); waitForUi();
    // enter their birthdate
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"born_input\").value = \"1987-01-04\"");
    // enter their deathdate
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"died_input\").value = \"2087-01-04\"");
    // enter a gender
    driver.findElement(By.xpath("//label[contains(.,'male')]")).click(); waitForUi();

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a sibling using the search capability.
    driver.findElement(By.id("add_sibling")).clear(); waitForUi();
    driver.findElement(By.id("add_sibling")).sendKeys("Ellis"); waitForUi();
    MyThread.sleep(50);
    driver.findElement(By.id("add_sibling")).sendKeys(Keys.DOWN); waitForUi();
    driver.findElement(By.id("add_sibling")).sendKeys(ENTER); waitForUi();
    String siblingsInput = driver.findElement(By.id("add_sibling")).getAttribute("value");
    assertTrue(siblingsInput.contains("Ellis Katz"));

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a spouse using the search capability.
    driver.findElement(By.id("add_spouse")).clear(); waitForUi();
    driver.findElement(By.id("add_spouse")).sendKeys("Ellis"); waitForUi();
    MyThread.sleep(50);
    driver.findElement(By.id("add_spouse")).sendKeys(Keys.DOWN); waitForUi();
    driver.findElement(By.id("add_spouse")).sendKeys(ENTER); waitForUi();
    String spousesInput = driver.findElement(By.id("add_spouse")).getAttribute("value");
    assertTrue(spousesInput.contains("Ellis Katz"));

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a parent using the search capability.
    driver.findElement(By.id("add_parent")).clear(); waitForUi();
    driver.findElement(By.id("add_parent")).sendKeys("Ellis"); waitForUi();
    MyThread.sleep(50);
    driver.findElement(By.id("add_parent")).sendKeys(Keys.DOWN); waitForUi();
    driver.findElement(By.id("add_parent")).sendKeys(ENTER); waitForUi();
    String parentsInput = driver.findElement(By.id("add_parent")).getAttribute("value");
    assertTrue(parentsInput.contains("Ellis Katz"));

    // use the dynamic capability on this page to add Ellis Katz
    // add Ellis as a child using the search capability.
    driver.findElement(By.id("add_child")).sendKeys(Keys.END); waitForUi();
    driver.findElement(By.id("add_child")).sendKeys("Ellis"); waitForUi();
    MyThread.sleep(50);
    driver.findElement(By.id("add_child")).sendKeys(Keys.DOWN); waitForUi();
    driver.findElement(By.id("add_child")).sendKeys(ENTER); waitForUi();
    String childrenInput = driver.findElement(By.id("add_child")).getAttribute("value");
    assertTrue(childrenInput.contains("Ellis Katz"));

    // add a bio
    driver.findElement(By.id("biography_input")).sendKeys("<p>\\nIt was the best of times, it was the worst of times.\\n</p>"); waitForUi();
    // add notes
    driver.findElement(By.id("notes_input")).sendKeys("Here are some secret notes about John.  Don't let anyone see this."); waitForUi();

    // Interesting.  I needed to switch to a JavaScript click instead of a normal
    // Selenium click.  Not sure why this was necessary.  The button in question
    // *is* connected to Javascript code - maybe that is a reason.
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"add_extra_field_button\").click()");

    driver.findElement(By.id("extra_data_key_1")).click(); waitForUi();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_1")));
      dropdown.selectByValue("Wedding date");
    }
    driver.findElement(By.id("extra_data_value_1")).click(); waitForUi();
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"extra_data_value_1\").value = \"1999-01-04\"");


    // add extra - graduation
    driver.findElement(By.id("add_extra_field_button")).click(); waitForUi();

    driver.findElement(By.id("extra_data_key_2")).click(); waitForUi();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_2")));
      dropdown.selectByValue("Graduation date");
    }
    driver.findElement(By.id("extra_data_value_2")).click(); waitForUi();
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"extra_data_value_2\").value = \"1999-01-04\"");


    // add extra - birthplace
    driver.findElement(By.id("add_extra_field_button")).click(); waitForUi();

    driver.findElement(By.id("extra_data_key_3")).click(); waitForUi();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_3")));
      dropdown.selectByValue("Birthplace");
    }
    driver.findElement(By.id("extra_data_value_3")).click(); waitForUi();
    driver.findElement(By.id("extra_data_value_3")).sendKeys("Anytown, USA"); waitForUi();


    // add extra - deathplace
    driver.findElement(By.id("add_extra_field_button")).click(); waitForUi();

    driver.findElement(By.id("extra_data_key_4")).click(); waitForUi();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_4")));
      dropdown.selectByValue("Deathplace");
    }
    driver.findElement(By.id("extra_data_value_4")).click(); waitForUi();
    driver.findElement(By.id("extra_data_value_4")).sendKeys("St. Louis, Missouri"); waitForUi();

    // add extra - graduation date
    driver.findElement(By.id("add_extra_field_button")).click(); waitForUi();

    driver.findElement(By.id("extra_data_key_5")).click(); waitForUi();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_5")));
      dropdown.selectByValue("Graduation date");
    }
    // make it year-only
    driver.findElement(By.id("year_only_for_extra_value_5")).click(); waitForUi();
    driver.findElement(By.id("extra_data_value_5")).click(); waitForUi();
    driver.findElement(By.id("extra_data_value_5")).sendKeys("1988"); waitForUi();

    // delete an extra field
    driver.findElement(By.cssSelector("#extra_data_item_3 > button.extra_data_item_delete")).click(); waitForUi();

    // enter the data
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();


    // open editing for John Doe's details
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();
    // click the edit button on John Doe - the 13th row, 6th column button "edit"
    driver.findElement(By.cssSelector("#search_field")).sendKeys("john doe"); waitForUi();
    driver.findElement(By.cssSelector("#search-submit")).click(); waitForUi();
    driver.findElement(By.cssSelector("div.other_components > div:nth-child(1) > a:nth-child(2)")).click(); waitForUi();
    // delete an extra item
    driver.findElement(By.id("extra_data_item_2")).click(); waitForUi();
    // update death year
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"died_input\").value = \"2097-01-04\"");
    // update notes
    driver.findElement(By.id("notes_input")).sendKeys("Here are some secret notes about John. "); waitForUi();
    driver.findElement(By.id("spouses_input")).sendKeys(
            "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a>");
    // enter data
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".spouses")).getText(), "Ellis Katz");
  }

  private void modifyEllisPhotos() {
    // click on ellis's photos #\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div.photos > a
    driver.findElement(By.cssSelector("#\\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div.photos > a")).click(); waitForUi();
    // add a description for one of ellis's photos
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).click(); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).sendKeys("Ellis was always smoking at home, during dinner"); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description_save_button")).click(); waitForUi();

    // modify a caption for a photo
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).clear(); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).sendKeys("Ellis at dinner smoking, as was his wont."); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description_save_button")).click(); waitForUi();
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
    driver.findElement(By.cssSelector("tbody > tr:nth-child(1) > td:nth-child(7) > details")).click(); waitForUi();
    // give the details control a bit of time to open
    MyThread.sleep(200);
    driver.findElement(By.cssSelector("tbody > tr:nth-child(1) > td:nth-child(7) > details .delete_button")).click(); waitForUi();
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
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();
    // search for Marjorie
    driver.findElement(By.id("search_field")).sendKeys("marjorie"); waitForUi();
    driver.findElement(By.id("search-submit")).click(); waitForUi();

    // go to Marjorie's photos
    driver.findElement(By.cssSelector(".photos a")).click(); waitForUi();

    // click to copy first photo to someone else - does not really matter which photo we pick
    driver.findElement(By.cssSelector("button.copy_to_other_person_button")).click(); waitForUi();

    // search for Ellis, we'll copy the photo to him
    driver.findElement(By.id("person_selection_input")).sendKeys("Ellis"); waitForUi();
    // wait a bit for the browser to get Ellis's info
    MyThread.sleep(50);
    driver.findElement(By.id("person_selection_input")).sendKeys(DOWN); waitForUi();
    driver.findElement(By.id("person_selection_input")).sendKeys(ENTER); waitForUi();
    driver.findElement(By.id("short_description")).sendKeys("This is a caption for a photo I am copying from Marjorie to Ellis"); waitForUi();
    driver.findElement(By.id("copy_photo_button")).click(); waitForUi();

    // now we should find ourselves on Ellis's photo page, with this new photo
    assertEquals(driver.findElement(By.id("view-person-detail-link")).getText(), "Ellis Katz");
    assertTrue(driver.getPageSource().contains("This is a caption for a photo I am copying"));
  }

  private void deleteMichelle() {
    // view the List all Persons page
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();
    // make sure we see michelle
    {
      List<WebElement> elements = driver.findElements(By.xpath("//span[contains(.,'Michelle')]"));
      assert(!elements.isEmpty());
    }
    // open the delete button
    driver.findElement(By.cssSelector("#a5e8e11c-26d2-484f-954d-8cc001330f10_details .delete > details")).click(); waitForUi();
    // give the control a bit of time to open.
    MyThread.sleep(200);
    // click the delete button
    driver.findElement(By.cssSelector("#a5e8e11c-26d2-484f-954d-8cc001330f10_details button.delete_button")).click(); waitForUi();
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
    driver.findElement(By.id("search_by_name")).click(); waitForUi();
    // enter a search term
    driver.findElement(By.id("search_by_name")).sendKeys("lou"); waitForUi();
    MyThread.sleep(80);
    driver.findElement(By.xpath("//span[contains(.,'Louis Harold Goodman')]")).click(); waitForUi();
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-era")).getText(), "January 30, 1919 to July 7, 2007 (88 years)");
    // go back to the homepage
    driver.findElement(By.cssSelector("#logo > img")).click(); waitForUi();
    // search for nobody
    driver.findElement(By.id("search_by_name")).sendKeys("nobody"); waitForUi();
    // press enter
    driver.findElement(By.id("search_by_name")).sendKeys(ENTER); waitForUi();
    // make sure it shows our search
    TestFramework.assertEquals(driver.findElement(By.id("search_query")).getText(), "You searched for: nobody");
    // no one should be found
    TestFramework.assertEquals(driver.findElement(By.cssSelector("#random_persons_container > ul > li")).getText(), "No persons found");
  }

  private void testDetailsAndLinks() {
    // search for the first letters in Marjorie
    driver.findElement(By.id("search_by_name")).sendKeys("mar"); waitForUi();
    MyThread.sleep(20);
    // Click Marjorie's name
    driver.findElement(By.xpath("//span[contains(.,'Marjorie Katz')]")).click(); waitForUi();
    // Make sure we're on her detail page
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-name")).getText(), "Marjorie Katz");
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-era")).getText(), "February 3, 1925 to July 13, 2020 (95 years)");
    // Check that we see her brother listed
    TestFramework.assertEquals(driver.findElement(By.cssSelector(".siblings")).getText(), "Herbert Blumberg");
    // Go to her husband's page
    driver.findElement(By.linkText("Ellis Katz")).click(); waitForUi();
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
    driver.findElement(By.xpath("//button[contains(.,'Logout')]")).click(); waitForUi();
    TestFramework.assertEquals(driver.findElement(By.xpath("//p")).getText(), "You've been logged out");
  }

  private void login() {
    // open the login page
    driver.get("http://localhost:8080/login");
    // enter username
    driver.findElement(By.id("username")).click(); waitForUi();
    driver.findElement(By.id("username")).sendKeys("admin"); waitForUi();
    // enter password
    driver.findElement(By.id("password")).click(); waitForUi();
    String adminPassword;
    try {
      adminPassword = Files.readString(Path.of("../target/simple_db/admin_password"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    driver.findElement(By.id("password")).sendKeys(adminPassword); waitForUi();
    // login
    driver.findElement(By.id("login_button")).click(); waitForUi();
  }

  private void resetPassword() {
    // open the reset page
    driver.get("http://localhost:8080/resetpassword");
    // get the new suggested password
    String newPassword = driver.findElement(By.id("new_password")).getText();
    // reset the password
    driver.findElement(By.id("change_password")).click(); waitForUi();

    try {
      Files.writeString(Path.of("../target/simple_db/admin_password"), newPassword);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    driver.findElement(By.linkText("Home")).click(); waitForUi();
  }

  private void registerNewUser() {
    // open the register page
    driver.get("http://localhost:8080/register");
    // set a new username and password
    String newUsername = "authperson";
    String newPassword = "abcdef123567yay";
    driver.findElement(By.id("username")).sendKeys(newUsername); waitForUi();
    driver.findElement(By.id("password")).sendKeys(newPassword); waitForUi();
    // enter the new data
    driver.findElement(By.id("register_user_button")).click(); waitForUi();

    driver.findElement(By.linkText("Home")).click(); waitForUi();

    logout();

    // open the login page
    driver.get("http://localhost:8080/login");
    // enter username
    driver.findElement(By.id("username")).sendKeys(newUsername); waitForUi();
    // enter password
    driver.findElement(By.id("password")).sendKeys(newPassword); waitForUi();
    // login
    driver.findElement(By.id("login_button")).click(); waitForUi();

    logout();
  }
}
