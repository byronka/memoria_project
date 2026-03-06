package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.featurelogic.persons.services.ObscureInformationProcessor;
import com.renomad.inmra.featurelogic.persons.services.RelationInputs;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.VideoToPerson;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.htmlparsing.*;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.renomad.inmra.featurelogic.persons.services.RelationshipTextService.getGenderedRelationshipString;
import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.utils.StringUtils.safeAttr;

public class FamilyGraph {

    // create a parser and parse out the values from the familial relations
    static HtmlParser htmlParser = new HtmlParser();
    private final static Pattern imageRegex = Pattern.compile("<img.*?src=\"photo\\?name=(?<photo>.*?\\..+?)\"[^>]*>");
    private final static Pattern videoRegex = Pattern.compile("</video>");

    /**
     * Caching helper - gets a {@link PersonNode} from the cache, and
     * if needed, add it first.
     * @return the {@link PersonNode} from the {@link Map}
     */
    public static PersonNode getPersonNode(UUID id, String name, Map<UUID, PersonNode> personNodes, Gender gender, boolean isLiving) {
        if (!personNodes.containsKey(id)) {
            personNodes.put(id, new PersonNode(id, name, gender, isLiving));
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
            Collection<Person> persons,
            Map<UUID, PersonNode> personNodes,
            IPersonLruCache personLruCache) {

        // build the root node for person zero.  This is not a tree - it's a graph.
        // so as long as we can reach everyone, it doesn't really matter
        // where we start.
        //
        // Now, if there's a standalone person, they wouldn't get into the
        // graph.  We would know that if the person list size is different
        // than the graph node count

        try {
            boolean isLiving = ObscureInformationProcessor.isLiving(personFile.getBorn(), personFile.getDied(), LocalDate.now());
            var personNode = getPersonNode(personFile.getId(), personFile.getName(), personNodes, personFile.getGender(), isLiving);

            List<HtmlParseNode> parsedSiblings = htmlParser.parse(personFile.getSiblings()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedSiblings, persons, personNode, "sibling", personNodes, personLruCache);

            List<HtmlParseNode> parsedParents = htmlParser.parse(personFile.getParents()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedParents, persons, personNode, "parent", personNodes, personLruCache);

            List<HtmlParseNode> parsedSpouse = htmlParser.parse(personFile.getSpouses()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedSpouse, persons, personNode, "spouse", personNodes, personLruCache);

            List<HtmlParseNode> parsedChildren = htmlParser.parse(personFile.getChildren()).stream()
                    .filter(x -> x.getTagInfo().getTagName().equals(TagName.A)).toList();
            addVerifiedRelations(parsedChildren, persons, personNode, "child", personNodes, personLruCache);

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
            Collection<Person> persons,
            PersonNode personNode,
            String relation,
            Map<UUID, PersonNode> personNodes,
            IPersonLruCache personLruCache) {
        List<Map.Entry<String, PersonNode>> connections = personNode.getConnections();
        for (var personAnchorElement : parsedRelationHtmlNodes) {
            String hrefValue = personAnchorElement.getTagInfo().getAttribute("href");
            String personUuid = hrefValue.replace("person?id=", "");

            // now we have the UUID, check that person's details:
            Person foundPerson = SearchUtils.findExactlyOne(persons.stream(), x -> x.getId().toString().equals(personUuid));
            if (foundPerson != null) {
                PersonFile personFile = personLruCache.getCachedPersonFile(foundPerson.getId().toString());
                Gender gender = personFile.getGender();
                boolean isLiving = ObscureInformationProcessor.isLiving(personFile.getBorn(), personFile.getDied(), LocalDate.now());
                connections.add(Map.entry(relation, getPersonNode(foundPerson.getId(), foundPerson.getName(), personNodes, gender, isLiving)));
            }
        }
        personNode.setConnections(connections);
    }

    /**
     * A breadth-first walk of a graph from a given node.
     */
    public static List<PersonNode> walkGraph(PersonNode rootNode) {
        List<PersonNode> resultantNodeList = new ArrayList<>();
        var queue = new ArrayDeque<PersonNode>();
        var seenSet = new HashSet<UUID>();
        seenSet.add(rootNode.getId());
        queue.add(rootNode); // add to the end of the queue

        while(!queue.isEmpty()) {
            PersonNode person = queue.poll();  // remove from the front of the queue
            resultantNodeList.add(person);
            for (var pn : person.getConnections()) {
                PersonNode p = pn.getValue();
                if (!seenSet.contains(p.getId())) {
                    seenSet.add(p.getId());
                    queue.add(p);
                }
            }
        }
        return resultantNodeList;
    }

    /**
     * Get all ancestors of the person represented by personNode
     * @param personNode the originating person
     * @param maxRelationDistance the count of the furthest ring of relationships we'll include.
     * @param obscureStartingNode if this is true, the starting node is a living person and the user is
     *                            unauthenticated to see them
     */
    public static List<ShortRelationship> ancestors(PersonNode personNode, int maxRelationDistance, boolean obscureStartingNode) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> x.getKey().equals("parent"),
                obscureStartingNode,
                false
        );
    }

    /**
     * Get all siblings of the person represented by personNode
     * @param personNode the originating person
     * @param obscureStartingNode if this is true, the starting node is a living person and the user is
     *                            unauthenticated to see them
     */
    public static List<ShortRelationship> siblings(PersonNode personNode, boolean obscureStartingNode) {
        var siblings = traverseFamilyGraph(
                personNode,
                1,
                x -> x.getKey().equals("sibling"),
                obscureStartingNode,
                false
        );
        siblings.removeFirst();
        return siblings;
    }

    /**
     * Get all spouses of the person represented by personNode
     * @param personNode the originating person
     * @param obscureStartingNode if this is true, the starting node is a living person and the user is
     *                            unauthenticated to see them
     */
    public static List<ShortRelationship> spouses(PersonNode personNode, boolean obscureStartingNode) {
        var spouses = traverseFamilyGraph(
                personNode,
                1,
                x -> x.getKey().equals("spouse"),
                obscureStartingNode,
                false
        );
        spouses.removeFirst();
        return spouses;
    }

    /**
     * Get all descendants of the person represented by personNode
     * @param personNode the originating person
     * @param maxRelationDistance the count of the furthest ring of relationships we'll include.
     * @param obscureStartingNode if this is true, the starting node is a living person and the user is
     *                            unauthenticated to see them
     */
    public static List<ShortRelationship> descendants(PersonNode personNode, int maxRelationDistance, boolean obscureStartingNode) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> x.getKey().equals("child"),
                obscureStartingNode,
                false
        );
    }

    /**
     * Collects relatives of all kinds by distance in relationship to the {@link PersonNode} provided
     *
     * @param maxRelationDistance the count of the furthest ring of relationships we'll include.
     * @param obscureStartingNode if this is true, the starting node is a living person and the user is
     *                            unauthenticated to see them
     * @param useConciseTerms whether we want to convert strings like "sister of mother" to "aunt".  This
     *                        runs a heavy-handed string conversion with regex matchers, it isn't efficient,
     *                        so we want to only use it when necessary.
     */
    public static List<ShortRelationship> closeRelativesIncludingMarriage(PersonNode personNode, int maxRelationDistance, boolean obscureStartingNode, boolean useConciseTerms) {
        return traverseFamilyGraph(
                personNode,
                maxRelationDistance,
                x -> true,
                obscureStartingNode,
                useConciseTerms
        );
    }


    private static final Pattern siblingInLawPattern = Pattern.compile("((husband|wife|spouse) of (brother|sister|sibling)) of (.+$)");
    private static final Pattern siblingInLawPattern2 = Pattern.compile("((sister|brother|sibling) of (husband|wife|spouse)) of (.+$)");
    private static final Pattern childInLawPattern = Pattern.compile("((husband|wife|spouse) of (son|daughter|child)) of (.+$)");
    private static final Pattern parentInLawPattern = Pattern.compile("((mother|father|parent) of (wife|husband|spouse)) of (.+$)");
    private static final Pattern uncleAuntsPattern = Pattern.compile("((brother|sister) of (mother|father|parent)) of (.+$)");
    private static final Pattern grandparentsPattern = Pattern.compile("((mother|father|parent) of (mother|father|parent)) of (.+$)");
    private static final Pattern grandkidsPattern = Pattern.compile("((child|son|daughter) of (child|son|daughter)) of (.+$)");
    private static final Pattern nephewPattern = Pattern.compile("((child|son|daughter) of (brother|sister|sibling)) of (.+$)");
    private static final Pattern stepkidPattern = Pattern.compile("((child|son|daughter) of (husband|wife|spouse)) of (.+$)");
    private static final Pattern stepparentPattern = Pattern.compile("((wife|husband|spouse) of (mother|father|parent)) of (.+$)");
    private static final Pattern halfsiblingPattern = Pattern.compile("((son|daughter|child) of (mother|father|parent)) of (.+$)");

    /**
     * This method expect to occasionally encounter a pattern, along
     * the lines of "foo of foo of PERSON NAME".  It is anticipated
     * this data is the close relatives of a person.  We would like to
     * summarize that by adjusting it to something along the lines of:
     * "uncle of PERSON NAME. brother of father of PERSON NAME".
     */
    static String makeConcise(String relation) {
        Matcher siblingInLawMatcher = siblingInLawPattern.matcher(relation);
        Matcher siblingInLawMatcher2 = siblingInLawPattern2.matcher(relation);
        Matcher childInLawMatcher = childInLawPattern.matcher(relation);
        Matcher parentMatcher = parentInLawPattern.matcher(relation);
        Matcher uncleMatcher = uncleAuntsPattern.matcher(relation);
        Matcher grandparentMatcher = grandparentsPattern.matcher(relation);
        Matcher grandkidsMatcher = grandkidsPattern.matcher(relation);
        Matcher nephewMatcher = nephewPattern.matcher(relation);
        Matcher stepkidMatcher = stepkidPattern.matcher(relation);
        Matcher stepparentMatcher = stepparentPattern.matcher(relation);
        Matcher halfsiblingMatcher = halfsiblingPattern.matcher(relation);

        if (siblingInLawMatcher.matches()) {
            return switch (siblingInLawMatcher.group(1)) {
                case "wife of brother", "wife of sister", "wife of sibling" ->
                        "sister-in-law (%s)".formatted(siblingInLawMatcher.group(1));
                case "husband of brother", "husband of sister", "husband of sibling" -> "brother-in-law (%s)".formatted(siblingInLawMatcher.group(1));
                case "spouse of sister", "spouse of brother", "spouse of sibling" -> "sibling-in-law (%s)".formatted(siblingInLawMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + siblingInLawMatcher.group(0));
            };
        }
        
        if (siblingInLawMatcher2.matches()) {
            return switch (siblingInLawMatcher2.group(1)) {
                case "sister of husband", "sister of wife", "sister of spouse" ->
                        "sister-in-law (%s)".formatted(siblingInLawMatcher2.group(1));
                case "brother of husband", "brother of wife", "brother of spouse" -> "brother-in-law (%s)".formatted(siblingInLawMatcher2.group(1));
                case "sibling of husband", "sibling of wife", "sibling of spouse" -> "sibling-in-law (%s)".formatted(siblingInLawMatcher2.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + siblingInLawMatcher2.group(0));
            };
        }

        if (childInLawMatcher.matches()) {
            return switch (childInLawMatcher.group(1)) {
                case "husband of daughter", "husband of son", "husband of child" ->
                        "son-in-law (%s)".formatted(childInLawMatcher.group(1));
                case "wife of daughter", "wife of son", "wife of child" -> "daughter-in-law (%s)".formatted(childInLawMatcher.group(1));
                default -> relation;
            };
        }

        if (parentMatcher.matches()) {
            return switch (parentMatcher.group(1)) {
                case "mother of wife", "mother of husband", "mother of spouse" ->     "mother-in-law (%s)".formatted(parentMatcher.group(1));
                case "father of wife", "father of spouse", "father of husband" ->  "father-in-law (%s)".formatted   (parentMatcher.group(1));
                case "parent of wife", "parent of husband", "parent of spouse" ->  "parent-in-law (%s)".formatted   (parentMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + parentMatcher.group(0));
            };
        }


        if (uncleMatcher.matches()) {
            return switch (uncleMatcher.group(1)) {
                case "brother of mother", "brother of father", "brother of parent" ->     "uncle (%s)".formatted(uncleMatcher.group(1));
                case "sister of mother", "sister of father", "sister of parent" ->  "aunt (%s)".formatted       (uncleMatcher.group(1));
                default -> relation;
            };
        }

        if (grandparentMatcher.matches()) {
            return switch (grandparentMatcher.group(1)) {
                case "mother of mother", "mother of father", "mother of parent" ->     "grandmother (%s)".formatted(grandparentMatcher.group(1));
                case "father of mother", "father of father", "father of parent" ->  "grandfather (%s)".formatted   (grandparentMatcher.group(1));
                case "parent of mother", "parent of father", "parent of parent" ->  "grandparent (%s)".formatted   (grandparentMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + grandparentMatcher.group(0));
            };
        }

        if (grandkidsMatcher.matches()) {
            return switch (grandkidsMatcher.group(1)) {
                case "son of son", "son of daughter", "son of child" ->     "grandson (%s)".formatted                 (grandkidsMatcher.group(1));
                case "daughter of son", "daughter of daughter", "daughter of child" ->  "granddaughter (%s)".formatted(grandkidsMatcher.group(1));
                case "child of son", "child of daughter", "child of child" ->  "grandchild (%s)".formatted            (grandkidsMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + grandkidsMatcher.group(0));
            };
        }

        if (nephewMatcher.matches()) {
            return switch (nephewMatcher.group(1)) {
                case "son of brother", "son of sister", "son of sibling" ->     "nephew (%s)".formatted           (nephewMatcher.group(1));
                case "daughter of brother", "daughter of sister", "daughter of sibling" ->  "niece (%s)".formatted(nephewMatcher.group(1));
                default -> relation;
            };
        }

        if (stepkidMatcher.matches()) {
            return switch (stepkidMatcher.group(1)) {
                case "son of husband", "son of wife", "son of spouse" ->     "stepson (%s)".formatted                 (stepkidMatcher.group(1));
                case "daughter of husband", "daughter of wife", "daughter of spouse" ->  "stepdaughter (%s)".formatted(stepkidMatcher.group(1));
                case "child of husband", "child of wife", "child of spouse" ->  "stepchild (%s)".formatted            (stepkidMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + siblingInLawMatcher.group(0));
            };
        }
        
        if (stepparentMatcher.matches()) {
            return switch (stepparentMatcher.group(1)) {
                case "wife of father", "wife of mother", "wife of parent" ->     "stepmother (%s)".formatted      (stepparentMatcher.group(1));
                case "husband of father", "husband of mother", "husband of parent" ->  "stepfather (%s)".formatted(stepparentMatcher.group(1));
                case "spouse of father", "spouse of mother", "spouse of parent" ->  "stepparent (%s)".formatted   (stepparentMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + siblingInLawMatcher.group(0));
            };
        }
        
        if (halfsiblingMatcher.matches()) {
            return switch (halfsiblingMatcher.group(1)) {
                case "son of father", "son of mother", "son of parent" ->     "half-brother (%s)".formatted           (halfsiblingMatcher.group(1));
                case "daughter of father", "daughter of mother", "daughter of parent" ->  "half-sister (%s)".formatted(halfsiblingMatcher.group(1));
                case "child of father", "child of mother", "child of parent" ->  "half-sibling (%s)".formatted        (halfsiblingMatcher.group(1));
                default -> throw new IllegalStateException("Unexpected value: " + siblingInLawMatcher.group(0));
            };
        }
        

        return relation;
    }



    /**
     * Gets the extended blood relatives of a person.  This differs from the ancestors - it
     * gets the uncles, aunts, cousins, nephews, nieces.
     *
     * @param originatingPersonNode The person for whom we are finding extended family
     */
    public static List<UnclesAndCousins> findExtendedBloodRelations(PersonNode originatingPersonNode) {
        // get all the ancestors up to a point.
        int maxDistance = 5;
        List<ShortRelationship> ancestors = ancestors(originatingPersonNode, maxDistance, false);

        // sort the ancestors by distance, filtering out the person at distance 0, which is the originating person
        List<ShortRelationship> ancestorListSorted = ancestors.stream()
                .filter(x -> x.distance > 0)
                .sorted(Comparator.comparingInt(ShortRelationship::distance))
                .toList();

        int currentDistance = 1;
        List<UnclesAndCousins> unclesAndCousins = new ArrayList<>();
        HashSet<PersonNode> visitedPersons = new HashSet<>();
        visitedPersons.add(originatingPersonNode);
        Set<ShortRelationship> currentLevelUncles = new HashSet<>();
        Set<ShortRelationship> currentLevelCousins = new HashSet<>();
        for (ShortRelationship ancestor : ancestorListSorted) {
            // if we move outwards a level (parent to grandparent, etc), collect the data
            // captured so far and reset the uncle / cousin collections. Also, increment the
            // value of "currentDistance", which is how many levels up we've gone.
            if (ancestor.distance() != currentDistance) {
                unclesAndCousins.add(new UnclesAndCousins(currentLevelUncles, currentLevelCousins, currentDistance));
                currentDistance = ancestor.distance();
                currentLevelUncles = new HashSet<>();
                currentLevelCousins = new HashSet<>();
            }

            // keep track of who we've visited - we use this when traversing down
            // through children so we don't get people multiple times, and so we
            // exclusively find people off the direct ancestry tree.
            visitedPersons.add(ancestor.personNode());
            List<ShortRelationship> descendants = traverseFamilyGraph(
                    ancestor.personNode(),
                    currentDistance + 2,
                    x -> x.getKey().equals("child") && !visitedPersons.contains(x.getValue()),
                    true,
                    false
            );
            // the children one level down are the uncles and aunts (and siblings, in the first round) of the originating person.
            Set<ShortRelationship> uncles = descendants.stream().filter(x -> x.distance() == 1).collect(Collectors.toSet());
            // the children past one level down are the nieces/nephews and cousins.
            Set<ShortRelationship> cousins = descendants.stream().filter(x -> x.distance() > 1).collect(Collectors.toSet());
            currentLevelUncles.addAll(uncles);
            currentLevelCousins.addAll(cousins);
        }
        unclesAndCousins.add(new UnclesAndCousins(currentLevelUncles, currentLevelCousins, currentDistance));
        return unclesAndCousins;
    }

    public static String renderDescendantsShortHtml(PersonNode rootNode, IPersonLruCache personLruCache, boolean shouldShowPrivateInformation, ObscureInformationProcessor oip, String extraQueryStringForOtherPerson) {
        StringBuilder sb = new StringBuilder();
        innerRecursiveCallDescendantsShortHtml(rootNode, sb, 0, personLruCache, shouldShowPrivateInformation, oip, extraQueryStringForOtherPerson);
        return sb.toString();
    }

    private static void innerRecursiveCallDescendantsShortHtml(PersonNode node, StringBuilder sb, int depth,
                                                               IPersonLruCache personLruCache, boolean shouldShowPrivateInformation,
                                                               ObscureInformationProcessor oip, String extraQueryStringForOtherPerson) {
        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(node.getId().toString());
        String thumbnailHtml = "";
        boolean shouldObscureInformation = oip.shouldObscureInformation(cachedPersonFile.getBorn(), cachedPersonFile.getDied(), shouldShowPrivateInformation);
        List<PersonNode> children = node.getConnections().stream().filter(x -> x.getKey().equals("child")).map(Map.Entry::getValue).sorted(Comparator.comparing(PersonNode::getGender)).toList();

        if (!children.isEmpty()) {
            sb.append("<li><details open>");
        } else {
            sb.append("<li>");
        }
        if (!shouldObscureInformation) {
            String thumbnailUrl = safeAttr(cachedPersonFile.getImageUrl());
            boolean hasSpouse = node.getConnections().stream().anyMatch(x -> x.getKey().equals("spouse"));
            if (!thumbnailUrl.isBlank()) {
                thumbnailHtml = String.format("<img alt=\"\" loading=\"lazy\" class=\"tree-thumbnail\" src=\"%s&size=small\">", thumbnailUrl);
            }
            sb.append(String.format(
                    "<summary>%s<span><a href=\"person?id=%s%s\"><span class=\"name\">%s</span>%s</a></span>%s\n",
                    hasSpouse ? "<span class=\"parents-group\">" : "",
                    node.getId().toString(),
                    extraQueryStringForOtherPerson,
                    StringUtils.safeHtml(node.getName()),
                    thumbnailHtml,
                    hasSpouse ? "" : "</summary>"
                    ));
            if (hasSpouse) {
                Set<PersonNode> spouses = node.getConnections().stream().filter(x -> x.getKey().equals("spouse")).map(Map.Entry::getValue).collect(Collectors.toSet());
                for (PersonNode spouse : spouses) {
                    PersonFile cachedSpousePersonFile = personLruCache.getCachedPersonFile(spouse.getId().toString());
                    boolean shouldObscureSpouseInformation = oip.shouldObscureInformation(cachedSpousePersonFile.getBorn(), cachedSpousePersonFile.getDied(), shouldShowPrivateInformation);
                    if (shouldObscureSpouseInformation) {
                        sb.append(String.format(
                                "<span><a href=\"person?id=%s%s\"><span class=\"name\">Private <em>(spouse)</em></span></a></span>\n",
                                spouse.getId().toString(),
                                extraQueryStringForOtherPerson
                        ));
                    } else {
                        String spouseThumbnailUrl = safeAttr(cachedSpousePersonFile.getImageUrl());
                        String spouseThumbnailHtml = "";
                        if (!spouseThumbnailUrl.isBlank()) {
                            spouseThumbnailHtml = String.format("<img alt=\"\" loading=\"lazy\" class=\"tree-thumbnail\" src=\"%s&size=small\">", spouseThumbnailUrl);
                        }
                        sb.append(String.format(
                                "<span><a href=\"person?id=%s%s\"><span class=\"name\">%s <em>(spouse)</em></span>%s</a></span>\n",
                                spouse.getId().toString(),
                                extraQueryStringForOtherPerson,
                                StringUtils.safeHtml(spouse.getName()),
                                spouseThumbnailHtml
                        ));
                    }

                }
                sb.append("</span></summary>\n");
            }
        } else {
            sb.append(String.format(
                    "<summary><a href=\"person?id=%s%s\">%sPrivate</a></summary>\n",
                    node.getId().toString(),
                    extraQueryStringForOtherPerson,
                    thumbnailHtml)
            );
        }


        if (!children.isEmpty()) {
            sb.append("\n<ul>\n");
            for (PersonNode child : children) {
                innerRecursiveCallDescendantsShortHtml(child, sb, depth + 1, personLruCache, shouldShowPrivateInformation, oip, extraQueryStringForOtherPerson);
            }
            sb.append("</ul>\n");
        }
        if (!children.isEmpty()) {
            sb.append("</details></li>\n");
        } else {
            sb.append("</li>");
        }

    }


    public static String renderAncestorsShortHtml(PersonNode rootNode, IPersonLruCache personLruCache, boolean shouldShowPrivateInformation, ObscureInformationProcessor oip, String extraQueryStringForOtherPerson) {
        StringBuilder sb = new StringBuilder();
        innerRecursiveCallAncestorsShortHtml(rootNode, sb, 0, personLruCache, shouldShowPrivateInformation, oip, extraQueryStringForOtherPerson);
        return sb.toString();
    }

    private static void innerRecursiveCallAncestorsShortHtml(PersonNode node, StringBuilder sb, int depth,
                                                             IPersonLruCache personLruCache, boolean shouldShowPrivateInformation,
                                                             ObscureInformationProcessor oip, String extraQueryStringForOtherPerson) {
        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(node.getId().toString());
        String thumbnailHtml = "";
        boolean shouldObscureInformation = oip.shouldObscureInformation(cachedPersonFile.getBorn(), cachedPersonFile.getDied(), shouldShowPrivateInformation);
        if (!shouldObscureInformation) {
            String thumbnailUrl = safeAttr(cachedPersonFile.getImageUrl());
            if (!thumbnailUrl.isBlank()) {
                thumbnailHtml = String.format("<img alt=\"\" loading=\"lazy\" class=\"tree-thumbnail\" src=\"%s&size=small\">", thumbnailUrl);
            }
        }
        List<PersonNode> parents = node.getConnections().stream()
                .filter(x -> x.getKey().equals("parent")).map(Map.Entry::getValue).sorted(Comparator.comparing(PersonNode::getGender)).toList();

        if (!parents.isEmpty()) {
            sb.append("<li><details open>");
        } else {
            sb.append("<li>");
        }
        sb.append(String.format(
                "<summary><a href=\"person?id=%s%s\"><span class=\"name\">%s</span>%s</a></summary>\n",
                node.getId().toString(),
                extraQueryStringForOtherPerson,
                shouldObscureInformation ? "Private" : StringUtils.safeHtml(node.getName()),
                thumbnailHtml
                ));
        if (!parents.isEmpty()) {
            sb.append("<ul>\n");
            for (PersonNode parent : parents) {
                innerRecursiveCallAncestorsShortHtml(parent, sb, depth + 1, personLruCache, shouldShowPrivateInformation, oip, extraQueryStringForOtherPerson);
            }
            sb.append("</ul>\n");
        }
        if (!parents.isEmpty()) {
            sb.append("</details></li>\n");
        } else {
            sb.append("</li>\n");
        }

    }


    /**
     * Produce a concise sub-nested list of individuals with their descendants
     *
     * @param rootNode             where to start the descent
     * @param personNodeOrdinalMap a mapping of person to ordinal in the map
     */
    public static String renderPosterityShort(PersonNode rootNode, IPersonLruCache personLruCache, Map<PersonNode, Integer> personNodeOrdinalMap) {
        StringBuilder sb = new StringBuilder();
        innerRecursiveCallPosterityShort(rootNode, sb, 0, personLruCache, personNodeOrdinalMap);
        return sb.toString();
    }

    private static void innerRecursiveCallPosterityShort(
            PersonNode node, StringBuilder sb, int depth, IPersonLruCache personLruCache, Map<PersonNode, Integer> personNodeOrdinalMap) {

        sb.append("|    ".repeat(depth)).append(String.format("%s %s\n", personNodeOrdinalMap.get(node), StringUtils.safeHtml(node.getName())));
        boolean hasSpouse = node.getConnections().stream().anyMatch(x -> x.getKey().equals("spouse"));
        if (hasSpouse) {
            Set<PersonNode> spouses = node.getConnections().stream().filter(x -> x.getKey().equals("spouse")).map(Map.Entry::getValue).collect(Collectors.toSet());
            for (PersonNode spouse : spouses) {
                PersonFile cachedSpousePersonFile = personLruCache.getCachedPersonFile(spouse.getId().toString());
                String genderedSpouseWord = getGenderedRelationshipString("spouse", cachedSpousePersonFile.getGender());
                sb.append("|    ".repeat(depth)).append(String.format("%s: %s\n", genderedSpouseWord, StringUtils.safeHtml(cachedSpousePersonFile.getName())));
            }
        }
        List<PersonNode> children = node.getConnections().stream().filter(x -> x.getKey().equals("child")).map(Map.Entry::getValue).toList();
        if (!children.isEmpty()) {
            var sortedChildren = children.stream().sorted(Comparator.comparing(personNodeOrdinalMap::get)).toList();
            for (PersonNode child : sortedChildren) {
                innerRecursiveCallPosterityShort(child, sb, depth + 1, personLruCache, personNodeOrdinalMap);
            }
        }
    }

    /**
     * Renders a similar list to {@link #renderPosterityShort(PersonNode, IPersonLruCache, Map)} except
     * that it includes more detail per person and is not in a list format.
     *
     * @param personNodeOrdinalMap A mapping between persons and their ordinal in the tree, to enable us
     *                             to easily indicate, roughly, a distance in relationship to the original node
     */
    public static String renderPosterityLong(IPersonLruCache personLruCache, Map<PersonNode, Integer> personNodeOrdinalMap) {
        StringBuilder sb = new StringBuilder();
        List<Map.Entry<PersonNode, Integer>> sortedPersonsToList = personNodeOrdinalMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
        for (Map.Entry<PersonNode, Integer> entry : sortedPersonsToList) {
            printExpandedDescendantPersonDetails(entry.getKey(), sb, personLruCache, entry.getValue());
        }
        return sb.toString();
    }

    private static void printExpandedDescendantPersonDetails(PersonNode node, StringBuilder sb, IPersonLruCache personLruCache, int ordinal) {
        PersonFile personFile = personLruCache.getCachedPersonFile(node.getId().toString());
        String spouses = node.getConnections().stream().filter(x -> x.getKey().equals("spouse")).map(x -> StringUtils.safeHtml(x.getValue().getName())).collect(Collectors.joining(", "));
        String children = node.getConnections().stream().filter(x -> x.getKey().equals("child")).map(x -> StringUtils.safeHtml(x.getValue().getName())).collect(Collectors.joining(", "));

        sb.append("<div class=\"person-data\">\n");
        sb.append(String.format("""
                <div class="text-container">
                <p class="first-line"><span class="ordinal">%s</span>) <span class="name">%s</span></p>
                <ul>
                <li><span class="label">Gender:</span> %s</li>
                <li><span class="label">Birthdate:</span> %s</li>
                """, ordinal, StringUtils.safeHtml(node.getName()), personFile.getGender().toString().toLowerCase(), personFile.getBorn().getPrettyString()));
        if (!personFile.getDied().equals(Date.EMPTY)) {
            sb.append("<li><span class=\"label\">Deathdate:</span> ").append(personFile.getDied().getPrettyString()).append("</li>\n");
        }
        List<PersonFile.ExtraFieldTriple> extraFieldsAsList = personFile.getExtraFieldsAsList();
        extraFieldsAsList.sort(Comparator.comparing(PersonFile.ExtraFieldTriple::key));
        for (var extraField : extraFieldsAsList) {
            sb.append("<li><span class=\"label\">").append(extraField.key()).append(":</span> ").append(extraField.value()).append("</li>\n");
        }
        if (!spouses.isEmpty()) {
            sb.append("<li><span class=\"label\">Spouses:</span> ").append(spouses).append("</li>\n");
        }
        if (!children.isEmpty()) {
            sb.append("<li><span class=\"label\">Children:</span> ").append(children).append("</li>\n");
        }
        sb.append("</ul></div>\n");
        if (personFile.getImageUrl() != null && !personFile.getImageUrl().isBlank()) sb.append("<img class=\"person-thumbnail-image\" src=\"/").append(personFile.getImageUrl()).append("&size=small\">");
        sb.append("</div>\n\n");
    }

    public static String renderAncestryShort(PersonNode rootNode, Map<PersonNode, Integer> personNodeOrdinalMap) {
        StringBuilder sb = new StringBuilder();
        innerRecursiveCallAncestryShort(rootNode, sb, 0, personNodeOrdinalMap);
        return sb.toString();
    }

    /**
     * Used to calculate the breadth-first rendering of the family tree,
     * assigning ordinal values to each person encountered, so in a very
     * general way, the higher the number, the further distantly related.
     * Sorts by gender for ancestry and by birthdate for descendants
     * @param rootNode the person at which to start the breadth-first walk
     * @param relationship either "parent" for ancestors or "child" for descendants
     */
    public static Map<PersonNode, Integer> calculateOrdinals(PersonNode rootNode, String relationship, AbstractDb<Person> personDb) {
        Map<PersonNode, Integer> personNodeOrdinalMap = new HashMap<>();
        Set<PersonNode> seenPersons = new HashSet<>();
        Queue<PersonNode> personQueue = new LinkedList<>();
        personQueue.add(rootNode);
        int ordinal = 1;

        while(!personQueue.isEmpty()) {
            PersonNode currentPerson = personQueue.poll();

            personNodeOrdinalMap.put(currentPerson, ordinal);
            ordinal += 1;

            List<PersonNode> relations = currentPerson.getConnections().stream().filter(x -> x.getKey().equals(relationship)).map(Map.Entry::getValue).toList();

            if (!relations.isEmpty()) {

                List<PersonNode> sortedRelations = new ArrayList<>();
                if (relationship.equals("parent")) {
                    sortedRelations = relations.stream().sorted(Comparator.comparing(PersonNode::getGender)).toList();
                } else if (relationship.equals("child")) {
                    // if we are listing descendants, we will list them in order of birthdates.
                    // to do that, we have to get their birthdates off the Person database
                    ArrayList<Person> personsWithBirthdates = new ArrayList<>();
                    for (PersonNode personNode : relations) {
                        Person person = personDb.findExactlyOne("id", personNode.getId().toString());
                        personsWithBirthdates.add(person);
                    }
                    personsWithBirthdates.sort(Comparator.comparing(x -> x.getBirthday().toLocalDate().orElse(LocalDate.MIN)));

                    for (Person person : personsWithBirthdates) {
                        sortedRelations.add(SearchUtils.findExactlyOne(relations.stream(), x -> x.getId().equals(person.getId())));
                    }
                } else {
                    throw new FamilyGraphProcessingException("The calculateOrdinals method only takes a parameter of parent or child for the relationship");
                }

                for (PersonNode relation : sortedRelations) {
                    if (!seenPersons.contains(relation)) {
                        personQueue.add(relation);
                        seenPersons.add(relation);
                    }
                }
            }

        }

        return personNodeOrdinalMap;
    }

    private static void innerRecursiveCallAncestryShort(PersonNode node, StringBuilder sb, int depth, Map<PersonNode, Integer> personNodeOrdinalMap) {
        sb.append("|    ".repeat(depth)).append(String.format("%s %s\n", personNodeOrdinalMap.get(node), StringUtils.safeHtml(node.getName())));
        List<PersonNode> parents = node.getConnections().stream().filter(x -> x.getKey().equals("parent")).map(Map.Entry::getValue).toList();

        if (!parents.isEmpty()) {
            var sortedParents = parents.stream().sorted(Comparator.comparing(PersonNode::getGender)).toList();

            // now do the core logic, including recursion
            for (PersonNode parent : sortedParents) {
                innerRecursiveCallAncestryShort(parent, sb, depth + 1, personNodeOrdinalMap);
            }
        }
    }

    /**
     * Renders a similar list to {@link #renderPosterityShort} except
     * that it includes more detail per person.
     *
     * @param personNodeOrdinalMap a mapping between persons and their ordinal in the tree
     */
    public static String renderAncestryLong(IPersonLruCache personLruCache, Map<PersonNode, Integer> personNodeOrdinalMap) {
        StringBuilder sb = new StringBuilder();
        List<Map.Entry<PersonNode, Integer>> sortedPersonsToList = personNodeOrdinalMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
        for (Map.Entry<PersonNode, Integer> entry : sortedPersonsToList) {
            printExpandedAncestorPersonDetails(entry.getKey(), sb, personLruCache, entry.getValue());
        }
        return sb.toString();
    }

    private static void printExpandedAncestorPersonDetails(PersonNode node, StringBuilder sb, IPersonLruCache personLruCache, int ordinal) {
        PersonFile personFile = personLruCache.getCachedPersonFile(node.getId().toString());
        String spouses = node.getConnections().stream().filter(x -> x.getKey().equals("spouse")).map(x -> StringUtils.safeHtml(x.getValue().getName())).collect(Collectors.joining(", "));
        String parents = node.getConnections().stream().filter(x -> x.getKey().equals("parent")).map(x -> StringUtils.safeHtml(x.getValue().getName())).collect(Collectors.joining(", "));

        sb.append("<div class=\"person-data\">\n");
        sb.append(String.format("""
                <div class="text-container">
                <p class="first-line"><span class="ordinal">%s</span>) <span class="name">%s</span></p>
                <ul>
                <li><span class="label">Gender:</span> %s</li>
                <li><span class="label">Birthdate:</span> %s</li>
                """, ordinal, StringUtils.safeHtml(node.getName()), personFile.getGender().toString().toLowerCase(), personFile.getBorn().getPrettyString()));
        if (!personFile.getDied().equals(Date.EMPTY)) {
            sb.append("<li><span class=\"label\">Deathdate:</span> ").append(personFile.getDied().getPrettyString()).append("</li>\n");
        }
        for (var extraField : personFile.getExtraFieldsAsList()) {
            sb.append("<li><span class=\"label\">").append(extraField.key()).append(":</span> ").append(extraField.value()).append("</li>\n");
        }
        if (!spouses.isEmpty()) {
            sb.append("<li><span class=\"label\">Spouses:</span> ").append(spouses).append("</li>\n");
        }
        if (!parents.isEmpty()) {
            sb.append("<li><span class=\"label\">Parents:</span> ").append(parents).append("</li>\n");
        }
        sb.append("</ul></div>\n");
        if (personFile.getImageUrl() != null && !personFile.getImageUrl().isBlank()) sb.append("<img class=\"person-thumbnail-image\" src=\"/").append(personFile.getImageUrl()).append("&size=small\">");
        sb.append("</div>\n\n");
    }


    public record UnclesAndCousins(Set<ShortRelationship> uncles, Set<ShortRelationship> cousins, int currentDistance) {}



    /**
     * Build a list of relations between one person and another
     * Given a "target" of Paul and a "relative" of Louis, an example result
     * would be a list that eventually would indicate: "Louis, father of Susan, wife of Ron, brother of Paul Katz".
     * <br>
     * The underlying algorithm is a bi-directional breadth-first search.
     * @param seekBloodRelatives only search through parents
     */
    public static List<Relationship> findConnection(PersonNode target, PersonNode relative, boolean seekBloodRelatives) {
        ArrayDeque<PersonNode> leftQueue = new ArrayDeque<>();
        ArrayDeque<PersonNode> rightQueue = new ArrayDeque<>();
        leftQueue.add(target);
        List<Relationship> leftSR = new ArrayList<>();
        List<Relationship> rightSR = new ArrayList<>();
        rightQueue.add(relative);
        HashSet<UUID> leftSeenSet = new HashSet<>();
        HashSet<UUID> rightSeenSet = new HashSet<>();
        leftSeenSet.add(target.getId());
        rightSeenSet.add(relative.getId());
        UUID intersection = null;

        // the following is a label, we're using a kind of goto to
        // handle the complexity.
        outer:

        while(!leftQueue.isEmpty() || !rightQueue.isEmpty()) {
            PersonNode leftPerson = leftQueue.poll();
            PersonNode rightPerson = rightQueue.poll();

            if (leftPerson != null) {
                for (Map.Entry<String, PersonNode> connection : seekBloodRelatives ? leftPerson.getBloodConnections() : leftPerson.getConnections()) {
                    PersonNode p = connection.getValue();
                    if (!leftSeenSet.contains(p.getId())) {
                        leftSeenSet.add(p.getId());
                        leftQueue.add(p);
                        leftSR.add(new Relationship(leftPerson, connection.getKey(), p));
                        intersection = getIntersection(leftSeenSet, rightSeenSet);
                        if (intersection != null) break outer;
                    }
                }
            }
            if (rightPerson != null) {
                for (Map.Entry<String, PersonNode> connection : seekBloodRelatives ? rightPerson.getBloodConnections() : rightPerson.getConnections()) {
                    PersonNode p = connection.getValue();
                    if (!rightSeenSet.contains(p.getId())) {
                        rightSeenSet.add(p.getId());
                        rightQueue.add(p);
                        rightSR.add(new Relationship(p, getOpposite(connection.getKey()), rightPerson));
                        intersection = getIntersection(leftSeenSet, rightSeenSet);
                        if (intersection != null) break outer;
                    }
                }
            }
        }

        if (intersection != null) {
            UUID finalIntersection = intersection;

            // we found the collision between the two searches.  Now we have to assemble the whole path.
            // find the node marking the end of the relationship on the left side

            Relationship leftPointer = SearchUtils.findExactlyOne(leftSR.stream(), x -> x.relation().getId().equals(finalIntersection));
            // find the node marking the end of the relationship on the right side
            Relationship rightPointer = SearchUtils.findExactlyOne(rightSR.stream(), x -> x.person().getId().equals(finalIntersection));

            List<Relationship> leftSRs = new ArrayList<>();
            if (leftPointer != null) {
                leftSRs.add(leftPointer);
            }

            List<Relationship> rightSRs = new ArrayList<>();
            if (rightPointer != null) {
                rightSRs.add(rightPointer);
            }

            while (true) {
                if (leftPointer != null) {
                    Relationship finalLeftPointer = leftPointer;
                    leftPointer = SearchUtils.findExactlyOne(leftSR.stream(), x -> x.relation().getId().equals(finalLeftPointer.person().getId()));
                    leftSRs.add(leftPointer);
                } else {
                    break;
                }
            }
            while (true) {
                if (rightPointer != null) {
                    Relationship finalRightPointer = rightPointer;
                    rightPointer = SearchUtils.findExactlyOne(rightSR.stream(), x -> x.person().getId().equals(finalRightPointer.relation().getId()));
                    rightSRs.add(rightPointer);
                } else {
                    break;
                }
            }
            Collections.reverse(leftSRs);
            return Stream.concat(leftSRs.stream(), rightSRs.stream()).filter(Objects::nonNull).toList();
        }
        return List.of();
    }

    /**
     * This converts a list of relations of blood-related people to a
     * concise representation.  Instead of "father of father of father", you get "grandfather".
     * For example:
     * <ul>
     *     <li>parent parent child child (FEMALE) -> first cousin</li>
     *     <li>parent parent parent child (MALE) -> great-uncle</li>
     *     <li>parent parent parent parent child child child (FEMALE) -> second cousin, once-removed</li>
     * </ul>
     * @param relationships a list of relationships - this *must* be for blood-related persons,
     *                      as it is expecting to only see relationships between parents and children.
     * @return a string representing the concise relationship.
     */
    public static String printCosanguinity(List<Relationship> relationships) {
        StringBuilder sb = new StringBuilder(12);
        for (Relationship value : relationships) {
            String relationship = value.relationship();
            switch (relationship) {
                case "parent" -> sb.append('p');
                case "child" -> sb.append('c');
                default -> throw new FamilyGraphProcessingException("allowed relationships are parent or child");
            }
        }

        Gender gender = relationships.getLast().relation().getGender();

        // The code is just a string of p (parent) and c (child).  We can derive the
        // name of the relationship between two people purely on this code.  For example,
        // ccc is great-grandchild.  ppp is great-grandparent.  ppcc is first-cousin, and
        // pc is sibling. pppc is great-uncle/aunt.
        String code = sb.toString();

        // if the first item is a 'c', or "child", then all the
        // rest must be c as well, and this is a child or grandchild.
        if (code.charAt(0) == 'c') {
            if (code.length() == 1) {
                return switch(gender) {
                    case Gender.MALE -> "son";
                    case Gender.FEMALE -> "daughter";
                    case Gender.UNKNOWN -> "child";
                };
            } else if (code.length() == 2) {
                return switch(gender) {
                    case Gender.MALE -> "grandson";
                    case Gender.FEMALE -> "granddaughter";
                    case Gender.UNKNOWN -> "grandchild";
                };
            } else if (code.length() == 3) {
                return switch(gender) {
                    case Gender.MALE -> "great-grandson";
                    case Gender.FEMALE -> "great-granddaughter";
                    case Gender.UNKNOWN -> "great-grandchild";
                };
            } else {
                return switch(gender) {
                    case Gender.MALE -> "%dx gr-grandson".formatted(code.length() - 2);
                    case Gender.FEMALE -> "%dx gr-granddaughter".formatted(code.length() - 2);
                    case Gender.UNKNOWN -> "%dx gr-grandchild".formatted(code.length() - 2);
                };
            }
        } else {

            if (code.length() == 1) {
                return switch(gender) {
                    case Gender.MALE -> "father";
                    case Gender.FEMALE -> "mother";
                    case Gender.UNKNOWN -> "parent";
                };
            }

            // if the code is longer than one character and the first
            // char is a p (parent) and second is c (child), then the only
            // option is dependent on how many c's.
            if (code.charAt(1) == 'c') {
                if (code.length() == 2) {
                    return switch(gender) {
                        case Gender.MALE -> "brother";
                        case Gender.FEMALE -> "sister";
                        case Gender.UNKNOWN -> "sibling";
                    };
                } else if (code.length() == 3) {
                    return switch(gender) {
                        case Gender.MALE -> "nephew";
                        case Gender.FEMALE -> "niece";
                        case Gender.UNKNOWN -> "nephew/niece";
                    };
                } else if (code.length() == 4) {
                    return switch(gender) {
                        case Gender.MALE -> "great-nephew";
                        case Gender.FEMALE -> "great-niece";
                        case Gender.UNKNOWN -> "great-nephew/niece";
                    };
                } else {
                    return switch(gender) {
                        case Gender.MALE -> "%dx gr-nephew".formatted(code.length() - 3);
                        case Gender.FEMALE -> "%dx gr-niece".formatted(code.length() - 3);
                        case Gender.UNKNOWN -> "%dx gr-nephew/niece".formatted(code.length() - 3);
                    };
                }
            }

            // now we get to the tricky part.  What is left as possibilities here
            // are:
            //  1. all parents, meaning a grandparent
            //  2. all parents except for the last, meaning an uncle/aunt
            //  3. even balance of parents and child, meaning cousins
            // and then, dreadfully...
            //  3. varying balance of parents and child, meaning 2nd cousin once removed, etc.

            // Fortunately, at this point we know the length is greater than one, and
            // there's some p's (parents) at the start and potentially c's (children) after, so
            // we can just count them.

            int indexOfFirstChildCharacter = code.indexOf('c');

            // if there is no index of first 'c' (child) then we're dealing
            // with a code of all grandparents
            if (indexOfFirstChildCharacter == -1) {
                if (code.length() == 2) {
                    return switch(gender) {
                        case Gender.MALE -> "grandfather";
                        case Gender.FEMALE -> "grandmother";
                        case Gender.UNKNOWN -> "grandparent";
                    };
                } else if (code.length() == 3) {
                    return switch(gender) {
                        case Gender.MALE -> "great-grandfather";
                        case Gender.FEMALE -> "great-grandmother";
                        case Gender.UNKNOWN -> "great-grandparent";
                    };
                } else {
                    return switch(gender) {
                        case Gender.MALE -> "%dx gr-grandfather".formatted(code.length() - 2);
                        case Gender.FEMALE -> "%dx gr-grandmother".formatted(code.length() - 2);
                        case Gender.UNKNOWN -> "%dx gr-grandparent".formatted(code.length() - 2);
                    };
                }
            // if the first 'c' (child) is just the last character, then we know this is
            // an uncle/aunt
            } else if (indexOfFirstChildCharacter == code.length() - 1) {
                if (code.length() == 3) {
                    return switch(gender) {
                        case Gender.MALE -> "uncle";
                        case Gender.FEMALE -> "aunt";
                        case Gender.UNKNOWN -> "uncle/aunt";
                    };
                } else if (code.length() == 4) {
                    return switch(gender) {
                        case Gender.MALE -> "great-uncle";
                        case Gender.FEMALE -> "great-aunt";
                        case Gender.UNKNOWN -> "great-uncle/aunt";
                    };
                } else {
                    return switch(gender) {
                        case Gender.MALE -> "%dx gr-uncle".formatted(code.length() - 3);
                        case Gender.FEMALE -> "%dx gr-aunt".formatted(code.length() - 3);
                        case Gender.UNKNOWN -> "%dx gr-uncle/aunt".formatted(code.length() - 3);
                    };
                }
            // now we know there are multiple p's and multiple c's, so we just
            // look at the counts.  We are now to the point where the result is definitely
            // going to be a cousin of some kind.
            } else {
                int countOfInitialParents = indexOfFirstChildCharacter;
                int countOfSubsequentChildren = code.length() - indexOfFirstChildCharacter;
                StringBuilder cousinDescriber = new StringBuilder(20);

                /*
                ppcc -> 1st cousin
                pppcc -> 1st cousin once-removed
                ppccc -> 1st cousin once-removed
                ppppccc -> 2nd cousin once-removed
                ppppcc -> 1st cousin, twice-removed
                 */
                if (countOfSubsequentChildren < countOfInitialParents) {
                    int adjustedCousinRemovedValue = (countOfInitialParents - countOfSubsequentChildren);
                    int adjustedCousinValue = (countOfInitialParents - 1) - adjustedCousinRemovedValue;
                    cousinDescriber.append(convertToOrdinalForCousin(adjustedCousinValue)).append(" cousin ");
                    cousinDescriber.append(convertToRemovedOrdinal(adjustedCousinRemovedValue)).append(" removed");
                } else {
                    cousinDescriber.append(convertToOrdinalForCousin(countOfInitialParents - 1)).append(" cousin");
                    if (countOfSubsequentChildren > countOfInitialParents) {
                        cousinDescriber.append(" ").append(convertToRemovedOrdinal(countOfSubsequentChildren - countOfInitialParents)).append(" removed");
                    }
                }
                return cousinDescriber.toString();
            }
        }
    }

    /**
     * Converts 1 to "first", 2 to "second", etc., used
     * when describing cousins (e.g. first cousin)
     */
    private static String convertToOrdinalForCousin(int number) {
       return switch (number) {
           case 1 -> "first";
           case 2 -> "second";
           case 3 -> "third";
           case 4 -> "fourth";
           case 5 -> "fifth";
           case 6 -> "sixth";
           case 7 -> "seventh";
           case 8 -> "eighth";
           case 9 -> "ninth";
           case 10 -> "tenth";
           case 11 -> "eleventh";
           case 12 -> "twelfth";
           case 13 -> "thirteenth";
           case 14 -> "fourteenth";
           case 15 -> "fifteenth";
           default -> String.valueOf(number);
       };
    }

    /**
     * For use with cousins.  First cousin, "twice" removed.
     * or maybe "six times" removed.
     */
    private static String convertToRemovedOrdinal(int number) {
        return switch(number) {
          case 1 -> "once";
          case 2 -> "twice";
          case 3 -> "thrice";
          case 4 -> "four times";
          case 5 -> "five times";
          case 6 -> "six times";
          case 7 -> "seven times";
          case 8 -> "eight times";
          case 9 -> "nine times";
          case 10 -> "ten times";
          default -> "%d times".formatted(number);
        };
    }

    /**
     * Returns the intersection of two sets.
     */
    private static UUID getIntersection(HashSet<UUID> leftSeenSet, HashSet<UUID> rightSeenSet) {
        Set<UUID> intersection = leftSeenSet.stream()
                .filter(rightSeenSet::contains)
                .collect(Collectors.toSet());
        mustBeTrue(intersection.isEmpty() || intersection.size() == 1, "intersection must be no values or one value. Was " + intersection);
        return intersection.stream().findFirst().orElse(null);
    }

    /**
     * This will return the opposite relation.
     * child -> parent
     * parent -> child
     * sibling -> sibling
     * spouse -> spouse
     */
    private static String getOpposite(String key) {
        return switch (key) {
            case "child" -> "parent";
            case "parent" -> "child";
            default -> key;
        };
    }


    public record ShortRelationship(PersonNode personNode, String relationDescription, int distance){ }

    /**
     * This method lets us traverse the family graph from a given
     * starting node, a certain distance out, given certain predicate.
     * This is a breadth-first search.
     * @param personNode The starting node
     * @param maxRelationDistance How far out from the starting node we will go.  That is,
     *                            how many edges will we traverse.
     * @param personNodePredicate This {@link Predicate} is used to determine which edges we
     *                            will choose to explore on each node.  Typical examples
     *                            in this domain are "child", "spouse", "parent", and "sibling"
     * @param obscureStartingNode If this is true the starting node is a living person and thus
     *                            should have its name made private.
     * @param useConciseTerms whether to run the "makeConsise" method, which converts strings,
     *                        like "sister of mother", to "aunt".  This is necessary for the
     *                        detailed view renderer, but not the gettingOlderLoop which
     *                        just wants to know the sizes of different relationships.
     */
    private static List<ShortRelationship> traverseFamilyGraph(
            PersonNode personNode,
            int maxRelationDistance,
            Predicate<Map.Entry<String, PersonNode>> personNodePredicate,
            boolean obscureStartingNode,
            boolean useConciseTerms
            ) {
        // if we got a null value for personNode, bail immediately
        if (personNode == null) return new ArrayList<>();

        // initialize some base values, we'll expand on this as we visit other
        // nodes in the graph
        List<ShortRelationship> relationships = new ArrayList<>();
        // relationDistance of 0 is reserved for the starting person
        int relationDistance = 0;
        relationships.add(new ShortRelationship(personNode, obscureStartingNode ? "Private" : StringUtils.safeHtml(personNode.getName()), relationDistance));

        // prepare some data structures to assist with a breadth-first search.
        var queue = new ArrayDeque<ShortRelationship>();
        var seenSet = new HashSet<UUID>();
        seenSet.add(personNode.getId());
        queue.add(relationships.getFirst());

        while(!queue.isEmpty()) {
            ShortRelationship relationship = queue.poll();
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
                    String genderedRelationshipString = (p.isLiving() && obscureStartingNode) ? relationToPerson.getKey() : getGenderedRelationshipString(relationToPerson.getKey(), p.getGender());

                    ShortRelationship myNewRelationship;
                    if (useConciseTerms) {
                        myNewRelationship = new ShortRelationship(p, makeConcise(genderedRelationshipString + " of " + relationship.relationDescription()), relationDistance);
                    } else {
                        myNewRelationship = new ShortRelationship(p, genderedRelationshipString + " of " + relationship.relationDescription(), relationDistance);
                    }

                    relationships.add(myNewRelationship);
                    seenSet.add(p.getId());
                    queue.add(myNewRelationship);
                }
            }
        }
        return relationships;
    }

    public static PersonMetrics getPersonMetrics(PersonNode personNode, IPersonLruCache personLruCache, AbstractDb<PhotoToPerson> photoToPersonDb, AbstractDb<VideoToPerson> videoToPersonDb) {
        var personAncestors = ancestors(personNode, 99999, false);
        // number of ancestors of this person
        int countAncestors = personAncestors.size() - 1;
        var personDescendants = descendants(personNode, 9999, false);
        // number of descendants
        int countDescendants = personDescendants.size() - 1;
        PersonFile personFile = personLruCache.getCachedPersonFile(personNode.getId().toString());
        // how old they are in years
        long ageYears = -1;
        Date born = personFile.getBorn();
        Date died = personFile.getDied();
        if (! (born.equals(Date.EMPTY) || born.equals(Date.EXISTS_BUT_UNKNOWN))) {
            if (! (died.equals(Date.EMPTY) || died.equals(Date.EXISTS_BUT_UNKNOWN))) {
                ageYears = Date.calcYearsBetween(born, died);
            } else if (died.equals(Date.EMPTY)) {
                LocalDate now = LocalDate.now();
                var nowDate = new Date(now.getYear(), Month.getByOrdinal(now.getMonthValue()), now.getDayOfMonth());
                ageYears = Date.calcYearsBetween(born, nowDate);
            }
        }

        // get text of biography without html
        var parser = new HtmlParser();
        int countBioChars;
        String flattenedBioData = "";
        try {
            var parsedBio = parser.parse(personFile.getBiography());
            var parsedAuthBio = parser.parse(personFile.getAuthBio());
            String extractedData = innerText(parsedBio);
            extractedData += innerText(parsedAuthBio);
            // replace the line feeds with whitespace
            flattenedBioData = extractedData.replace("\n", " ").replace("\r", " ");
            // how many characters long their bio is.  Just a rough value - could vary with unicode
            countBioChars = flattenedBioData.length();
        } catch (ParsingException ex) {
            // if we can't parse the biography, just read its length
            countBioChars = personFile.getBiography().length();
            countBioChars += personFile.getAuthBio().length();
            flattenedBioData = personFile.getBiography();
            flattenedBioData += personFile.getAuthBio();
        }

        // how many photos this person has
        long countPhotos = photoToPersonDb.values().stream().filter(x -> x.getPersonIndex() == personFile.getIndex()).count();
        // how many videos this person has
        long countVideos = videoToPersonDb.values().stream().filter(x -> x.getPersonIndex() == personFile.getIndex()).count();
        var closeRelatives = FamilyGraph.closeRelativesIncludingMarriage(personNode, 2, false, false);
        // count of relatives closely related, including marriage
        int countCloseRelatives = closeRelatives.size() - 1;
        List<FamilyGraph.UnclesAndCousins> extendedBloodRelations = FamilyGraph.findExtendedBloodRelations(personNode);
        // count of nephews and nieces
        int countNephewsNieces = 0;
        if (! extendedBloodRelations.isEmpty()) {
            countNephewsNieces = extendedBloodRelations.getFirst().cousins().size();
        }
        // count of uncles and aunts
        int countUnclesAunts = 0;
        if (extendedBloodRelations.size() > 1) {
            countUnclesAunts = extendedBloodRelations.get(1).uncles().size();
        }
        // count of first cousins
        int countFirstCousin = 0;
        if (extendedBloodRelations.size() > 1) {
            countFirstCousin = extendedBloodRelations.get(1).cousins().size();
        }
        // the total count of all cousins
        int countCousins = 0;
        for (int i = 0; i < extendedBloodRelations.size(); i++) {
            if (i == 0) continue;
            countCousins += extendedBloodRelations.get(i).cousins().size();
        }
        // count of children
        long countChildren = personDescendants.stream().filter(x -> x.distance() == 1).count();
        var siblings = FamilyGraph.siblings(personNode, false);
        // count of siblings
        int countSiblings = siblings.size();
        var countSpouses = FamilyGraph.spouses(personNode, false).size();

        Matcher picMatcher = imageRegex.matcher(personFile.getBiography() + personFile.getAuthBio());
        long bioImageCount = picMatcher.results().count();
        // count of pics referenced in the video
        Matcher videoMatcher = videoRegex.matcher(personFile.getBiography() + personFile.getAuthBio());
        // count of videos in the bio
        long bioVideoCount = videoMatcher.results().count();

        // whether this person has a headshot
        boolean hasHeadshot = ! personFile.getImageUrl().isBlank();

        // get the entire relatives graph for this person, mainly to see whether they are
        // disconnected from the primary tree.  If they have a different size than everyone
        // else, they are disconnected.
        List<ShortRelationship> fullGraph = FamilyGraph.closeRelativesIncludingMarriage(personNode, 9999, false, false);
        int familyTreeSize = fullGraph.size();

        // size of the notes field for this person
        int notesCharCount = personFile.getNotes().length();

        // size of the summary biography
        int summaryCharCount = personFile.getAuthBio().length();

        return new PersonMetrics(
                0L,
                personNode.getName(),
                personNode.getId(),
                (int) bioImageCount,
                (int) bioVideoCount,
                (int) countPhotos,
                (int) countVideos,
                countSpouses,
                countSiblings,
                (int) countChildren,
                (int) ageYears,
                countBioChars,
                countCloseRelatives,
                countFirstCousin,
                countAncestors,
                countDescendants,
                countNephewsNieces,
                countUnclesAunts,
                born,
                died,
                flattenedBioData.substring(0, Math.min(countBioChars, 100)),
                personFile.getExtraFields(),
                hasHeadshot,
                familyTreeSize,
                notesCharCount,
                summaryCharCount,
                countCousins
        );
    }

    static String innerText(List<HtmlParseNode> innerContent) {
        if (innerContent == null) return "";
        if (innerContent.size() == 1 && innerContent.getFirst().getType() == ParseNodeType.CHARACTERS) {
            return innerContent.getFirst().getTextContent();
        } else {
            StringBuilder sb = new StringBuilder();
            for (HtmlParseNode hpn : innerContent) {
                List<String> data = hpn.print();
                for (String value : data) {
                    sb.append(" ").append(value.trim());
                }
            }
            return sb.toString().trim();
        }
    }


    public static void updateNode(PersonNode personNode, PersonFile newPersonFileData, Map<UUID, PersonNode> personNodes, Collection<Person> persons, IPersonLruCache personLruCache) {
        // create copy of list
        List<Map.Entry<String, PersonNode>> oldNodeConnections = personNode.getConnections().stream().toList();
        // delete the personNode from the map
        personNodes.remove(personNode.getId());
        // build a new personNode with the new data
        PersonNode newPersonNode = createNodeWithConnections(newPersonFileData, persons, personNodes, personLruCache);
        mustBeTrue(personNode.getId().equals(newPersonFileData.getId()), "person is being updated (not deleted) so id remains");
        // for each person who was connected to this person, adjust their connections to point at the new person node
        for (Map.Entry<String, PersonNode> connection : oldNodeConnections) {
            List<Map.Entry<String, PersonNode>> revisedConnections = new ArrayList<>();
            // get each relative's connections so we can revise them to point at the new node
            for (Map.Entry<String, PersonNode> relativeConnections : connection.getValue().getConnections()) {
                // if it was previously pointing at the old node, revise to point at new node
                if (relativeConnections.getValue().equals(personNode)) {
                    revisedConnections.add(Map.entry(relativeConnections.getKey(), newPersonNode));
                } else {
                    // otherwise, just add in what was previously there
                    revisedConnections.add(relativeConnections);
                }
            }
            connection.getValue().setConnections(revisedConnections);
        }
    }


    public static void deleteNode(PersonNode oldPersonNode, Map<UUID, PersonNode> personNodes) {
        // create copy of list
        List<Map.Entry<String, PersonNode>> oldNodeConnections = oldPersonNode.getConnections().stream().toList();
        // delete the personNode from the map
        personNodes.remove(oldPersonNode.getId());

        // for each person who was connected to this person, adjust their connections to point at the new person node
        for (Map.Entry<String, PersonNode> connection : oldNodeConnections) {
            List<Map.Entry<String, PersonNode>> revisedConnections = new ArrayList<>();
            // get each relative's connections so we can revise them to point at the new node
            for (Map.Entry<String, PersonNode> relativeConnections : connection.getValue().getConnections()) {
                // only include connections to relatives who aren't the old node
                if (!relativeConnections.getValue().equals(oldPersonNode)) {
                    revisedConnections.add(relativeConnections);
                }
            }
            connection.getValue().setConnections(revisedConnections);
        }
    }

    private final static Pattern personAnchorRegex = Pattern.compile("(<a href=\"?)((person\\?id=)(?<personid>[a-f0-9-]+)[^>]*>)(?<personname>[^<]*)(</a>)");

    private static Set<UUID> getSetOfIds(String relationLInks) {
        Matcher matcher = personAnchorRegex.matcher(relationLInks);
        HashSet<UUID> uuids = new HashSet<>();
        while(matcher.find()) {
            String personid = matcher.group("personid");
            UUID uuid = UUID.fromString(personid);
            uuids.add(uuid);
        }
        return uuids;
    }

    /**
     * Looks for invalid relationships which would cause a cycle.
     * Looks for children in the ancestry of the updating person, and
     * looks for parents in the posterity.
     * @param personIdBeingUpdated the id of the person getting updated
     * @param relationInputs a string consisting of anchor tags pointing at persons
     *                       who are relatives of the person.
     */
    public static void checkForCycle(String name, UUID personIdBeingUpdated, RelationInputs relationInputs, Map<UUID, PersonNode> personNodes) {
        String parentsLinks = relationInputs.parentInput();
        String childrenLinks = relationInputs.childInput();
        Set<UUID> parentIds = getSetOfIds(parentsLinks);
        Set<UUID> childrenIds = getSetOfIds(childrenLinks);

        // check each parent to see if any have the person as an ancestor
        for (UUID id : parentIds) {
            PersonNode parentPersonNode = personNodes.get(id);
            boolean isExistingAncestor = FamilyGraph.ancestors(parentPersonNode, 999, false).stream().anyMatch(x -> x.personNode().getId().equals(personIdBeingUpdated));
            if (isExistingAncestor) {
                throw new CircularLoopException(("%s is trying to make %s a parent, but is already an ancestor of theirs, " +
                        "which would be an illegal cycle in the tree").formatted(name, parentPersonNode.getName()));
            }
        }

        // check each child to see if any have the person as a descendant
        for (UUID id : childrenIds) {
            PersonNode childPersonNode = personNodes.get(id);
            boolean isExistingDescendant = FamilyGraph.descendants(childPersonNode, 999, false).stream().anyMatch(x -> x.personNode().getId().equals(personIdBeingUpdated));
            if (isExistingDescendant) {
                throw new CircularLoopException(("%s is trying to make %s a child, but is already a descendant of theirs, " +
                        "which would be an illegal cycle in the tree").formatted(name, childPersonNode.getName()));
            }
        }

    }


    /**
     * Looks for invalid relationships which would cause a cycle.
     * Looks for children in the ancestry of the updating person, and
     * looks for parents in the posterity.
     * @param name name of person being updated
     * @param personIdBeingUpdated the id of the person getting updated
     * @param relationshipLinks a string consisting of anchor tags pointing at persons
     *                       who are relatives of the person.
     * @param relationType either "parent" or "child" - the relationship between the person
     *                     being updated and the other person, from the perspective of the
     *                     person being updated.  The updatee is a ______ to the other person.
     */
    public static void checkForCycle(String name, UUID personIdBeingUpdated, String relationshipLinks, String relationType, Map<UUID, PersonNode> personNodes) {
        Set<UUID> relationIds = getSetOfIds(relationshipLinks);

        if (relationType.equals("child")) {
            // check each relation to see if any have the person as an ancestor
            for (UUID id : relationIds) {
                PersonNode parentPersonNode = personNodes.get(id);
                boolean isExistingAncestor = FamilyGraph.ancestors(parentPersonNode, 99999, false).stream().anyMatch(x -> x.personNode().getId().equals(personIdBeingUpdated));
                if (isExistingAncestor) {
                    throw new CircularLoopException(("Impending illegal cycle detected: %s is an existing ancestor of %s").formatted(name, parentPersonNode.getName()));
                }
            }
        } else if (relationType.equals("parent")) {
            // check each child to see if any have the person as a descendant
            for (UUID id : relationIds) {
                PersonNode childPersonNode = personNodes.get(id);
                boolean isExistingDescendant = FamilyGraph.descendants(childPersonNode, 99999, false).stream().anyMatch(x -> x.personNode().getId().equals(personIdBeingUpdated));
                if (isExistingDescendant) {
                    throw new CircularLoopException(("Impending illegal cycle detected: %s is an existing descendant of %s").formatted(name, childPersonNode.getName()));
                }
            }
        }
    }
}
