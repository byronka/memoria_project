package com.renomad.inmra.featurelogic.persons;

import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.HtmlParser;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.utils.SearchUtils;

import java.util.*;
import java.util.function.Predicate;

import static com.renomad.inmra.featurelogic.persons.services.RelationshipTextService.getGenderedRelationshipString;

public class FamilyGraph {

    // create a parser and parse out the values from the familial relations
    static HtmlParser htmlParser = new HtmlParser();

    /**
     * Caching helper - gets a {@link PersonNode} from the cache, and
     * if needed, adds it first.
     * @return the {@link PersonNode} from the {@link Map}
     */
    public static PersonNode getPersonNode(UUID id, String name, Map<UUID, PersonNode> personNodes, Gender gender) {
        if (!personNodes.containsKey(id)) {
            personNodes.put(id, new PersonNode(id, name, gender));
        }

        return personNodes.get(id);
    }

    /**
     * Given a {@link PersonFile}, examine the text in each of its fields - siblings,
     * parents, and so on - and convert it to a PersonNode, which exists as a node in
     * a graph of family relationships.
     * <br>
     * adds this personNode to the map of personNodes.
     * <br>
     * Returns the fully built {@link PersonNode}
     * @throws FamilyGraphProcessingException thrown if any of the processing of text fails in any way.
     */
    public static PersonNode createNodeWithConnections(
            PersonFile personFile,
            Collection<PersonFile> personFiles,
            Map<UUID, PersonNode> personNodes) {

        // build the root node for person zero.  This is not a tree - it's a graph.
        // so as long as we can reach everyone, it doesn't really matter
        // where we start.
        //
        // Now, if there's a standalone person, they wouldn't get into the
        // graph.  We would know that if the person list size is different
        // than the graph node count

        try {
            var personNode = getPersonNode(personFile.getId(), personFile.getName(), personNodes, personFile.getGender());

            List<HtmlParseNode> parsedSiblings = htmlParser.parse(personFile.getSiblings()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedSiblings, personFiles, personNode, "sibling", personNodes);

            List<HtmlParseNode> parsedParents = htmlParser.parse(personFile.getParents()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedParents, personFiles, personNode, "parent", personNodes);

            List<HtmlParseNode> parsedSpouse = htmlParser.parse(personFile.getSpouses()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedSpouse, personFiles, personNode, "spouse", personNodes);

            List<HtmlParseNode> parsedChildren = htmlParser.parse(personFile.getChildren()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedChildren, personFiles, personNode, "child", personNodes);

            return personNode;
        } catch (Exception ex) {
            throw new FamilyGraphProcessingException(
                    String.format("Error: processing during createNodeWithConnections for person: %s personid: %s",
                            personFile.getName(), personFile.getId()), ex);
        }

    }

    /**
     * For each of the anchor ("a") elements we find in a person's data
     * field (e.g. their siblings, parents, and so on), look through all
     * the other persons - if the anchor element references a valid person,
     * add that link as a relation to the {@link PersonNode} connections.
     */
    private static void addVerifiedRelations(
            Collection<HtmlParseNode> parsedRelationHtmlNodes,
            Collection<PersonFile> personFiles,
            PersonNode personNode,
            String relation,
            Map<UUID, PersonNode> personNodes) {
        List<Map.Entry<String, PersonNode>> connections = personNode.getConnections();
        for (var personAnchorElement : parsedRelationHtmlNodes) {
            String hrefValue = personAnchorElement.getTagInfo().getAttribute("href");
            String personUuid = hrefValue.replace("person?id=", "");

            // now we have the UUID, check that person's details:
            PersonFile foundPerson = SearchUtils.findExactlyOne(personFiles.stream(), x -> x.getId().toString().equals(personUuid));
            if (foundPerson != null) {
                connections.add(Map.entry(relation, getPersonNode(foundPerson.getId(), foundPerson.getName(), personNodes, foundPerson.getGender())));
            }
        }
        personNode.setConnections(connections);
    }

    /**
     * A breadth-first search
     */
    public static void printGraph(PersonNode rootNode) {
        var queue = new ArrayDeque<PersonNode>();
        var seenSet = new HashSet<UUID>();
        seenSet.add(rootNode.getId());
        queue.add(rootNode); // add to the end of the queue

        while(!queue.isEmpty()) {
            PersonNode person = queue.poll();  // remove from the front of the queue
            System.out.println(person);
            for (var pn : person.getConnections()) {
                PersonNode p = pn.getValue();
                if (!seenSet.contains(p.getId())) {
                    seenSet.add(p.getId());
                    queue.add(p);
                }
            }
        }
    }

    public static List<Relationship> ancestors(PersonNode personNode, int maxRelationDistance) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> x.getKey().equals("parent")
        );
    }

    public static List<Relationship> siblings(PersonNode personNode) {
        var siblings = traverseFamilyGraph(
                personNode,
                1,
                x -> x.getKey().equals("sibling")
        );
        siblings.removeFirst();
        return siblings;
    }

    public static List<Relationship> descendants(PersonNode personNode, int maxRelationDistance) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> x.getKey().equals("child")
        );
    }

    /**
     * Collects relatives of all kinds by distance in relationship to the {@link PersonNode} provided
     * @param maxRelationDistance the count of the furthest ring of relationships we'll include.
     */
    public static List<Relationship> closeRelativesIncludingMarriage(PersonNode personNode, int maxRelationDistance) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> true
        );
    }

    /**
     * Collects relatives of all kinds by distance in relationship to the {@link PersonNode} provided
     * @param maxRelationDistance the count of the furthest ring of relationships we'll include.
     */
    public static List<Relationship> closeRelativesExcludingMarriage(PersonNode personNode, int maxRelationDistance) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> ! x.getKey().equals("spouse")
        );
    }

    public record Relationship(PersonNode personNode, String relationDescription, int distance){ }

    /**
     * This method lets us traverse the family graph from a given
     * starting node, a certain distance out, given certain predicate
     * @param personNode The starting node
     * @param maxRelationDistance How far out from the starting node we will go.  That is,
     *                            how many edges will we traverse.
     * @param personNodePredicate This {@link Predicate} is used to determine which edges we
     *                            will choose to explore on each node.  Typical examples
     *                            in this domain are "child", "spouse", "parent", and "sibling"
     */
    private static List<Relationship> traverseFamilyGraph(
            PersonNode personNode,
            int maxRelationDistance,
            Predicate<Map.Entry<String, PersonNode>> personNodePredicate
            ) {
        // if we got a null value for personNode, bail immediately
        if (personNode == null) return new ArrayList<>();

        // initialize some base values, we'll expand on this as we visit other
        // nodes in the graph
        List<Relationship> relationships = new ArrayList<>();
        // relationDistance of 0 is reserved for the starting person
        int relationDistance = 0;
        relationships.add(new Relationship(personNode, personNode.getName(), relationDistance));

        // prepare some data structures to assist with a breadth-first search.
        var queue = new ArrayDeque<Relationship>();
        var seenSet = new HashSet<UUID>();
        seenSet.add(personNode.getId());
        queue.add(relationships.getFirst());

        while(!queue.isEmpty()) {
            Relationship relationship = queue.poll();
            relationDistance = relationship.distance() + 1;
            if (relationDistance > maxRelationDistance) break;

            List<Map.Entry<String, PersonNode>> relationsToPerson = relationship
                    .personNode()
                    .getConnections()
                    .stream()
                    .filter(personNodePredicate)
                    .toList();

            for (Map.Entry<String, PersonNode> relationToPerson : relationsToPerson) {

                PersonNode p = relationToPerson.getValue();

                if (!seenSet.contains(p.getId())) {

                    // the string form of the relationship - "child", "spouse", etc.
                    String genderedRelationshipString = getGenderedRelationshipString(relationToPerson.getKey(), p.getGender());

                    Relationship myNewRelationship = new Relationship(p, genderedRelationshipString + " of " + relationship.relationDescription(), relationDistance);
                    relationships.add(myNewRelationship);
                    seenSet.add(p.getId());
                    queue.add(myNewRelationship);
                }
            }
        }
        return relationships;
    }



}
