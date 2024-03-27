package com.renomad.inmra.featurelogic.persons;

import com.renomad.minum.utils.SearchUtils;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.*;

import static com.renomad.inmra.featurelogic.persons.FamilyGraph.*;
import static com.renomad.minum.testing.TestFramework.*;

/**
 * Maybe easier to think of a family tree as a family graph.
 * <br>
 * I would like to build a graph of the people in my family.  This
 * data structure will allow me to do some fun stuff.  I will be
 * able to find connections between people.  Interesting statistics.
 * Create diagrams.
 * <br>
 * But to do this, I need to work with the materials I have.  Each
 * of the persons in this program have different relationships.
 * Here is an example:
 * <pre>
 *     Alice
 *     -----
 *     siblings:   Bob, Carol
 *     parents:    David, Eleanor
 *     spouse:     Frank
 * </pre>
 * <br>
 * So I want a node, "Alice", connected to "Bob" by an edge labeled "sibling"
 * <br>
 * As a practical matter, this means that each node (that is, each person) has a list
 * of relations.  In Alice's case, she would have a list of siblings, list of parents, list of spouses.
 * <br>
 * Then, we would have some algorithms for traversing the graph, etc.
 */
public class FamilyGraphTests {

    private Map<UUID, PersonNode> personNodes;
    private List<PersonFile> personFiles;

    @Before
    public void init() {
        personFiles = new ArrayList<>();

        addPersonFilesTestData();

        personNodes = new HashMap<>();
        for (var personFile : personFiles) {
            FamilyGraph.createNodeWithConnections(personFile, personFiles, personNodes);
        }
    }


    /**
     * This test creates a graph from a realistic database
     */
    @Test
    public void fullScaleTest() {
        printGraph(personNodes.values().stream().findFirst().orElseThrow());
    }

    @Test
    public void getAncestorsOfPerson() {
        PersonNode byron = SearchUtils.findExactlyOne(personNodes.values().stream(), x -> x.getName().contains("Byron"));
        var byronAncestors = ancestors(byron, 3);
        for (var person : byronAncestors) {
            System.out.printf("%s%n",person);
        }
    }

    @Test
    public void getDescendantsOfPerson() {
        PersonNode ellisKatz = SearchUtils.findExactlyOne(personNodes.values().stream(), x -> x.getName().equals("Ellis Katz"));
        var ellisDescendants = descendants(ellisKatz, 3);
        for (var person : ellisDescendants) {
            System.out.printf("%s%n",person);
        }
    }

    /**
     * Find people close by, also including through marriage. no more than 2 away.
     */
    @Test
    public void getCloseRelativesIncludingMarriage() {
        PersonNode ron = SearchUtils.findExactlyOne(personNodes.values().stream(), x -> x.getName().contains("Ron"));
        List<Relationship> ronCloseRelatives = closeRelativesIncludingMarriage(ron, 2);
        for (var person : ronCloseRelatives) {
            System.out.printf("%s%n",person);
        }
    }

    /**
     * It should be possible to remove the ancestors and descendants from
     * the PersonDistance value we get from "all close relatives", so we
     * don't repeat ourselves.
     */
    @Test
    public void subtractAncestorsAndDescendantsFromCloseRelatives() {
        PersonNode paul = SearchUtils.findExactlyOne(personNodes.values().stream(), x -> x.getName().contains("Paul Katz"));
        List<Relationship> paulCloseRelatives = closeRelativesIncludingMarriage(paul, 3);
        List<Relationship> paulDescendants = descendants(paul, 3);
        List<Relationship> paulAncestors = ancestors(paul, 3);
        paulCloseRelatives.removeAll(paulDescendants);
        paulCloseRelatives.removeAll(paulAncestors);
        for (var person : paulCloseRelatives) {
            System.out.printf("%s%n",person);
        }
    }

    /**
     * This is a test to see whether I can determine just the blood relatives
     * of a person.
     */
    @Test
    public void onlyBloodRelatives() {
        PersonNode ron = SearchUtils.findExactlyOne(personNodes.values().stream(), x -> x.getName().contains("Ron"));
        List<Relationship> ronBloodRelatives = closeRelativesExcludingMarriage(ron, 3);
        List<Relationship> removedSome = ronBloodRelatives.stream().filter(x -> !x.relationDescription().contains("parent of child")).toList();
        for (var person : removedSome) {
            System.out.printf("%s%n",person);
        }
    }

    /**
     * It will be a common occurrence to update the graph
     * once it is built.  every new person created, every update,
     * every deletion of a person, will cause an impact on
     * the graph.
     * <br>
     * Fortunately, that is like the easiest thing in the world.  all
     * we have to do is basically convert the relations fields to proper
     * validated connections and then add that personNode to the map.
     * done and done.
     */
    @Test
    public void testUpdateExistingGraph() {
        // creating a new person who has just one relation - his brother is Byron
        PersonFile personFile = new PersonFile(
                1L,
                UUID.fromString("faeb2550-4c4a-4f41-abef-d64dc89dbf20"),
                "","new person", Date.EMPTY, Date.EMPTY,
                "<a href='person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610'>Byron</a>", "", "", "", "", "", "", Gender.UNKNOWN, null, ""
                );

        PersonNode newNode = FamilyGraph.createNodeWithConnections(personFile, personFiles, personNodes);
        assertTrue(! newNode.getConnections().isEmpty());
        assertTrue(newNode.getConnections().stream().anyMatch(x -> x.getValue().getName().equals("Byron Katz")));
        assertFalse(newNode.getConnections().stream().anyMatch(x -> x.getValue().getName().equals("Elysa Katz")));

        // updating a person's relations
        PersonFile personFileUpdated = new PersonFile(
                1L,
                UUID.fromString("faeb2550-4c4a-4f41-abef-d64dc89dbf20"),
                "","new person", Date.EMPTY, Date.EMPTY,
                "<a href='person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610'>Byron</a>" +
                "<a href='person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b'>Elysa</a>",
                "", "", "", "", "", "", Gender.UNKNOWN, null, ""
        );
        personNodes.remove(personFileUpdated.getId());
        PersonNode updatedNode = FamilyGraph.createNodeWithConnections(personFileUpdated, personFiles, personNodes);
        assertTrue(! updatedNode.getConnections().isEmpty());
        assertTrue(updatedNode.getConnections().stream().anyMatch(x -> x.getValue().getName().equals("Byron Katz")));
        assertTrue(updatedNode.getConnections().stream().anyMatch(x -> x.getValue().getName().equals("Elysa Katz")));

        // deleting a person
        personNodes.remove(personFileUpdated.getId());
        assertFalse(personNodes.containsKey(personFileUpdated.getId()));
    }

    /**
     * Not much of a test - really just a convenient handle into the code in case
     * I want to dig deeper.
     */
    @Test
    public void testSiblings() {
        var result = FamilyGraph.siblings(personNodes.values().stream().filter(x -> x.getName().equals("Herbert Blumberg")).findFirst().orElseThrow());
        assertEquals(result.get(0).relationDescription(), "sister of Herbert Blumberg");
    }



    private void addPersonFilesTestData() {
        var ellis = new PersonFile(1L, UUID.fromString("83fe56ff-1607-4057-8cb0-31f62ccb930f"), "photo?name=b6c3eab6-de63-321e-b24b-30c46dc3626f.jpg",
                "Ellis Katz", Date.extractDate("1921-11-21"), Date.extractDate("2020-03-12"), "Florence", "<a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>", "Robert and Ethel",
                "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>, <a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a>, and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(ellis);

        var marjorie = new PersonFile(2L, UUID.fromString("ab1e7835-e6df-49ac-8492-9cf8b1686d7d"), "photo?name=e0b61d10-2f1a-3d55-be20-a20c4da121f4.jpg", "Marjorie Katz",
                Date.extractDate("1925-02-03"), Date.extractDate("2020-07-13"), "<a href=\"person?id=5eb1d7a5-09bd-45c9-b5d8-7c0510f0a359\">Herbert Blumberg</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a>", "Louis and Leah",
                "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>, <a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a>, and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(marjorie);

        var marc = new PersonFile(3L, UUID.fromString("dbab1ed7-4147-4187-85b5-9029081b9cef"), "", "Marc Blumberg",
                Date.EMPTY, Date.EMPTY, "Frank and Greg",
                "", "<a href=\"person?id=5eb1d7a5-09bd-45c9-b5d8-7c0510f0a359\">Herbert Blumberg</a> and Babette",
                "",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(marc);

        var herbert = new PersonFile(3L, UUID.fromString("5eb1d7a5-09bd-45c9-b5d8-7c0510f0a359"), "", "Herbert Blumberg",
                Date.EMPTY, Date.EMPTY, "<a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie</a>",
                "", "Louis and Leah",
                "<a href=\"person?id=dbab1ed7-4147-4187-85b5-9029081b9cef\">Marc Blumberg</a>",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(herbert);

        var ron = new PersonFile(3L, UUID.fromString("ee3df413-1d4a-41a9-9d40-b597e2d8849e"), "", "Ron Katz",
                Date.EMPTY, Date.EMPTY, "<a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a> and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a> and <a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>",
                "<a href=\"person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610\">Byron</a> <a href=\"person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b\">Elysa</a>",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(ron);

        var susan = new PersonFile(3L, UUID.fromString("de4f411f-17aa-49d4-8879-071280d5b319"), "", "Susan Katz",
                Date.EMPTY, Date.EMPTY, "Gary",
                "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>", "<a href=\"person?id=4faec4bc-5993-496e-806d-a402a142f2ec\">Margie Sylvia Evensky Goodman</a> and <a href=\"person?id=b3cd0fe1-0322-4e71-8010-8eaf6d7d9333\">Louis Harold Goodman</a>",
                "<a href=\"person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610\">Byron</a> and <a href=\"person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b\">Elysa</a>",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(susan);

        var dan = new PersonFile(4L, UUID.fromString("f0306481-691b-4dd2-a807-2f1784abd509"), "", "Dan Katz",
                Date.EMPTY, Date.EMPTY, "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a> and <a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>",
                "<a href=\"person?id=a5e8e11c-26d2-484f-954d-8cc001330f10\">Michelle</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a> and <a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>",
                "Erica and Joelle",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(dan);

        var michelle = new PersonFile(5L, UUID.fromString("a5e8e11c-26d2-484f-954d-8cc001330f10"), "", "Michelle Katz",
                Date.EMPTY, Date.EMPTY, "",
                "<a href=person?id=f0306481-691b-4dd2-a807-2f1784abd509>Dan</a>", "",
                "Erica and Joelle",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(michelle);

        var byron = new PersonFile(6L, UUID.fromString("c2b45e41-a46f-4cbf-965d-4f4fcf01d610"), "", "Byron Katz",
                Date.EMPTY, Date.EMPTY, "<a href=\"person?id=ebb6f61d-20eb-421f-967a-71e0bfd7cf9b\">Elysa</a>",
                "<a href=\"person?id=84503aed-3941-480f-a768-b30a8b7f2097\">Susanne</a>", "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and <a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a>",
                "Cameron David and Corey",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(byron);

        var elysa = new PersonFile(7L, UUID.fromString("ebb6f61d-20eb-421f-967a-71e0bfd7cf9b"), "", "Elysa Katz",
                Date.EMPTY, Date.EMPTY, "<a href=person?id=c2b45e41-a46f-4cbf-965d-4f4fcf01d610>Byron</a>",
                "Dan", "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a> and <a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a>",
                "Nathan Viven and Andrew",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(elysa);

        var paul = new PersonFile(8L, UUID.fromString("0a75a5e4-8a30-4119-9f80-10b90211b0f1"), "", "Paul Katz",
                Date.EMPTY, Date.EMPTY, "<a href=\"person?id=ee3df413-1d4a-41a9-9d40-b597e2d8849e\">Ron</a> and <a href=\"person?id=f0306481-691b-4dd2-a807-2f1784abd509\">Dan</a>",
                "<a href=\"person?id=ae28b8c2-b167-4fd0-9407-fec67d66c732\">Tina</a>", "<a href=\"person?id=83fe56ff-1607-4057-8cb0-31f62ccb930f\">Ellis Katz</a> and <a href=\"person?id=ab1e7835-e6df-49ac-8492-9cf8b1686d7d\">Marjorie Katz</a>",
                "",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(paul);

        var tina = new PersonFile(9L, UUID.fromString("ae28b8c2-b167-4fd0-9407-fec67d66c732"), "", "Tina Katz",
                Date.EMPTY, Date.EMPTY, "",
                "<a href=\"person?id=0a75a5e4-8a30-4119-9f80-10b90211b0f1\">Paul</a>", "Reva",
                "",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(tina);

        var margie = new PersonFile(10L, UUID.fromString("4faec4bc-5993-496e-806d-a402a142f2ec"), "", "Margie Sylvia Evensky Goodman",
                Date.EMPTY, Date.EMPTY, "",
                "<a href=\"person?id=b3cd0fe1-0322-4e71-8010-8eaf6d7d9333\">Louis Harold Goodman</a>", "",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and Gary",
                "", "", "", Gender.FEMALE, null, "");
        personFiles.add(margie);

        var louis = new PersonFile(11L, UUID.fromString("b3cd0fe1-0322-4e71-8010-8eaf6d7d9333"), "", "Louis Goodman",
                Date.EMPTY, Date.EMPTY, "",
                "<a href=\"person?id=4faec4bc-5993-496e-806d-a402a142f2ec\">Margie Sylvia Evensky Goodman</a>", "",
                "<a href=\"person?id=de4f411f-17aa-49d4-8879-071280d5b319\">Susan</a> and Gary",
                "", "", "", Gender.MALE, null, "");
        personFiles.add(louis);
    }

}
