package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.Month;
import com.renomad.inmra.featurelogic.persons.RelationType;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

import static com.renomad.minum.testing.TestFramework.*;

public class PersonCreateServicesTests {

    private TestLogger logger;

    @Before
    public void init() {
        Context context = buildTestingContext("PersonCreateServicesTests");
        logger = (TestLogger)context.getLogger();
    }

    @Test
    public void testBuildingNecessaryDirectories() {
        IFileUtils mockFileUtils = buildMockFileUtilsThatThrows();

        PersonCreateServices.buildNecessaryDirectories(
                logger,
                mockFileUtils,
                Path.of(""),
                Path.of(""));

        assertTrue(logger.findFirstMessageThatContains("Hi, the directory creation failed").length() > 0);
    }

    /**
     * A {@link com.renomad.inmra.utils.FileUtils} that always throws
     * an exception from {@link com.renomad.inmra.utils.FileUtils#makeDirectory(Path)}
     */
    private static IFileUtils buildMockFileUtilsThatThrows() {
        return new IFileUtils() {

            @Override
            public void makeDirectory(Path path) throws IOException {
                throw new IOException("Hi, the directory creation failed.");
            }

            @Override
            public String readTemplate(String path) {
                return null;
            }
        };
    }

    /**
     * If a date is unknown.  That is, it exists, we just don't
     * know it.  For example, when exactly did Napoleon die? He definitely dead though.
     */
    @Test
    public void testAddingLifespanDate_ExistsButUnknown() {
        HashMap<String, String> templateMap = new HashMap<>();

        PersonCreateServices.addLifespanDate(templateMap, Date.EXISTS_BUT_UNKNOWN, PersonCreateServices.BornOrDied.BORN);

        assertEquals(templateMap.get("is_born_date_unknown_checked"), "checked");
        assertEquals(templateMap.get("is_born_date_unknown"), "disabled");
        assertEquals(templateMap.get("is_born_date_year_only_checked"), "");
        assertEquals(templateMap.get("born_date_input_type"), "date");
        assertEquals(templateMap.get("born_input_value"), "");
    }

    /**
     * If the date is just a year, e.g. 1984
     */
    @Test
    public void testAddingLifespanDate_YearOnly() {
        HashMap<String, String> templateMap = new HashMap<>();

        PersonCreateServices.addLifespanDate(templateMap, new Date(1984, Month.NONE, 1), PersonCreateServices.BornOrDied.BORN);

        assertEquals(templateMap.get("is_born_date_unknown_checked"), "");
        assertEquals(templateMap.get("is_born_date_unknown"), "");
        assertEquals(templateMap.get("is_born_date_year_only_checked"), "checked");
        assertEquals(templateMap.get("born_date_input_type"), "number");
        assertEquals(templateMap.get("born_input_value"), "1984");
    }

    @Test
    public void testAddingLifespanDate_FullDate() {
        HashMap<String, String> templateMap = new HashMap<>();

        PersonCreateServices.addLifespanDate(templateMap, new Date(1984, Month.JANUARY, 1), PersonCreateServices.BornOrDied.BORN);

        assertEquals(templateMap.get("is_born_date_unknown_checked"), "");
        assertEquals(templateMap.get("is_born_date_unknown"), "");
        assertEquals(templateMap.get("is_born_date_year_only_checked"), "");
        assertEquals(templateMap.get("born_date_input_type"), "date");
        assertEquals(templateMap.get("born_input_value"), "1984-01-01");
    }

    @Test
    public void testBuildingRelation_Sibling() {
        PersonCreateServices.RelationInputs relationInputs = PersonCreateServices.fillRelationInputs("element goes here", RelationType.SIBLING);
        assertEquals(relationInputs.siblingInput(), "element goes here");
    }
    @Test
    public void testBuildingRelation_Parent() {
        PersonCreateServices.RelationInputs relationInputs = PersonCreateServices.fillRelationInputs("element goes here", RelationType.PARENT);
        assertEquals(relationInputs.childInput(), "element goes here");
    }
    @Test
    public void testBuildingRelation_Child() {
        PersonCreateServices.RelationInputs relationInputs = PersonCreateServices.fillRelationInputs("element goes here", RelationType.CHILD);
        assertEquals(relationInputs.parentInput(), "element goes here");
    }
    @Test
    public void testBuildingRelation_Spouse() {
        PersonCreateServices.RelationInputs relationInputs = PersonCreateServices.fillRelationInputs("element goes here", RelationType.SPOUSE);
        assertEquals(relationInputs.spouseInput(), "element goes here");
    }
}
