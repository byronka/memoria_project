package com.renomad.inmra.uitests;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.MyThread;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.renomad.inmra.uitests.SimpleClient.post;
import static com.renomad.inmra.uitests.Utilities.waitForUi;
import static com.renomad.minum.testing.TestFramework.*;
import static org.openqa.selenium.Keys.DOWN;
import static org.openqa.selenium.Keys.ENTER;

public class Test1 {

  private WebDriver driver;
  private TestLogger logger;

  @Test
  public void testUI() throws IOException {
    Context context = buildTestingContext("ui_tests");
    logger = (TestLogger) context.getLogger();
    driver = new ChromeDriver();
    // set a decent window size
    driver.manage().window().setSize(new Dimension(1200, 1100));
    // open the homepage
    driver.get("http://localhost:8080/");
    // wait a sec before starting, to let browser finish its loading
    MyThread.sleep(1000);
    adminActivities();
    reviewWithoutEdits();
    driver.quit();
  }

  public void adminActivities() throws IOException {
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
    toggleMenu();
    driver.findElement(By.xpath("//button[contains(.,'Logout')]")).click(); waitForUi();
    driver.findElement(By.linkText("Index")).click(); waitForUi();
    login();
    playWithDates();
    testAddingNewPersons();
  }


  /**
   * This test does not change state - there is no need to restore the database
   * between runs of adjusting this test - just the first time.
   */
  public void reviewWithoutEdits() {
    // open the homepage
    driver.get("http://localhost:8080/");
    testDetailsAndLinks();
    driver.get("http://localhost:8080/");
    toggleMenu();
    TestFramework.assertTrue(driver.findElement(By.cssSelector("nav")).getText().contains(
            "Create New Person\n" +
                    "List all Persons\n" +
                    "Administration page\n" +
                    "Register user\n" +
                    "Reset password\n" +
                    "Logout"));

    TestFramework.assertEquals(driver.findElement(By.linkText("Create New Person")).getText(), "Create New Person");
    TestFramework.assertEquals(driver.findElement(By.linkText("List all Persons")).getText(), "List all Persons");
    TestFramework.assertEquals(driver.findElement(By.xpath("//button[contains(.,'Logout')]")).getText(), "Logout");
    // click on "Persons"
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();

    sortByBirthDateAscending();

    sortByBirthDateDescending();

    checkDefaultSorting();

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

    // reset the page by resetting the search
    driver.findElement(By.id("clear-search-submit")).click(); waitForUi();

    // Click the edit button for Ellis
    driver.findElement(By.cssSelector("#\\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div:nth-child(1) > a:nth-child(2)")).click(); waitForUi();
    driver.findElement(By.id("view_read_only_page_link")).click(); waitForUi();

    checkNavigationMenu();

    toggleMenu();
    navigateAround();

    checkExtendedFamilyDetails();
    driver.get("http://localhost:8080");
    logout();
  }

  private void checkNavigationMenu() {
    // check that we see nav items we expect
    toggleMenu();
    assertTrue(driver.findElement(By.cssSelector("nav")).getText().contains(
            "Create New Person\n" +
                    "List all Persons\n" +
                    "Administration page\n" +
                    "Register user\n" +
                    "Reset password\n" +
                    "Logout\n"
    ));
  }

  private void checkExtendedFamilyDetails() {
    // view some extended family details
    driver.findElement(By.partialLinkText("Ellis Katz")).click(); waitForUi();
    driver.findElement(By.linkText("Extended relatives")).click(); waitForUi();
    driver.findElement(By.linkText("Close relatives")).click(); waitForUi();

    // confirm we see some expected people in the close relatives
    List<WebElement> closeRelatives = driver.findElements(By.cssSelector(".close_relatives a"));
    List<String> closeRelativesNames = closeRelatives.stream().map(x -> x.getText()).toList();
    assertTrue(closeRelativesNames.contains("Tina"));
    assertTrue(closeRelativesNames.contains("Byron"));

    driver.findElement(By.linkText("Descendants")).click(); waitForUi();
    List<WebElement> descendantsGrouped = driver.findElements(By.cssSelector("ul.descendants-grouped li"));
    List<String> textValuesOfGroupedDescendants = descendantsGrouped.stream().map(x -> x.getText()).toList();
    assertTrue(textValuesOfGroupedDescendants.get(1).contains("children"), "Values were: " + textValuesOfGroupedDescendants);
    assertTrue(textValuesOfGroupedDescendants.get(2).contains("grandchildren"), "Values were: " + textValuesOfGroupedDescendants);

    driver.findElement(By.id("descendants-printable-link")).click(); waitForUi();
    driver.get("http://localhost:8080/index?search=katz");
    MyThread.sleep(100);
    driver.findElement(By.partialLinkText("Ellis Katz")).click(); waitForUi();

    driver.findElement(By.linkText("Ron")).click(); waitForUi();
    driver.findElement(By.linkText("Byron")).click(); waitForUi();
    driver.findElement(By.linkText("Extended relatives")).click(); waitForUi();
    List<WebElement> bloodRelatives = driver.findElements(By.cssSelector("ul.calculated-relatives.blood_relatives li"));
    String collectedBloodRelatives = bloodRelatives.stream().map(x -> x.getText()).collect(Collectors.joining(";"));
    assertTrue(collectedBloodRelatives.contains("Paul"));
    assertTrue(collectedBloodRelatives.contains("Dan"));

    driver.findElement(By.id("ancestors-printable-link")).click(); waitForUi();
  }

  private void navigateAround() {
    // navigate around a bit
    driver.findElement(By.id("logo")).click(); waitForUi();
    driver.get("http://localhost:8080/person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f"); // Ellis
    driver.findElement(By.linkText("Ron")).click(); waitForUi();
    driver.findElement(By.linkText("Dan")).click(); waitForUi();
    driver.findElement(By.linkText("Paul")).click(); waitForUi();
    driver.findElement(By.linkText("Tina")).click(); waitForUi();
    driver.get("http://localhost:8080/index?search=katz");
    assertTrue(driver.findElement(By.id("ab1e7835-e6df-49ac-8492-9cf8b1686d7d_details")).isDisplayed());
  }

  private void checkDefaultSorting() {
    // clear the sorting
    driver.findElement(By.id("clear-sort-submit")).click(); waitForUi();

    // confirm the first person is now Ellis
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).getFirst().getText(), "Ellis Katz");
    driver.findElement(By.id("search_field")).click(); waitForUi();
    driver.findElement(By.id("search_field")).sendKeys("ellis"); waitForUi();
    driver.findElement(By.id("search-submit")).click(); waitForUi();
  }

  private void sortByBirthDateDescending() {
    driver.findElement(By.id("sort-select")).click(); waitForUi();
    {
      WebElement dropdown = driver.findElement(By.id("sort-select"));
      dropdown.findElement(By.xpath("//option[. = 'birth date descending']")).click(); waitForUi();
    }

    // confirm the first person is Margie
    assertEquals(
            driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).getFirst().getText(),
            "Margie Sylvia Evensky Goodman");
  }

  private void sortByBirthDateAscending() {
    // choose to sort by birth date ascending
    driver.findElement(By.id("sort-select")).click(); waitForUi();
    {
      WebElement dropdown = driver.findElement(By.id("sort-select"));
      dropdown.findElement(By.xpath("//option[. = 'birth date ascending']")).click(); waitForUi();
    }

    // confirm the first person is Ron
    assertEquals(driver.findElements(By.cssSelector("div.name-and-lifespan > span.name")).getFirst().getText(), "Ron");
  }

  /**
   * There are various techniques and alternatives for adding
   * connections between persons and adding new persons.
   * <br>
   * We'll hit everything not already tested
   * <br>
   * They are:
   * 1. clicking Create New Person from the menu (already tested in createNewGuy)
   * 2. Adding a new name for a relation when looking at a person.
   * 3. Adding the name of an existing person as a relation
   *   3.a. adding by connecting in the "edit" mode, by adding a new anchor tag in the field
   *   3.b. adding by choosing the "simple" option when entering their name from the person detail page
   *   3.c. choosing the "complete" option, which automatically makes lots more connections
   */
  private void testAddingNewPersons() {
    driver.findElement(By.id("edit_navigation_link")).click(); waitForUi();
    addElysaAsParent();
    addHarryAsChild();
    addKevinAsSibling();

    addCassandraAsSpouseOfRon();

    // add another parent of Ron, "Bagel"
    driver.findElement(By.linkText("Ron")).click(); waitForUi();
    WebElement parentInputElement = driver.findElement(By.cssSelector(".add-relation-action.parent input.linkages"));
    parentInputElement.sendKeys("Bagel"); waitForUi();
    driver.findElement(By.cssSelector(".add-relation-action.parent input[type=submit]")).click(); waitForUi();

    String ronSimpleContainerText = driver.findElement(By.cssSelector("#simple_container ul")).getText();
    assertEquals(ronSimpleContainerText, "Ron will get a new parent: Bagel");
    String ronCompleteContainerText = driver.findElement(By.cssSelector("#complete_container ul")).getText();
    assertEquals(ronCompleteContainerText, "Ron will get a new parent: Bagel\n" +
            "parent Ellis Katz will get a new spouse: Bagel\n" +
            "parent Marjorie Katz will get a new spouse: Bagel\n" +
            "sibling Dan will get a new parent: Bagel\n" +
            "sibling Paul will get a new parent: Bagel\n" +
            "sibling Harry will get a new parent: Bagel\n" +
            "sibling Kevin will get a new parent: Bagel");
    driver.findElement(By.cssSelector("button[type=submit]")).click(); waitForUi();
    assertEquals(collectNames("#parent_list a"), "Ellis Katz Marjorie Katz Bagel");
    driver.findElement(By.linkText("Bagel")).click(); waitForUi();
    assertEquals(collectNames("#spouse_list a"), "Ellis Katz Marjorie Katz");
    assertEquals(collectNames("#child_list a"), "Ron Dan Paul Harry Kevin");
    driver.findElement(By.linkText("Ellis Katz")).click(); waitForUi();
    assertEquals(collectNames("#child_list a"), "Ron Dan Paul Harry Kevin");
    assertEquals(collectNames("#spouse_list a"), "Marjorie Katz Bagel");
  }
  
  private String collectNames(String cssSelector) {
    return driver.findElements(By.cssSelector(cssSelector)).stream().map(WebElement::getText).collect(Collectors.joining(" "));
  }

  private void addCassandraAsSpouseOfRon() {
    // add Cassandra as (another) spouse of Ron
    driver.findElement(By.linkText("Ron")).click(); waitForUi();
    WebElement spouseInputElement = driver.findElement(By.cssSelector(".add-relation-action.spouse input.linkages"));
    spouseInputElement.sendKeys("Cassandra"); waitForUi();
    driver.findElement(By.cssSelector(".add-relation-action.spouse input[type=submit]")).click(); waitForUi();

    String ronSimpleContainerText = driver.findElement(By.cssSelector("#simple_container ul")).getText();
    assertEquals(ronSimpleContainerText, "Ron will get a new spouse: Cassandra");
    String ronCompleteContainerText = driver.findElement(By.cssSelector("#complete_container ul")).getText();
    assertEquals(ronCompleteContainerText, "Ron will get a new spouse: Cassandra\n" +
            "child Byron will get a new parent: Cassandra\n" +
            "child Elysa will get a new parent: Cassandra");
    driver.findElement(By.cssSelector("button[type=submit]")).click(); waitForUi();
    assertEquals(collectNames("#spouse_list a"), "Susan Cassandra");
    assertEquals(collectNames("#child_list a"), "Byron Elysa");
    driver.findElement(By.linkText("Cassandra")).click(); waitForUi();
    assertEquals(collectNames("#child_list a"), "Byron Elysa");
    assertEquals(collectNames("#spouse_list a"), "Ron");
    driver.findElement(By.linkText("Byron")).click(); waitForUi();
    assertEquals(collectNames("#parent_list a"), "Susan Ron Cassandra");
  }

  /**
   * Add an existing person, Elysa, to an existing person, John Doe
   */
  private void addElysaAsParent() {
    // add Elysa as a parent to John Doe
    WebElement parentInputElement = driver.findElement(By.cssSelector(".add-relation-action.parent input.linkages"));
    parentInputElement.sendKeys("Elysa"); waitForUi();
    MyThread.sleep(100);
    parentInputElement.sendKeys(DOWN); waitForUi();
    parentInputElement.sendKeys(ENTER); waitForUi();
    driver.findElement(By.cssSelector(".add-relation-action.parent input[type=submit]")).click(); waitForUi();
    driver.findElement(By.cssSelector("button[type=submit]")).click(); waitForUi();
    String parent = driver.findElement(By.id("parent_list")).getText();
    assertEquals("Elysa X", parent);
  }

  private void addHarryAsChild() {
    // head to the regular view page for Harry
    driver.findElement(By.id("view_read_only_page_link")).click();
    // jump over to Marjorie to add a person
    driver.findElement(By.linkText("Extended relatives")).click(); waitForUi();
    driver.findElement(By.linkText("Marjorie Katz")).click(); waitForUi();
    driver.findElement(By.id("edit_navigation_link")).click(); waitForUi();
    WebElement childrenInputElement = driver.findElement(By.cssSelector(".add-relation-action.child input.linkages"));

    // add Harry as a new child of Marjorie
    childrenInputElement.sendKeys("Harry"); waitForUi();
    driver.findElement(By.cssSelector(".add-relation-action.child input[type=submit]")).click(); waitForUi();
    String simpleContainerText = driver.findElement(By.cssSelector("#simple_container ul")).getText();
    assertEquals(simpleContainerText, "Marjorie Katz will get a new child: Harry");
    String completeContainerText = driver.findElement(By.cssSelector("#complete_container ul")).getText();
    assertEquals(completeContainerText, "Marjorie Katz will get a new child: Harry\n" +
            "child Ron will get a new sibling: Harry\n" +
            "child Dan will get a new sibling: Harry\n" +
            "child Paul will get a new sibling: Harry\n" +
            "spouse Ellis Katz will get a new child: Harry");
    driver.findElement(By.cssSelector("button[type=submit]")).click(); waitForUi();
    String marjorieChildren = collectNames("#child_list a");
    assertEquals("Ron Dan Paul Harry", marjorieChildren);
    driver.findElement(By.linkText("Harry")).click(); waitForUi();
    String harryParents = collectNames("#parent_list a");
    assertEquals("Marjorie Katz Ellis Katz", harryParents);
    String harrySiblings = collectNames("#sibling_list a");
    assertEquals("Ron Dan Paul", harrySiblings);
    driver.findElement(By.linkText("Ron")).click(); waitForUi();
    String ronSiblings = collectNames("#sibling_list a");
    assertEquals("Dan Paul Harry", ronSiblings);
    driver.findElement(By.linkText("Ellis Katz")).click(); waitForUi();
    String ellisChildren = collectNames("#child_list a");
    assertEquals("Ron Dan Paul Harry", ellisChildren);
    driver.findElement(By.linkText("Harry")).click(); waitForUi();
  }

  private void addKevinAsSibling() {
    // add Kevin as a sibling of Harry
    WebElement siblingsInputElement = driver.findElement(By.cssSelector(".add-relation-action.sibling input.linkages"));
    siblingsInputElement.sendKeys("Kevin"); waitForUi();
    driver.findElement(By.cssSelector(".add-relation-action.sibling input[type=submit]")).click(); waitForUi();
    String harrySimpleContainerText = driver.findElement(By.cssSelector("#simple_container ul")).getText();
    assertEquals(harrySimpleContainerText, "Harry will get a new sibling: Kevin");
    String harryCompleteContainerText = driver.findElement(By.cssSelector("#complete_container ul")).getText();
    assertEquals(harryCompleteContainerText, "Harry will get a new sibling: Kevin\n" +
            "parent Marjorie Katz will get a new child: Kevin\n" +
            "parent Ellis Katz will get a new child: Kevin\n" +
            "sibling Ron will get a new sibling: Kevin\n" +
            "sibling Dan will get a new sibling: Kevin\n" +
            "sibling Paul will get a new sibling: Kevin");
    driver.findElement(By.cssSelector("button[type=submit]")).click(); waitForUi();
    assertEquals(collectNames("#sibling_list a"), "Ron Dan Paul Kevin");
    assertEquals(collectNames("#parent_list a"), "Marjorie Katz Ellis Katz");
    driver.findElement(By.linkText("Kevin")).click(); waitForUi();
    assertEquals(collectNames("#sibling_list a"), "Harry Ron Dan Paul");
    assertEquals(collectNames("#parent_list a"), "Marjorie Katz Ellis Katz");
  }


  private void toggleMenu() {
    driver.findElement(By.id("menuToggle")).click(); waitForUi();
    MyThread.sleep(120);
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
    driver.findElement(By.id("view_read_only_page_link")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "1987 to Unknown");
    driver.navigate().back();
    driver.findElement(By.id("born_date_unknown_checkbox")).click(); waitForUi();
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    driver.findElement(By.id("view_read_only_page_link")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "Unknown to Unknown");
    driver.navigate().back();
    driver.findElement(By.id("death_date_unknown_checkbox")).click(); waitForUi();
    {
      WebElement element = driver.findElement(By.id("born_date_year_only_checkbox"));
      boolean isEditable = element.isEnabled() && element.getDomAttribute("readonly") == null;
      assertFalse(isEditable);
    }
    {
      WebElement element = driver.findElement(By.id("born_input"));
      boolean isEditable = element.isEnabled() && element.getDomAttribute("readonly") == null;
      assertFalse(isEditable);
    }
    driver.findElement(By.id("died_input")).clear(); waitForUi();
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    driver.findElement(By.id("view_read_only_page_link")).click(); waitForUi();
    assertEquals(driver.findElement(By.cssSelector(".lifespan-era")).getText(), "Unknown to");
  }

  private void checkAdministrationPage() {
    // go to admin page
    toggleMenu();
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
    toggleMenu();
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

    // add a bio
    driver.findElement(By.id("biographical_section_details")).click(); waitForUi();
    driver.findElement(By.id("biography_input")).sendKeys("<p>\nIt was the best of times, it was the worst of times.\n</p>"); waitForUi();
    // add notes
    driver.findElement(By.id("notes_details")).click(); waitForUi();
    driver.findElement(By.id("notes_input")).sendKeys("Here are some secret notes about John.  Don't let anyone see this."); waitForUi();

    // add extra fields
    driver.findElement(By.id("extra_fields_details")).click(); waitForUi();

    // Interesting.  I needed to switch to a JavaScript click instead of a normal
    // Selenium click.  Not sure why this was necessary.  The button in question
    // *is* connected to Javascript code - maybe that is a reason.
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"add_extra_field_button\").click()"); waitForUi();

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

    // delete the birthplace extra field
    driver.findElement(By.cssSelector("#extra_data_item_3 > button.extra_data_item_delete")).click(); waitForUi();

    // save the data
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    driver.findElement(By.id("view_read_only_page_link")).click(); waitForUi();

    // assert some stuff about the view
    {
      // wedding date
      String weddingDateText = driver.findElement(By.cssSelector(".extra-info.Wedding.date")).getText();
      assertEquals(weddingDateText, "Wedding date: 1999-01-04");

      // graduation dates - there are two
      String graduationDateText1 = driver.findElements(By.cssSelector(".extra-info.Graduation.date")).get(0).getText();
      assertEquals(graduationDateText1, "Graduation date: 1999-01-04");
      String graduationDateText2 = driver.findElements(By.cssSelector(".extra-info.Graduation.date")).get(1).getText();
      assertEquals(graduationDateText2, "Graduation date: 1988");

      // deathplace
      String deathplaceText = driver.findElement(By.cssSelector(".extra-info.Deathplace")).getText();
      assertEquals(deathplaceText, "Deathplace: St. Louis, Missouri");

      // check the biography
      String biographyText = driver.findElement(By.cssSelector(".biography")).getText();
      assertEquals(biographyText, "It was the best of times, it was the worst of times.");

      // check the gender of John Doe
      String genderText = driver.findElement(By.cssSelector(".gender")).getText();
      assertEquals(genderText, "male");
    }
    // open editing for John Doe's details
    toggleMenu();
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();

    driver.findElement(By.cssSelector("#search_field")).sendKeys("john doe"); waitForUi();
    driver.findElement(By.cssSelector("#search-submit")).click(); waitForUi();
    driver.findElement(By.cssSelector("div.other_components > div:nth-child(1) > a:nth-child(2)")).click(); waitForUi();
    driver.findElement(By.id("extra_fields_details")).click(); waitForUi();

    // update death year
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"died_input\").value = \"2097-01-04\"");
    // update notes
    driver.findElement(By.id("notes_details")).click(); waitForUi();
    driver.findElement(By.id("notes_input")).sendKeys("Here are some secret notes about John. "); waitForUi();

    // necessary to cause the event to inspect if the form changed, to enable the save button
    driver.findElement(By.id("name_input")).click(); waitForUi();

    // add a new extra field - another wedding

    driver.findElement(By.id("add_extra_field_button")).click(); waitForUi();
    driver.findElement(By.id("extra_data_key_5")).click(); waitForUi();
    {
      Select dropdown = new Select(driver.findElement(By.id("extra_data_key_5")));
      dropdown.selectByValue("Wedding date");
    }
    driver.findElement(By.id("extra_data_value_5")).click(); waitForUi();
    ((JavascriptExecutor)driver).executeScript(
            "document.getElementById(\"extra_data_value_5\").value = \"2015-01-04\"");

    // enter data
    driver.findElement(By.id("form_submit_button")).click(); waitForUi();
    driver.findElement(By.id("view_read_only_page_link")).click(); waitForUi();


    // assert some stuff about the view
    {
      // wedding date - there are two
      String weddingDateText1 = driver.findElements(By.cssSelector(".extra-info.Wedding.date")).get(0).getText();
      assertEquals(weddingDateText1, "Wedding date: 1999-01-04");
      String weddingDateText2 = driver.findElements(By.cssSelector(".extra-info.Wedding.date")).get(1).getText();
      assertEquals(weddingDateText2, "Wedding date: 2015-01-04");

      // graduation dates - there are two
      String graduationDateText1 = driver.findElements(By.cssSelector(".extra-info.Graduation.date")).get(0).getText();
      assertEquals(graduationDateText1, "Graduation date: 1999-01-04");
      String graduationDateText2 = driver.findElements(By.cssSelector(".extra-info.Graduation.date")).get(1).getText();
      assertEquals(graduationDateText2, "Graduation date: 1988");

      // deathplace
      String deathplaceText = driver.findElement(By.cssSelector(".extra-info.Deathplace")).getText();
      assertEquals(deathplaceText, "Deathplace: St. Louis, Missouri");

      // check the biography
      String biographyText = driver.findElement(By.cssSelector(".biography")).getText();
      assertEquals(biographyText, "It was the best of times, it was the worst of times.");

      // check the gender of John Doe
      String genderText = driver.findElement(By.cssSelector(".gender")).getText();
      assertEquals(genderText, "male");
    }
  }

  private void modifyEllisPhotos() throws IOException {
    // click on ellis's photos #\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div.photos > a
    driver.findElement(By.cssSelector("#\\38 3fe56ff-1607-4057-8cb0-31f62ccb930f_details > div.other_components > div.photos > a")).click(); waitForUi();
    // add a description for one of ellis's photos
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).click(); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).sendKeys("Ellis was always smoking at home, during dinner"); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .description_save_button")).click(); waitForUi();
    MyThread.sleep(100);

    // modify a caption for a photo
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).clear(); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).sendKeys("Ellis at dinner smoking, as was his wont."); waitForUi();
    driver.findElement(By.cssSelector("tr:nth-child(1) .description_save_button")).click(); waitForUi();
    // give a second to finish saving the data
    MyThread.sleep(100);
    driver.navigate().refresh();

    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(1) .short_description")).getText(), "Ellis at dinner smoking, as was his wont.");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).getText(), "Ellis was always smoking at home, during dinner");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(3) .short_description")).getText(), "Ellis lounging by the pool");

    // prepare to take action by POST instead of ordinary (JavaScript) approach
    Cookie cookie = driver.manage().getCookies().stream().filter(x -> x.getName().equals("sessionid")).findFirst().orElseThrow();
    String photoId = driver.findElement(By.cssSelector("tbody > tr:nth-of-type(1)")).getDomAttribute("data-photoid");

    // modify the description and caption by POST request instead - as though JavaScript was disabled.
    post("http://localhost:8080/photodescriptionupdate", "caption=foo&long_description=foo&photoid="+photoId, List.of("Cookie", cookie.toString()));

    driver.navigate().refresh();

    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-of-type(1) .short_description")).getText(), "foo");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(1) .long_description")).getText(), "foo");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("tr:nth-child(3) .short_description")).getText(), "Ellis lounging by the pool");

    deleteAPhoto();

    deleteAPhotoByPost(cookie);

    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));

    uploadPhoto(wait);

    uploadVideo(wait);

    uploadSecondVideo(wait);

    // modify the caption of video with identifier 1
    modifyCaptionOfVideo();

    // hit some edge cases of adjusting the video caption
    videoPosterEdgeCases(cookie);

    modifyDescriptionOfVideo();

    // give a second to finish saving the data
    MyThread.sleep(100);
    driver.navigate().refresh();

    TestFramework.assertEquals(driver.findElement(By.cssSelector("textarea[data-videoid=\"1\"].short_description")).getText(), "This is a revision of the video caption");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("textarea[data-videoid=\"1\"].long_description")).getText(), "This is a revision of the video description");

    // prepare to take action by POST instead of ordinary (JavaScript) approach

    // modify the description and caption by POST request instead - as though JavaScript was disabled.
    post("http://localhost:8080/videodescriptionupdate", "long_description=foo&caption=bar&videoid=1", List.of("Cookie", cookie.toString()));

    driver.navigate().refresh();

    TestFramework.assertEquals(driver.findElement(By.cssSelector("textarea[data-videoid=\"1\"].long_description")).getText(), "foo");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("textarea[data-videoid=\"1\"].short_description")).getText(), "bar");

    deleteVideo();

    deleteVideoByPost(cookie);

    uploadVideo(wait);
    MyThread.sleep(150);
    driver.findElement(By.cssSelector("input[data-videoid=\"1\"]")).clear(); waitForUi();
    String photoUrl = driver.findElement(By.cssSelector("#table_container > table > tbody > tr:nth-child(1) > td:nth-child(1) > a")).getDomAttribute("href");
    driver.findElement(By.cssSelector("input[data-videoid=\"1\"]")).sendKeys(photoUrl); waitForUi();
    driver.findElement(By.cssSelector("button[data-videoid=\"1\"].poster_button")).click(); waitForUi();

    // give the page time to refresh
    MyThread.sleep(500);
    copyVideoToRon();
  }

  /**
   * Try a few edge cases for adjusting the video poster
   */
  private void videoPosterEdgeCases(Cookie cookie) {

    logger.test("trying to hit the endpoint unauth'd will return 400");
    {
      HttpResponse<String> post = post("http://localhost:8080/videoposterupdate", "long_description=foo&videoid=1", List.of());
      assertEquals(post.statusCode(), 400);
    }

    logger.test("sending a video id that does not parse to a number will return a 400");
    {
      HttpResponse<String> post = post("http://localhost:8080/videoposterupdate", "postervalue=foo&videoid=abc", List.of("Cookie", cookie.toString()));
      assertEquals(post.statusCode(), 400);
    }

    logger.test("happy path - actually adjust the value");
    {
      HttpResponse<String> post = post("http://localhost:8080/videoposterupdate", "postervalue=foo&videoid=1", List.of("Cookie", cookie.toString()));
      assertEquals(post.statusCode(), 303);
      assertEquals(post.headers().firstValue("location").orElseThrow(), "/message?message=The+video%27s+poster+has+been+modified&redirect=%2Fphotos%3Fpersonid%3D83fe56ff-1607-4057-8cb0-31f62ccb930f");
    }
  }

  private void copyVideoToRon() {
    driver.findElement(By.cssSelector("td[data-videoid=\"1\"].copy_video_section button")).click(); waitForUi();
    MyThread.sleep(150);
    driver.findElement(By.id("person_selection_input")).sendKeys("Ron"); waitForUi();
    assertEquals("Copy Video | Inmra", driver.getTitle());
    MyThread.sleep(150);
    driver.findElement(By.id("person_selection_input")).sendKeys(DOWN); waitForUi();
    driver.findElement(By.id("person_selection_input")).sendKeys(ENTER); waitForUi();
    driver.findElement(By.id("short_description")).clear(); waitForUi();
    driver.findElement(By.id("short_description")).sendKeys("This video caption has been adapted to suit Ron"); waitForUi();
    driver.findElement(By.id("long_description")).clear(); waitForUi();
    driver.findElement(By.id("long_description")).sendKeys("This video description has been adapted to suit Ron"); waitForUi();
    driver.findElement(By.id("copy_photo_button")).click(); waitForUi();
    assertEquals("List Photos | Inmra", driver.getTitle());
    assertEquals(driver.findElement(By.id("view-person-detail-link")).getText(), "Ron");

    TestFramework.assertEquals(driver.findElement(By.cssSelector("textarea[data-videoid=\"2\"].short_description")).getText(), "This video caption has been adapted to suit Ron");
    TestFramework.assertEquals(driver.findElement(By.cssSelector("textarea[data-videoid=\"2\"].long_description")).getText(), "This video description has been adapted to suit Ron");
  }

  private void deleteVideo() {
    // delete a video, confirm it is gone
    // first open the details element, to expose the delete button
    var deleteDetailsElement = driver.findElement(By.cssSelector("details[data-videoid=\"1\"]"));
    deleteDetailsElement.click(); waitForUi();
    MyThread.sleep(100);
    // then, click the delete button
    var deleteButton = driver.findElement(By.cssSelector("button[data-videoid=\"1\"].delete_button"));
    deleteButton.click(); waitForUi();
    MyThread.sleep(200);

    // confirm the alert
    Alert videoDeleteAlert = driver.switchTo().alert();
    videoDeleteAlert.accept();
    MyThread.sleep(50);
    {
      // the video should not be found - if we cannot find the short description, it's gone.
      List<WebElement> deletedVideoShortDescription = driver.findElements(By.cssSelector("textarea[data-videoid=\"1\"].short_description"));
      assert(deletedVideoShortDescription.isEmpty());
    }
  }

  private void deleteVideoByPost(Cookie cookie) {
    // delete a video, using a POST
    post("http://localhost:8080/deletevideo", "videoid=2", List.of("Cookie", cookie.toString()));
    driver.navigate().refresh();
    List<WebElement> videoElements = driver.findElements(By.cssSelector("tr[data-videoid=\"2\"]"));
    assert(videoElements.isEmpty());
  }

  private void modifyDescriptionOfVideo() {
    // modify a description for one of the videos
    WebElement videoDescription = driver.findElement(By.cssSelector("textarea[data-videoid=\"1\"].long_description"));
    videoDescription.clear(); waitForUi();
    videoDescription.sendKeys("This is a revision of the video description"); waitForUi();
    WebElement saveButtonForVideoDescription = driver.findElement(By.cssSelector("div[data-videoid=\"1\"].description_container button"));
    saveButtonForVideoDescription.click(); waitForUi();
    MyThread.sleep(50);
  }

  private void modifyCaptionOfVideo() {
    // modify a caption for one of the videos
    WebElement videoCaption = driver.findElement(By.cssSelector("textarea[data-videoid=\"1\"].short_description"));
    videoCaption.clear(); waitForUi();
    videoCaption.sendKeys("This is a revision of the video caption"); waitForUi();
    WebElement saveButtonForVideoCaption = driver.findElement(By.cssSelector("div[data-videoid=\"1\"].description_container button"));
    saveButtonForVideoCaption.click(); waitForUi();
    MyThread.sleep(50);
  }

  private void uploadSecondVideo(WebDriverWait wait) throws IOException {
    // upload a second video
    WebElement videoInput2 = driver.findElement(By.cssSelector("input#file_upload"));
    Path video2 = Path.of("../src/test/resources/images/video.mp4").toRealPath();
    videoInput2.sendKeys(video2.toString()); waitForUi();
    driver.findElement(By.cssSelector("input#short_description")).sendKeys("Here is a new video 2"); waitForUi();
    driver.findElement(By.cssSelector("textarea#long_description")).sendKeys("A video description 2"); waitForUi();
    driver.findElement(By.cssSelector("button#upload_button")).click(); waitForUi();

    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//textarea[contains(text(),'Here is a new video 2')]")));
  }

  private void uploadVideo(WebDriverWait wait) throws IOException {
    // upload a video
    WebElement videoInput = driver.findElement(By.cssSelector("input#file_upload"));
    Path video = Path.of("../src/test/resources/images/video.mp4").toRealPath();
    videoInput.sendKeys(video.toString()); waitForUi();
    driver.findElement(By.cssSelector("input#short_description")).sendKeys("Here is a new video"); waitForUi();
    driver.findElement(By.cssSelector("textarea#long_description")).sendKeys("A video description"); waitForUi();
    driver.findElement(By.cssSelector("button#upload_button")).click(); waitForUi();

    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//textarea[contains(text(),'Here is a new video')]")));
  }

  private void uploadPhoto(WebDriverWait wait) throws IOException {
    // upload a photo
    WebElement photoInput = driver.findElement(By.cssSelector("input#file_upload"));
    Path photo = Path.of("../src/test/resources/images/bessie.png").toRealPath();
    photoInput.sendKeys(photo.toString()); waitForUi();
    driver.findElement(By.cssSelector("input#short_description")).sendKeys("Here is a new photo"); waitForUi();
    driver.findElement(By.cssSelector("textarea#long_description")).sendKeys("This is the long description of the photo"); waitForUi();
    driver.findElement(By.cssSelector("button#upload_button")).click(); waitForUi();

    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[alt=\"thumbnail of Here is a new photo\"]")));
  }

  private void deleteAPhotoByPost(Cookie cookie) {
    // delete a photo, using a POST
    String photoIdForDeletion = driver.findElement(By.cssSelector("tbody > tr:nth-of-type(1)")).getDomAttribute("data-photoid");
    post("http://localhost:8080/deletephoto", "photoid="+photoIdForDeletion, List.of("Cookie", cookie.toString()));
    driver.navigate().refresh();
    List<WebElement> elements = driver.findElements(By.cssSelector("tr:nth-child(2) .short_description"));
    assert(elements.isEmpty());
  }

  private void deleteAPhoto() {
    // delete a photo, confirm it is gone
    driver.findElement(By.cssSelector("tbody > tr:nth-child(1) > .delete_section > details")).click(); waitForUi();
    // give the details control a bit of time to open
    MyThread.sleep(200);
    driver.findElement(By.cssSelector("tbody > tr:nth-child(1) > .delete_section > details .delete_button")).click(); waitForUi();
    // confirm the alert
    Alert alert = driver.switchTo().alert();
    alert.accept();
    MyThread.sleep(50);
    {
      List<WebElement> elements = driver.findElements(By.cssSelector("tr:nth-child(3) .short_description"));
      assert(elements.isEmpty());
    }
  }

  private void modifyMarjoriePhotos() {
    toggleMenu();
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();
    // search for Marjorie
    driver.findElement(By.id("search_field")).sendKeys("marjorie"); waitForUi();
    driver.findElement(By.id("search-submit")).click(); waitForUi();

    // go to Marjorie's photos
    driver.findElement(By.cssSelector(".photos a")).click(); waitForUi();

    // click to copy first photo to someone else - does not really matter which photo we pick
    driver.findElement(By.cssSelector("button.copy_to_other_person_button")).click(); waitForUi();

    // confirm that the short summary and longer description were brought in
    String shortDescription = driver.findElement(By.cssSelector("input#short_description")).getDomAttribute("value");
    assertEquals(shortDescription, "Marjorie in a graduation gown");
    String longDescription = driver.findElement(By.cssSelector("textarea#long_description")).getText();
    assertEquals(longDescription, "");


    // search for Ellis, we'll copy the photo to him
    driver.findElement(By.id("person_selection_input")).sendKeys("Ellis"); waitForUi();
    // wait a bit for the browser to get Ellis's info
    MyThread.sleep(50);
    driver.findElement(By.id("person_selection_input")).sendKeys(DOWN); waitForUi();
    driver.findElement(By.id("person_selection_input")).sendKeys(ENTER); waitForUi();
    driver.findElement(By.id("short_description")).clear(); waitForUi();
    driver.findElement(By.id("short_description")).sendKeys("This is a caption for a photo I am copying from Marjorie to Ellis"); waitForUi();
    driver.findElement(By.id("copy_photo_button")).click(); waitForUi();

    // now we should find ourselves on Ellis's photo page, with this new photo
    assertEquals(driver.findElement(By.id("view-person-detail-link")).getText(), "Ellis Katz");
    assertTrue(driver.getPageSource().contains("This is a caption for a photo I am copying"));
  }

  private void deleteMichelle() {
    // view the List all Persons page
    toggleMenu();
    driver.findElement(By.linkText("List all Persons")).click(); waitForUi();
    // make sure we see michelle
    {
      List<WebElement> elements = driver.findElements(By.xpath("//span[contains(.,'Michelle')]"));
      assert(!elements.isEmpty());
    }
    // go to her edit page
    driver.findElement(By.cssSelector("#a5e8e11c-26d2-484f-954d-8cc001330f10_details")).findElement(By.linkText("edit")).click(); waitForUi();
    // click the delete button
    driver.findElement(By.linkText("Delete")).click(); waitForUi();
    // give the control a bit of time to open.
    MyThread.sleep(200);
    // click the delete button
    driver.findElement(By.id("delete_button")).click(); waitForUi();

    // confirm we don't see michelle
    // view the List all Persons page
    driver.findElement(By.linkText("here")).click(); waitForUi();
    driver.findElement(By.id("search_field")).sendKeys("michelle"); waitForUi();
    driver.findElement(By.id("search-submit")).click(); waitForUi();
    String searchResult = driver.findElement(By.id("list_person_pane")).getText().trim(); waitForUi();
    assertEquals(searchResult, "Nothing to show"); waitForUi();
    driver.findElement(By.id("clear-search-submit")).click(); waitForUi();
  }

  private void testSearches() {

    TestFramework.assertEquals(driver.findElement(By.cssSelector("label")).getText(), "Search by name");
    // click in the search box
    driver.findElement(By.id("search_by_name")).click(); waitForUi();
    // enter a search term
    driver.findElement(By.id("search_by_name")).sendKeys("lou"); waitForUi();
    MyThread.sleep(80);
    driver.findElement(By.xpath("//span[contains(.,'Louis Harold Goodman')]")).click(); waitForUi();
    TestFramework.assertEquals(driver.findElement(By.className("lifespan-era")).getText(), "January 30, 1919 to July 7, 2007 (88 years)");

    // confirm we see the expected results for Lou's kids
    String louChildren = driver.findElement(By.className("children")).getText();
    assertEquals(louChildren, "Private and Gary");

    // look at the data on the page for relatives, as a shortcut to see what kind of
    // data we're providing
    Map<String, Map<String,String>> relatives_data = (Map<String, Map<String,String>>)((JavascriptExecutor)driver).executeScript("return relatives_data");
    List<String> relationships = relatives_data.values().stream().map(stringStringMap -> stringStringMap.get("relationship")).toList();
    assertEqualsDisregardOrder(relationships, List.of(
            "spouse of Louis Harold Goodman",
            "Louis Harold Goodman"
    ));

    // go back to the homepage
    driver.findElement(By.cssSelector("#logo > img")).click(); waitForUi();
    // search for nobody
    driver.findElement(By.id("search_by_name")).sendKeys("nobody"); waitForUi();
    // press enter
    driver.findElement(By.id("search_by_name")).sendKeys(ENTER); waitForUi();
    // make sure it shows our search
    TestFramework.assertEquals(driver.findElement(By.id("search_query")).getText(), "You searched for: nobody");  waitForUi();
    // no one should be found
    TestFramework.assertEquals(driver.findElement(By.cssSelector("#no_persons_found_alert")).getText(), "No persons found");
  }

  private void testDetailsAndLinks() {
    // search for the first letters in Marjorie
    driver.findElement(By.id("search_by_name")).sendKeys("mar"); waitForUi();
    MyThread.sleep(80);
    // Click Marjorie's name
    driver.findElement(By.partialLinkText("Marjorie Katz")).click(); waitForUi();
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

  private void logout() {
    toggleMenu();
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
      adminPassword = Files.readString(Path.of("../admin_password"));
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
      Files.writeString(Path.of("../admin_password"), newPassword);
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
