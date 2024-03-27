package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.renomad.minum.testing.TestFramework.*;

public class FamilyGraphBuilderTests {

    private static FamilyGraphBuilder familyGraphBuilder;
    private static IPersonLruCache personLruCache;

    @BeforeClass
    public static void init() throws IOException {
        Context context = buildTestingContext("FamilyGraphBuilderTests");
        TestLogger logger = (TestLogger) context.getLogger();
        FileUtils fileUtils = context.getFileUtils();
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory).resolve("family_graph_builder_tests_person"), logger);
        var personDb = context.getDb("family_graph_builder_tests_person", Person.EMPTY);

        var personFiles = addPersonFilesTestData();
        personLruCache = buildLruCache(personFiles);
        // build a database for our testing
        for (var p : personFiles) {
            personDb.write(new Person(0L, p.getId(), p.getName(), p.getBorn(), p.getDied()));
        }

        familyGraphBuilder = new FamilyGraphBuilder(context, personDb, personLruCache, logger);
    }

    /**
     * A complicated test for a complicated algorithm.
     * <br>
     * In order to (far) more efficiently modify the nodes whenever we modify a person,
     * we'll only modify that node and its immediate relations.
     * <br>
     * This test starts with Byron having a sister, and then modifies him to not.  The only
     * relations that should be modified are Byron and his immediate relations.
     */
    @Test
    public void testBuildFamilyGraph() {
        PersonNode byronPersonNodeBefore = familyGraphBuilder.personNodes.get(UUID.fromString("c2b45e41-a46f-4cbf-965d-4f4fcf01d610"));
        List<FamilyGraph.Relationship> siblingsBefore = FamilyGraph.siblings(byronPersonNodeBefore);
        assertEquals(siblingsBefore.size(), 1);
        assertEquals(siblingsBefore.get(0).relationDescription(), "sister of Byron Katz");
        var byronRevised = new PersonFile(6L, UUID.fromString("c2b45e41-a46f-4cbf-965d-4f4fcf01d610"), "", "Byron Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY,
                "",
                "Susanne",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and " +
                        "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>",
                "Cameron David and Corey",
                "", "", "", Gender.MALE, null, "");
        personLruCache.putToPersonFileLruCache(byronRevised.getId().toString(), byronRevised);

        familyGraphBuilder.buildFamilyGraph(byronRevised);

        PersonNode byronPersonNodeAfter = familyGraphBuilder.personNodes.get(UUID.fromString("c2b45e41-a46f-4cbf-965d-4f4fcf01d610"));
        List<FamilyGraph.Relationship> siblingsAfter = FamilyGraph.siblings(byronPersonNodeAfter);
        assertEquals(siblingsAfter.size(), 0);
    }


    private static IPersonLruCache buildLruCache(List<PersonFile> personFiles) {

        return new IPersonLruCache() {

            @Override
            public void putToPersonFileLruCache(String id, PersonFile personFile) {
                personFiles.removeIf(x -> x.getId().toString().equals(id));
                personFiles.add(personFile);
            }

            @Override
            public void removeFromPersonFileLruCache(String id) {

            }

            @Override
            public PersonFile getCachedPersonFile(Person person) {
                try {
                    return personFiles.stream().filter(x -> x.getId().equals(person.getId())).findAny().get();
                } catch (NoSuchElementException ex) {
                    throw new RuntimeException("Could not find a personfile for id: " + person.getId());
                }
            }

            @Override
            public PersonFile getCachedPersonFile(String uuidForPerson) {
                try {
                    return personFiles.stream().filter(x -> x.getId().toString().equals(uuidForPerson)).findAny().get();
                } catch (NoSuchElementException ex) {
                    throw new RuntimeException("Could not find a personfile for id: " + uuidForPerson);
                }
            }
        };
    }



    private static List<PersonFile> addPersonFilesTestData() {
        List<PersonFile> personFiles = new ArrayList<>();
        var ellis = new PersonFile(1L, UUID.fromString("83fe56ff-1607-4057-8cb0-31f62ccb930f"), "photo?name=b6c3eab6-de63-321e-b24b-30c46dc3626f.jpg",
                "Ellis Katz", com.renomad.inmra.featurelogic.persons.Date.extractDate("1921-11-21"), com.renomad.inmra.featurelogic.persons.Date.extractDate("2020-03-12"), "Florence", "<a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>", "Robert and Ethel",
                "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>, <a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a>, and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(ellis);

        var marjorie = new PersonFile(2L, UUID.fromString("ab1e7835-e6df-49ac-8492-9cf8b1686d7d"), "photo?name=e0b61d10-2f1a-3d55-be20-a20c4da121f4.jpg", "Marjorie Katz",
                com.renomad.inmra.featurelogic.persons.Date.extractDate("1925-02-03"), com.renomad.inmra.featurelogic.persons.Date.extractDate("2020-07-13"), "<a href=\"person?id=5eb1d7a5-09bd-45c9-b5d8-7c0510f0a359\">Herbert Blumberg</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a>", "Louis and Leah",
                "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>, <a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a>, and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(marjorie);

        var marc = new PersonFile(3L, UUID.fromString("dbab1ed7-4147-4187-85b5-9029081b9cef"), "", "Marc Blumberg",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "Frank and Greg",
                "", "<a href=\"person?id=5eb1d7a5-09bd-45c9-b5d8-7c0510f0a359\">Herbert Blumberg</a> and Babette",
                "",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(marc);

        var herbert = new PersonFile(3L, UUID.fromString("5eb1d7a5-09bd-45c9-b5d8-7c0510f0a359"), "", "Herbert Blumberg",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "<a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie</a>",
                "", "Louis and Leah",
                "<a href=\"person?id=dbab1ed7-4147-4187-85b5-9029081b9cef\">Marc Blumberg</a>",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(herbert);

        var ron = new PersonFile(3L, UUID.fromString("ee3df413-1d4a-41a9-9d40-b597e2d8849e"), "", "Ron Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "<a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a> and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a> and <a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>",
                "<a href=\"person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610\">Byron</a> <a href=\"person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b\">Elysa</a>",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(ron);

        var susan = new PersonFile(3L, UUID.fromString("de4f411f-17aa-49d4-8879-071280d5b319"), "", "Susan Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "Gary",
                "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>", "<a href=\"person?id=4faec4bc-5993-496e-806d-a402a142f2ec\">Margie Sylvia Evensky Goodman</a> and <a href=\"person?id=b3cd0fe1-0322-4e71-8010-8eaf6d7d9333\">Louis Harold Goodman</a>",
                "<a href=\"person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610\">Byron</a> and <a href=\"person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b\">Elysa</a>",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(susan);

        var dan = new PersonFile(4L, UUID.fromString("f0306481-691b-4dd2-a807-2f1784abd509"), "", "Dan Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a> and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "<a href=\"person?id=a5e8e11c-26d2-484f-954d-8cc001330f10\">Michelle</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a> and <a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>",
                "Erica and Joelle",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(dan);

        var michelle = new PersonFile(5L, UUID.fromString("a5e8e11c-26d2-484f-954d-8cc001330f10"), "", "Michelle Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "",
                "<a href=person?id=f0306481-691b-4dd2-a807-2f1784abd509>Dan</a>", "",
                "Erica and Joelle",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(michelle);

        var byron = new PersonFile(6L, UUID.fromString("c2b45e41-a46f-4cbf-965d-4f4fcf01d610"), "", "Byron Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "<a href=\"person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b\">Elysa</a>",
                "Susanne", "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and <a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>",
                "Cameron David and Corey",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(byron);

        var elysa = new PersonFile(7L, UUID.fromString("ebb6f61d-20eb-421f-967a-71e0bfd7cf9b"), "", "Elysa Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "<a href=person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610>Byron</a>",
                "Dan", "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a> and <a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a>",
                "Nathan Viven and Andrew",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(elysa);

        var paul = new PersonFile(8L, UUID.fromString("0a75a5e4-8a30-4119-9f80-10b90211b0f1"), "", "Paul Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a> and <a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a>",
                "<a href=\"person?id=ae28b8c2-b167-4fd0-9407-fec67d66c732\">Tina</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a> and <a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>",
                "",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(paul);

        var tina = new PersonFile(9L, UUID.fromString("ae28b8c2-b167-4fd0-9407-fec67d66c732"), "", "Tina Katz",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "",
                "<a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>", "Reva",
                "",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(tina);

        var margie = new PersonFile(10L, UUID.fromString("4faec4bc-5993-496e-806d-a402a142f2ec"), "", "Margie Sylvia Evensky Goodman",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "",
                "<a href=\"person?id=b3cd0fe1-0322-4e71-8010-8eaf6d7d9333\">Louis Harold Goodman</a>", "",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and Gary",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(margie);

        var louis = new PersonFile(11L, UUID.fromString("b3cd0fe1-0322-4e71-8010-8eaf6d7d9333"), "", "Louis Goodman",
                com.renomad.inmra.featurelogic.persons.Date.EMPTY, Date.EMPTY, "",
                "<a href=\"person?id=4faec4bc-5993-496e-806d-a402a142f2ec\">Margie Sylvia Evensky Goodman</a>", "",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and Gary",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(louis);

        return personFiles;

    }
}
