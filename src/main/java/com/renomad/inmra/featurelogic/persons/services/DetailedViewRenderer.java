package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.utils.Cleaners;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StringUtils;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.renomad.inmra.featurelogic.persons.FamilyGraph.*;
import static com.renomad.inmra.featurelogic.persons.services.RelationshipTextService.getGenderedRelationshipString;
import static com.renomad.minum.utils.StringUtils.safeAttr;
import static com.renomad.minum.utils.StringUtils.safeHtml;
import static java.util.stream.Collectors.groupingBy;

/**
 * This class is responsible for gathering and rendering
 * the detailed view of a person - that is, the view
 * most people see when they look up a particular person.
 */
public class DetailedViewRenderer {

    private final IPersonLruCache personLruCache;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final Lifespan lifespan;
    private final TemplateProcessor personDetailPageTemplateProcessor;
    private final TemplateProcessor personDetailPrintPageTemplateProcessor;
    private final TemplateProcessor otherPersonRelationshipTemplateProcessor;
    private final TemplateProcessor personAllRelativesPageTemplateProcessor;
    private final AbstractDb<Person> personDb;
    private final ILogger logger;
    private final Stats stats;
    /**
     * This intricate regex is so we can replace the name of a person with "private" quickly and cleanly
     * in any anchor elements that reference living people.  It is composed of three groups, so when
     * we run the replace command, we can replace it with matching groups: first + "private" + third
     */
    private final static Pattern personAnchorRegex = Pattern.compile("(<a href=\"?)((person\\?id=)(?<personid>[a-f0-9-]+)[^>]*>)(?<personname>[^<]*)(</a>)");
    private final static Pattern imageRegex = Pattern.compile("<img.*?src=\"photo\\?name=(?<photo>.*?\\..+?)\"[^>]*>");
    private final ObscureInformationProcessor oip;

    public DetailedViewRenderer(
            IFileUtils fileUtils,
            IPersonLruCache personLruCache,
            FamilyGraphBuilder familyGraphBuilder,
            Lifespan lifespan,
            AbstractDb<Person> personDb,
            ILogger logger,
            Stats stats) {

        this.personLruCache = personLruCache;
        this.familyGraphBuilder = familyGraphBuilder;
        this.lifespan = lifespan;
        personDetailPageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_detail_page.html"));
        personDetailPrintPageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_detail_page_printing.html"));
        personAllRelativesPageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_detail_page_all_relatives.html"));
        otherPersonRelationshipTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/relation_to_other_template.html"));
        this.personDb = personDb;
        this.logger = logger;
        this.stats = stats;
        this.oip = new ObscureInformationProcessor();
    }

    /**
     * Renders the detailed view of a person, with details necessary for the print view.  This is what typical users
     * get to see - the point of all our work.
     */
    public String renderPersonViewForPrint(PersonFile personFile) {

        var myMap = new HashMap<String, String>();
        myMap.put("id", safeAttr(personFile.getId().toString()));
        myMap.put("siblings", renderImmediateRelationsForPrinting(personFile.getSiblings()));
        myMap.put("spouses", renderImmediateRelationsForPrinting(personFile.getSpouses()));
        myMap.put("parents", renderImmediateRelationsForPrinting(personFile.getParents()));
        myMap.put("children",renderImmediateRelationsForPrinting(personFile.getChildren()));
        myMap.put("auth_biography", Cleaners.cleanScript(personFile.getAuthBio()));
        myMap.put("title_name", safeHtml(personFile.getName()));
        myMap.put("name", safeHtml(personFile.getName()));
        myMap.put("gender", personFile.getGender().serialize());
        myMap.put("biography", Cleaners.cleanScript(personFile.getBiography()));
        addImageToTemplate(personFile, myMap);
        addExtraFieldsToTemplate(personFile, myMap);
        String renderedLifespan = lifespan.renderLifespan(personFile);
        myMap.put("lifespan", renderedLifespan);
        return personDetailPrintPageTemplateProcessor.renderTemplate(myMap);
    }

    /**
     * Renders the detailed view of a person.  This is what typical users
     * get to see - the point of all our work.
     *
     * @param shouldShowPrivateInformation if this is false, we won't include certain information: name, gender, bio,
     *                                     photo_url, dates, extra information, and also hide the names of living people
     *                                     in the relatives lists (relationships, extended relatives, etc.)
     */
    public String renderPersonView(boolean isAuthenticated, PersonFile personFile, String navHeader,
                                   UUID otherPerson, boolean shouldShowPrivateInformation
                                  ) {
        var myMap = fillInTemplatePartially(personFile, otherPerson, shouldShowPrivateInformation);

        if (isAuthenticated) {
            // if we are viewing this person while authenticated, refresh the metrics for this person
            // and add a view showing the stats at the bottom of the page
            PersonNode personNode = familyGraphBuilder.getPersonNodes().get(personFile.getId());
            stats.rebuildMetricsForPerson(personNode, personLruCache);
            Person person = personDb.findExactlyOne("id", personFile.getId().toString());
            String statsRendered = stats.prepareStatsTemplate(person, personFile);
            myMap.put("stats", statsRendered);
        } else {
            myMap.put("stats", "");
        }
        // get the PersonNode for this person, as a first step to calculating their relatives
        // in the family graph.
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                familyGraphBuilder.getPersonNodes().values().stream(), x -> x.getId().equals(personFile.getId()));
        boolean obscureStartingNode = oip.shouldObscureInformation(personFile.getBorn(), personFile.getDied(), shouldShowPrivateInformation);

        // the relations is the list of relations between this and some other relation.
        List<Relationship> relations = addRelationToOther(personFile, familyGraphBuilder.getPersonNodes(), myMap, otherPerson, shouldShowPrivateInformation, false);
        Set<ShortRelationship> setOfCloseRelatives = new HashSet<>(obscureIfRequired(closeRelativesIncludingMarriage(myPersonNode, 2, obscureStartingNode, true), shouldShowPrivateInformation));

        // convert relations to set of ShortRelationships
        if (!relations.isEmpty()) {
            setOfCloseRelatives.addAll(relations.stream().map(x -> new ShortRelationship(x.relation(), "", 0)).toList());
            setOfCloseRelatives.add(new ShortRelationship(relations.getFirst().person(), "", 0));
        }

        addCloseRelativeDataForPreview(myMap, setOfCloseRelatives, shouldShowPrivateInformation);

        if (otherPerson == null || !otherPerson.equals(personFile.getId())) {
            myMap.put("relation_to_other_person_link", String.format("<a class=\"select-for-relation\" href=\"/person?id=%s&oid=%s\">Select</a>",
                    personFile.getId(),
                    personFile.getId()));
        } else {
            myMap.put("relation_to_other_person_link", "<div>This person is set as the target relation</div> <a class=\"select-for-relation\" href=\"/person?id="+personFile.getId()+"\">Unselect</a>");
        }

        String oidQueryString = "";
        if (otherPerson != null) {
            oidQueryString = "&oid=" + otherPerson;
        }

        myMap.put("navigation_header", navHeader);

        if (shouldShowPrivateInformation) {
            String cleanedBio = adjustBiographyText(personFile.getAuthBio(), shouldShowPrivateInformation);
            myMap.put("auth_biography", cleanedBio );
        } else {
            myMap.put("auth_biography", "");
        }

        if (oip.shouldObscureInformation(personFile.getBorn(), personFile.getDied(), shouldShowPrivateInformation)) {
            // if the person's info is redacted, show a login link near the name field, with enough information
            // for sending the user back to this spot after authentication.
            myMap.put("name", "Private");
            myMap.put("title_name", "Private");
            myMap.put("gender", "");
            myMap.put("biography", "");
            myMap.put("person_image", "");
            myMap.put("extra_fields", "");
            myMap.put("lifespan", "");
            myMap.put("link_to_extended_relatives", "");
        } else {
            myMap.put("title_name", safeHtml(personFile.getName()));
            myMap.put("name", safeHtml(personFile.getName()));
            myMap.put("gender", personFile.getGender().serialize());
            myMap.put("biography", adjustBiographyText(personFile.getBiography(), shouldShowPrivateInformation));
            addImageToTemplate(personFile, myMap);
            addExtraFieldsToTemplate(personFile, myMap);
            String renderedLifespan = lifespan.renderLifespan(personFile);
            myMap.put("lifespan", renderedLifespan);
            myMap.put("link_to_extended_relatives", String.format("""

                        <div id="extended_relatives_link_list_item">
                            <a title="Expand list of relatives" href="/person-all?id=%s%s">Extended relatives</a>
                        </div>
                
                    """.stripLeading(), personFile.getId(), oidQueryString));
        }
        return personDetailPageTemplateProcessor.renderTemplate(myMap);
    }

    /**
     * This method returns the relatives of a person who aren't direct
     * ancestors or descendants, but are related by blood (except when
     * a person has been adopted, it is still counted as a blood relative,
     * since that lineage led to adopting a child)
     * @param otherPerson UUID of another person, which we will tack onto the
     *                    URL for each rendered person to continue finding connections.
     *                    May be null, and in fact commonly will be, when we are
     *                    not showing connections between two people.
     */
    String renderUnclesAndCousinsToString(UUID otherPerson, boolean shouldShowPrivateInformation, List<UnclesAndCousins> unclesAndCousins) {
        StringBuilder sb = new StringBuilder();
        int level = 0;
        for (UnclesAndCousins uac : unclesAndCousins) {
            addUncles(otherPerson, shouldShowPrivateInformation, uac, sb, level);
            addCousins(otherPerson, shouldShowPrivateInformation, uac, sb, level);

            level += 1;
        }
        return sb.toString();
    }

    private void addCousins(UUID otherPerson, boolean shouldShowPrivateInformation, UnclesAndCousins uac, StringBuilder sb, int level) {
        sb.append("<li>");
        sb.append("<span class=\"label extended-family relative-type-label\">");
        switch (level){
            case 0: sb.append("Nephews and nieces: "); break;
            case 1: sb.append("Children of uncles and aunts: "); break;
            case 2: sb.append("Children of great-uncles/aunts: "); break;
            case 3: sb.append("Children of 2 x great-uncles/aunts:"); break;
            case 4: sb.append("Children of 3 x great-uncles/aunts:"); break;
        }
        sb.append("</span>");
        sb.append("</li>");
        sb.append("<ul>");
         /*
         * A list of cousins - at index 0, they are at your generation. At index 1,
         * they are once-removed, and so on.
         */
        Map<Integer, List<ShortRelationship>> cousinsByDistanceRemoved = uac.cousins().stream().collect(groupingBy(ShortRelationship::distance));
        for (var cousinsLevel : cousinsByDistanceRemoved.entrySet()) {
            String collectedCousins = cousinsLevel.getValue().stream().map(x -> {
                PersonFile cousin = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                int offsetFromGeneration = Math.abs(x.distance() - level - 1);

                String image = "";
                if (!cousin.getImageUrl().isBlank()) {
                    image = String.format("<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"%s&amp;size=icon\">&nbsp;", cousin.getImageUrl());
                }
                // if they are living, and the user is not authenticated, replace their name with "private"
                if (oip.shouldObscureInformation(cousin.getBorn(), cousin.getDied(), shouldShowPrivateInformation)) {
                    if (otherPerson != null) {
                        return String.format("<a href=\"person?id=%s&oid=%s\">Private</a>", cousin.getId(), otherPerson);
                    } else {
                        return String.format("<a href=\"person?id=%s\">Private</a>", cousin.getId());
                    }
                } else {
                    if (otherPerson != null) {
                        return String.format("<a class=\"offset-%d\" href=\"person?id=%s&oid=%s\">%s%s</a>", offsetFromGeneration, cousin.getId(), otherPerson, image, cousin.getName());
                    } else {
                        return String.format("<a class=\"offset-%d\" href=\"person?id=%s\">%s%s</a>",  offsetFromGeneration, cousin.getId(), image, cousin.getName());
                    }
                }

            }).sorted().collect(Collectors.joining("\n"));

            sb.append("<li>");
            // don't show the whole "once-removed" thing for nephews and nieces
            if (level > 0) {
                int offsetFromGeneration = cousinsLevel.getKey() - level - 1;
                if (level == 2 && offsetFromGeneration == -1) {
                    sb.append("first cousin, once removed ").append(makeTooltip("", "second")).append(": ");
                } else if (level == 3 && offsetFromGeneration == -1) {
                    sb.append("second cousin, once removed ").append(makeTooltip("2 x", "third")).append(": ");
                } else if (level == 3 && offsetFromGeneration == -2) {
                    sb.append("first cousin, twice removed ").append(makeTooltip("2 x", "third")).append(": ");
                } else if (level == 4 && offsetFromGeneration == -1) {
                    sb.append("third cousin, once removed ").append(makeTooltip("3 x", "fourth")).append(": ");
                } else if (level == 4 && offsetFromGeneration == -2) {
                    sb.append("second cousin, twice removed ").append(makeTooltip("3 x", "fourth")).append(": ");
                } else if (level == 4 && offsetFromGeneration == -3) {
                    sb.append("first cousin, three times removed ").append(makeTooltip("3 x", "fourth")).append(": ");
                } else {
                    switch (level) {
                        case 1 -> sb.append("first cousin, ");
                        case 2 -> sb.append("second cousin, ");
                        case 3 -> sb.append("third cousin, ");
                        case 4 -> sb.append("fourth cousin, ");
                    }
                    switch (offsetFromGeneration) {
                        case 0 -> sb.append("same-generation: ");
                        case 1 -> sb.append("once-removed: ");
                        case 2 -> sb.append("twice-removed: ");
                        case 3 -> sb.append("thrice-removed: ");
                    }
                }

            }
            if (collectedCousins.isBlank()) {
                sb.append("(None found)");
            } else {
                sb.append(collectedCousins);
            }
            sb.append("</li>");
        }
        sb.append("</ul>");
    }

    private static String makeTooltip(String xGreat, String level) {
        return String.format("""
                <span class="showTooltip" data-tooltip="children of %s great-uncles/aunts are %s cousins, except for the older generations, so there is symmetry between relations">
                    ℹ️
                </span>
                """, xGreat, level);
    }

    private void addUncles(UUID otherPerson, boolean shouldShowPrivateInformation, UnclesAndCousins uac, StringBuilder sb, int level) {
        if (level == 0) return;

        sb.append("<li>");

        sb.append("<span class=\"label extended-family relative-type-label\">");
        switch (level){
            case 0: sb.append("Siblings: "); break;
            case 1: sb.append("Uncles and aunts: "); break;
            case 2: sb.append("Great-uncles and aunts: "); break;
            case 3: sb.append("Gr-gr-uncles and aunts: "); break;
            case 4: sb.append("Gr-gr-gr-uncles and aunts: "); break;
        }
        sb.append("</span>");

        String collectedUncles = uac.uncles().stream().map(x -> {
            PersonFile personFile1 = personLruCache.getCachedPersonFile(x.personNode().getId().toString());

            String image = "";
            if (!personFile1.getImageUrl().isBlank()) {
                image = String.format("<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"%s&amp;size=icon\">&nbsp;", personFile1.getImageUrl());
            }

            // if they are living, and the user is not authenticated, replace their name with "private"
            if (oip.shouldObscureInformation(personFile1.getBorn(), personFile1.getDied(), shouldShowPrivateInformation)) {
                if (otherPerson != null) {
                    return String.format("<a href=\"person?id=%s&oid=%s\">Private</a>", x.personNode().getId(), otherPerson);
                } else {
                    return String.format("<a href=\"person?id=%s\">Private</a>", x.personNode().getId());
                }
            } else {
                if (otherPerson != null) {
                    return String.format("<a href=\"person?id=%s&oid=%s\">%s%s</a>", x.personNode().getId(), otherPerson, image, x.personNode().getName());
                } else {
                    return String.format("<a href=\"person?id=%s\">%s%s</a>", x.personNode().getId(), image, x.personNode().getName());
                }
            }

        }).sorted().collect(Collectors.joining("\n"));
        if (collectedUncles.isBlank()) {
            sb.append("(None found)");
        } else {
            sb.append(collectedUncles);
        }
        sb.append("</li>");
    }


    /**
     * The biography text is the content discussing aspects of this person's
     * life - their history.  It has some special needs - we need to remove
     * script elements, and we need to wrap img elements with a elements that
     * point to the original-size image.  If the user is authenticated to see
     * living people, we'll show them the archival photo.
     */
    private static String adjustBiographyText(String biography, boolean shouldShowPrivateInformation) {
        String cleanedBio = Cleaners.cleanScript(biography);
        Matcher matcher = imageRegex.matcher(cleanedBio);
        if (shouldShowPrivateInformation) {
            return matcher.replaceAll("<a href=\"photo?name=$1&size=archive\">$0</a>");
        } else {
            return matcher.replaceAll("<a href=\"photo?name=$1&size=original\">$0</a>");
        }
    }

    /**
     * This is focused on showing all the relatives of a person, but does not show the biography.
     */
    public String renderPersonViewAllRelatives(PersonFile personFile, String navHeader, UUID otherPerson, boolean shouldShowPrivateInformation) {
        var myMap = new HashMap<String, String>();
        myMap.put("id", personFile.getId().toString());
        myMap.put("oid", otherPerson == null ? "" : "&oid=" + otherPerson);
        myMap.put("siblings", renderImmediateRelations(personFile.getSiblings(), otherPerson, shouldShowPrivateInformation));
        myMap.put("spouses", renderImmediateRelations(personFile.getSpouses(), otherPerson, shouldShowPrivateInformation));
        myMap.put("parents", renderImmediateRelations(personFile.getParents(), otherPerson, shouldShowPrivateInformation));
        myMap.put("children",renderImmediateRelations(personFile.getChildren(), otherPerson, shouldShowPrivateInformation));

        // get the PersonNode for this person, as a first step to calculating their relatives
        // in the family graph.
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                familyGraphBuilder.getPersonNodes().values().stream(), x -> x.getId().equals(personFile.getId()));
        boolean obscureStartingNode = oip.shouldObscureInformation(personFile.getBorn(), personFile.getDied(), shouldShowPrivateInformation);

        // get extended family members - blood relatives, but not direct
        // ancestors or descendants.
        List<UnclesAndCousins> unclesAndCousins = findExtendedBloodRelations(myPersonNode);
        String bloodRelatives = renderUnclesAndCousinsToString(otherPerson, shouldShowPrivateInformation, unclesAndCousins);
        myMap.put("blood_relatives", bloodRelatives);

        // the relations is the list of relations between this and some other relation.
        List<Relationship> relations = addRelationToOther(personFile, familyGraphBuilder.getPersonNodes(), myMap, otherPerson, shouldShowPrivateInformation, true);

        // add relatives like cousins and uncles
        List<ShortRelationship> crim = obscureIfRequired(closeRelativesIncludingMarriage(myPersonNode, 2, obscureStartingNode, true), shouldShowPrivateInformation);
        if (!crim.isEmpty()) {
            crim.removeFirst();
        }
        Set<ShortRelationship> crimSet = new HashSet<>(crim);
        Set<ShortRelationship> allRelations = new HashSet<>(crimSet);

        int distance = 1;
        for (UnclesAndCousins uac : unclesAndCousins) {
            int finalDistance = distance;
            allRelations.addAll(uac.uncles().stream()
                    .filter(x -> allRelations.stream().noneMatch(y -> x.personNode().getId().equals(y.personNode().getId())))
                    .map(x -> new ShortRelationship(x.personNode(), "", finalDistance)).toList());
            allRelations.addAll(uac.cousins().stream()
                    .filter(x -> allRelations.stream().noneMatch(y -> x.personNode().getId().equals(y.personNode().getId())))
                    .map(x -> new ShortRelationship(x.personNode(), "", finalDistance)).toList());
            distance += 1;
        }

        String extraQueryStringForOtherPerson = getExtraQueryStringForOtherPerson(otherPerson);
        addCloseRelativesToMap(myMap, crimSet, shouldShowPrivateInformation, extraQueryStringForOtherPerson);

        // add ancestors and descendants
        String descendantsTreeGraphic = FamilyGraph.renderDescendantsShortHtml(myPersonNode, personLruCache, shouldShowPrivateInformation, oip, extraQueryStringForOtherPerson);
        myMap.put("descendants_tree_graphic", descendantsTreeGraphic);
        String ancestorsTreeGraphic = FamilyGraph.renderAncestorsShortHtml(myPersonNode, personLruCache, shouldShowPrivateInformation, oip, extraQueryStringForOtherPerson);
        myMap.put("ancestors_tree_graphic", ancestorsTreeGraphic);
        Set<ShortRelationship> descendants = addDescendants(myMap, myPersonNode, otherPerson, shouldShowPrivateInformation, obscureStartingNode);
        Set<ShortRelationship> ancestors = addAncestors(myMap, myPersonNode, otherPerson, shouldShowPrivateInformation, obscureStartingNode);
        allRelations.addAll(descendants);
        allRelations.addAll(ancestors);

        // convert relations to set of ShortRelationships
        if (!relations.isEmpty()) {
            allRelations.addAll(relations.stream().map(x -> new ShortRelationship(x.relation(), "", 0)).toList());
            allRelations.add(new ShortRelationship(relations.getFirst().person(), "", 0));
        }

        addCloseRelativeDataForPreview(myMap, allRelations, shouldShowPrivateInformation);

        if (otherPerson == null || !otherPerson.equals(personFile.getId())) {
            myMap.put("relation_to_other_person_link", String.format("<a class=\"select-for-relation\" href=\"/person-all?id=%s&oid=%s\">Select</a>",
                    personFile.getId(),
                    personFile.getId()));
        } else {
            myMap.put("relation_to_other_person_link", "<div>This person is set as the target relation</div> <a class=\"select-for-relation\" href=\"/person-all?id="+personFile.getId()+"\">Unselect</a>");
        }
        myMap.put("navigation_header", navHeader);

        if (oip.shouldObscureInformation(personFile.getBorn(), personFile.getDied(), shouldShowPrivateInformation)) {
            // if the person's info is redacted, show a login link near the name field, with enough information
            // for sending the user back to this spot after authentication.
            myMap.put("name", "Private");
            myMap.put("title_name", "Private");
            myMap.put("person_image", "");
            myMap.put("lifespan", "");
        } else {
            myMap.put("title_name", safeHtml(personFile.getName()));
            myMap.put("name", safeHtml(personFile.getName()));
            addImageToTemplate(personFile, myMap);
            String renderedLifespan = lifespan.renderLifespan(personFile);
            myMap.put("lifespan", renderedLifespan);
        }

        if (shouldShowPrivateInformation) {
            myMap.put("printable_descendants", "<a id=\"descendants-printable-link\" href=\"descendants_printable?id="+ personFile.getId()+"\">Printable version</a>");
            myMap.put("printable_ancestors", "<a id=\"ancestors-printable-link\"  href=\"ancestors_printable?id="+ personFile.getId()+"\">Printable version</a>");
        } else {
            myMap.put("printable_descendants", "");
            myMap.put("printable_ancestors", "");
        }

        return personAllRelativesPageTemplateProcessor.renderTemplate(myMap);
    }

    /**
     * This finds the relationship between two people and renders it to a
     * string fit for display on the person detail page
     * @param isExtendedFamilyView whether looking at the normal view or
     *                 the extended family view.  Used for creating a "reset" functionality
     *                 to stop showing the relationship.
     */
    private List<Relationship> addRelationToOther(
            PersonFile personFile,
            Map<UUID, PersonNode> personNodes,
            Map<String, String> myMap,
            UUID other,
            boolean shouldShowPrivateInformation,
            boolean isExtendedFamilyView) {
        // if the id of the other is null or equal to the id of the originating person, then
        // we can just set the relationship to empty string.
        if (other == null || other.equals(personFile.getId())) {
            myMap.put("relationship", "");
            return List.of();
        } else {
            PersonNode personNode = personNodes.get(personFile.getId());
            PersonNode otherPerson = personNodes.get(other);

            if (personNode == null) {
                logger.logAsyncError(() -> "Unable to find a personNode for " + personFile + ". Returning empty list");
                myMap.put("relationship", "");
                return List.of();
            }
            if (otherPerson == null) {
                logger.logAsyncError(() -> "Unable to find " + other + " in personNodes array.  Returning empty list.");
                myMap.put("relationship", "");
                return List.of();
            }

            String relationsString = "";
            List<Relationship> relations = findConnection(otherPerson, personNode, true);

            // if the relations is empty here, it means we couldn't find a relation between
            // the selected persons by parents. In that case we will indicate the persons
            // don't seem to be blood related, and show how they are related through the
            // tree, including through marriage.
            if (relations.isEmpty()) {
                relations = findConnection(otherPerson, personNode, false);
                if (!relations.isEmpty()) {
                    relationsString = "<em>The selected persons are not blood related.</em> ";
                }
            } else {
                if (shouldShowPrivateInformation || (!otherPerson.isLiving() && !personNode.isLiving())) {
                    relationsString += "%s is %s's ".formatted(safeHtml(personNode.getName()), safeHtml(otherPerson.getName())) +
                            printCosanguinity(relations) + ". ";
                }

            }
            relationsString += printRelationships(relations, other, shouldShowPrivateInformation);

            String relationRendered = otherPersonRelationshipTemplateProcessor.renderTemplate(Map.of(
                    "other_person_name", (!shouldShowPrivateInformation && otherPerson.isLiving()) ? "Private" : safeHtml(otherPerson.getName()),
                    "relation_to_other", relationsString,
                    "reset_page_link", isExtendedFamilyView ? "/person-all?id=" + personFile.getId() : "/person?id=" + personFile.getId()
            ));
            myMap.put("relationship", relationRendered);
            return relations;
        }

    }

    /**
     * This generates the relationship between two people - the person for this page,
     * and the person we are currently targeting as a relation
     */
    public String printRelationships(List<Relationship> relationships, UUID other, boolean shouldShowPrivateInformation) {
        if (relationships.isEmpty()) {
            return "No relationship found";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = relationships.size()-1; i >= 0; i--) {
            PersonNode relation = relationships.get(i).relation();
            sb.append(convertToHtml(relation, other, shouldShowPrivateInformation));
            sb.append("<span>");
            sb.append(", the ");
            String genderedRelationship = (relation.isLiving() &! shouldShowPrivateInformation) ? relationships.get(i).relationship() : getGenderedRelationshipString(relationships.get(i).relationship(), relation.getGender());
            sb.append(genderedRelationship).append(" of ");
            sb.append("</span>");
        }
        sb.append(convertToHtml(relationships.getFirst().person(), other, shouldShowPrivateInformation));
        return sb.toString();
    }

    private String convertToHtml(PersonNode relation, UUID other, boolean shouldShowPrivateInformation) {

        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(relation.getId().toString());
        String thumbnailHtml = "";
        String name = "";
        if (oip.shouldObscureInformation(cachedPersonFile.getBorn(), cachedPersonFile.getDied(), shouldShowPrivateInformation)) {
            name = "Private";
        } else {
            String thumbnailUrl = safeAttr(cachedPersonFile.getImageUrl());
            if (!thumbnailUrl.isBlank()) {
                thumbnailHtml = String.format("<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"%s&size=icon\">", thumbnailUrl);
            }
            name = StringUtils.safeHtml(relation.getName());
        }

        return String.format(
                "<a href=\"person?id=%s&oid=%s\">%s&nbsp;%s</a>",
                relation.getId().toString(),
                other,
                thumbnailHtml,
                name
        );
    }

    /**
     * Returns the list of descendants of a person.
     */
    private Set<ShortRelationship> addDescendants(Map<String, String> myMap,
                                                  PersonNode myPersonNode, UUID otherPerson, boolean shouldShowPrivateInformation, boolean obscureStartingNode) {
        List<ShortRelationship> myDescendants = obscureIfRequired(descendants(myPersonNode, 999, obscureStartingNode), shouldShowPrivateInformation);

        // group the ancestors by distance
        Map<Integer, Set<ShortRelationship>> results = new HashMap<>();
        for(var relationship : myDescendants) {
            if (!results.containsKey(relationship.distance())) {
                results.put(relationship.distance(), new HashSet<>());
            }
            results.get(relationship.distance()).add(relationship);
        }

        // if there is a person to set a relation to, we'll create the string for adding
        // to the anchor tag here.
        String extraQueryStringForOtherPerson = getExtraQueryStringForOtherPerson(otherPerson);

        StringBuilder renderedNodes = renderFamilyNodes(results, extraQueryStringForOtherPerson, shouldShowPrivateInformation, Direction.DESCENDANT);
        myMap.put("descendants", renderedNodes.toString());
        return new HashSet<>(myDescendants);
    }

    /**
     * Returns the list of ancestors of a person.
     */
    private Set<ShortRelationship> addAncestors(Map<String, String> myMap,
                                                PersonNode myPersonNode, UUID otherPerson, boolean shouldShowPrivateInformation, boolean obscureStartingNode) {
        List<ShortRelationship> myAncestors = obscureIfRequired(ancestors(myPersonNode, 999, obscureStartingNode), shouldShowPrivateInformation);

        // group the ancestors by distance
        Map<Integer, Set<ShortRelationship>> results = new HashMap<>();
        for(var relationship : myAncestors) {
            if (!results.containsKey(relationship.distance())) {
                results.put(relationship.distance(), new HashSet<>());
            }
            results.get(relationship.distance()).add(relationship);
        }

        // if there is a person to set a relation to, we'll create the string for adding
        // to the anchor tag here.
        String extraQueryStringForOtherPerson = getExtraQueryStringForOtherPerson(otherPerson);


        StringBuilder renderedNodes = renderFamilyNodes(results, extraQueryStringForOtherPerson, shouldShowPrivateInformation, Direction.ANCESTOR);

        myMap.put("ancestors", renderedNodes.toString());
        return new HashSet<>(myAncestors);
    }

    /**
     * Adjust the data to hide private information, if necessary. per a boolean (shouldShowPrivateInformation)
     */
    private List<ShortRelationship> obscureIfRequired(List<ShortRelationship> relationships, boolean shouldShowPrivateInformation) {
        List<ShortRelationship> adjustedRelationships = new ArrayList<>();
        for (int i = 0; i < relationships.size(); i++) {
            ShortRelationship shortRelationship = relationships.get(i);
            PersonNode personNode = shortRelationship.personNode();
            PersonFile personFile = personLruCache.getCachedPersonFile(personNode.getId().toString());
            boolean shouldObscureInformation = oip.shouldObscureInformation(personFile.getBorn(), personFile.getDied(), shouldShowPrivateInformation);
            if (shouldObscureInformation) {
                var adjustedShortRelationship = new ShortRelationship(
                        new PersonNode(personNode.getId(), "Private", Gender.UNKNOWN, true),
                        shortRelationship.relationDescription(),
                        shortRelationship.distance()
                );
                adjustedRelationships.add(adjustedShortRelationship);
            } else {
                adjustedRelationships.add(shortRelationship);
            }
        }
        return adjustedRelationships;
    }

    enum Direction {
        ANCESTOR,
        DESCENDANT
    }

    private StringBuilder renderFamilyNodes(Map<Integer, Set<ShortRelationship>> results, String extraQueryStringForOtherPerson, boolean shouldShowPrivateInformation, Direction direction) {
        // render the results, simply, separated by divs
        StringBuilder renderedNodes = new StringBuilder();
        for (var distance : results.keySet()) {
            renderedNodes.append("\n<li>\n");
            Set<ShortRelationship> personNodesAtDistance = results.get(distance);
            String renderedPersonNodes = personNodesAtDistance.stream()
                    .map(x -> {
                        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                        String thumbnailHtml = "<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"/general/hot-air-balloon.png\">&nbsp;";
                        if (!oip.shouldObscureInformation(cachedPersonFile.getBorn(), cachedPersonFile.getDied(), shouldShowPrivateInformation)) {
                            String thumbnailUrl = safeAttr(cachedPersonFile.getImageUrl());
                            if (!thumbnailUrl.isBlank()) {
                                thumbnailHtml = String.format("<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"%s\">&nbsp;", thumbnailUrl);
                            } else {
                                thumbnailHtml = switch (cachedPersonFile.getGender()) {
                                    case MALE -> "<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"/general/man.png\">&nbsp;";
                                    case FEMALE -> "<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"/general/woman.png\">&nbsp;";
                                    default -> "<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"/general/hot-air-balloon.png\">&nbsp;";
                                };
                            }
                        }

                        return String.format(
                                "<a href=\"person?id=%s%s\">%s%s</a>",
                                x.personNode().getId().toString(),
                                extraQueryStringForOtherPerson,
                                thumbnailHtml,
                                StringUtils.safeHtml(x.personNode().getName())
                        );
                    })
                    .sorted()
                    .collect(Collectors.joining("\n"));

            String relationName = "";
            switch(distance) {
                case 0: relationName = "self:"; break;
                case 1: relationName = direction.equals(Direction.ANCESTOR) ? "parents:" : "children:"; break;
                case 2: relationName = direction.equals(Direction.ANCESTOR) ?  "grandparents:" : "grandchildren:"; break;
                case 3: relationName = direction.equals(Direction.ANCESTOR) ?  "great-grandparents:" : "great-grandchildren:"; break;
                default: relationName = direction.equals(Direction.ANCESTOR) ? "great("+(distance-2)+"x)-grandparents:" : "great("+(distance-2)+"x)-grandchildren:";
            }
            renderedNodes
                    .append("\n<span class=\"distance\">").append(relationName).append("</span>")
                    .append("<div>")
                    .append(renderedPersonNodes)
                    .append("</div>")
                    .append("\n</li>\n");
        }
        return renderedNodes;
    }

    private static String getExtraQueryStringForOtherPerson(UUID otherPerson) {
        // if there is a person to set a relation to, we'll create the string for adding
        // to the anchor tag here.
        String extraQueryStringForOtherPerson;
        if (otherPerson != null) {
            extraQueryStringForOtherPerson = "&oid="+ otherPerson;
        } else {
            extraQueryStringForOtherPerson = "";
        }
        return extraQueryStringForOtherPerson;
    }

    /**
     * Adds the list of close relatives (including marriage) to the map for the
     * extended-family view of a person
     * @param closeRelatives the list of close relatives
     * @param shouldShowPrivateInformation true if the user is authenticated
     * @param extraQueryStringForOtherPerson a url for another person to whom we will calculate the connecting relations
     */
    private void addCloseRelativesToMap(Map<String, String> myMap, Set<ShortRelationship> closeRelatives, boolean shouldShowPrivateInformation, String extraQueryStringForOtherPerson) {
        // we'll only keep the outer layer of relatives - the inner layer is already shown on the page.
        Set<ShortRelationship> adjustedCloseRelatives = closeRelatives.stream().filter(x -> x.distance() == 2).collect(Collectors.toSet());
        myMap.put("relatives", adjustedCloseRelatives
                .stream()
                .sorted(Comparator.comparing(ShortRelationship::distance).thenComparing(x -> x.personNode().getName()))

                .map(x -> {
                    PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                    String thumbnailUrl = "";
                    String thumbnailHtml = "";
                    String name = "";
                    String relationDescription = "";
                    if (oip.shouldObscureInformation(cachedPersonFile.getBorn(), cachedPersonFile.getDied(), shouldShowPrivateInformation)) {
                        thumbnailHtml = "";
                        name = "Private";
                    } else {
                        thumbnailUrl = safeAttr(cachedPersonFile.getImageUrl());
                        thumbnailHtml = "";
                        if (!thumbnailUrl.isBlank()) {
                            thumbnailHtml = String.format("<img alt=\"\" loading=\"lazy\" class=\"thumbnail\" src=\"%s&size=icon\">&nbsp;", thumbnailUrl);
                        }
                        name = StringUtils.safeHtml(x.personNode().getName());
                        relationDescription = "&nbsp;<span class=\"relationship-description\">%s</span>".formatted(x.relationDescription());
                    }

                    return String.format("<li><a href=\"person?id=%s%s\" class=\"distance-%d\">%s%s</a>%s</li>\n",
                            x.personNode().getId().toString(),
                            extraQueryStringForOtherPerson,
                            x.distance(),
                            thumbnailHtml,
                            name,
                            relationDescription
                            );
                })
                .collect(Collectors.joining("\n")));
    }

    /**
     * This method will add data so the preview window works.  It will
     * generate JavaScript objects for all the people it is given
     */
    private void addCloseRelativeDataForPreview(Map<String, String> myMap,
                                                Set<ShortRelationship> myRelatives, boolean shouldShowPrivateInformation) {
        myMap.put("relatives_javascript_array", myRelatives
                .stream()
                .sorted(Comparator.comparing(ShortRelationship::distance))

                .map(x -> {
                    PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                    if (!oip.shouldObscureInformation(cachedPersonFile.getBorn(), cachedPersonFile.getDied(), shouldShowPrivateInformation)) {
                        Date birthdate = cachedPersonFile.getBorn();
                        Date deathdate = cachedPersonFile.getDied();
                        String bornString = birthdate.getPrettyString().isBlank() ? "" : "born " + birthdate.getPrettyString();
                        String deathString = deathdate.getPrettyString().isBlank() ? "" : "died " + deathdate.getPrettyString();
                        String thumbnailUrl = safeAttr(cachedPersonFile.getImageUrl());
                        return String.format("\"%s\": {borndate:\"%s\", deathdate:\"%s\", personimagesrc:\"%s\", relationship:\"%s\", distance:\"%d\", name:\"%s\"}",
                                x.personNode().getId().toString(),
                                bornString,
                                deathString,
                                thumbnailUrl,
                                StringUtils.safeHtml(x.relationDescription()).replace("\"", "\\\""),
                                x.distance(),
                                StringUtils.safeHtml(x.personNode().getName()).replace("\"", "\\\""));
                    } else {
                        return null;
                    }

                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(",\n")));
    }

    /**
     * A person has extra fields.  These are optional - like if they have
     * a wedding date recorded.
     */
    private static void addExtraFieldsToTemplate(PersonFile deserializedPersonFile, Map<String, String> myMap) {
        List<PersonFile.ExtraFieldTriple> extraFields = deserializedPersonFile.getExtraFieldsAsList();

        extraFields.sort(Comparator.comparing(PersonFile.ExtraFieldTriple::key));
        String extraFieldsItem = extraFields.stream().map(x -> String.format("""
                <li class="extra-info %s">
                    <span class="label">%s: </span>
                    <span>%s</span>
                </li>
                """,
                StringUtils.safeHtml(x.key()),
                StringUtils.safeHtml(x.key()),
                StringUtils.safeHtml(x.value()))
        ).collect(Collectors.joining("\n"));

        myMap.put("extra_fields", extraFieldsItem);
    }

    /**
     * Add an image that best portrays this person
     */
    private static void addImageToTemplate(PersonFile deserializedPersonFile, Map<String, String> myMap) {
        // only add the image link if this person has an associated image
        String imageHtml;
        if (deserializedPersonFile.getImageUrl() == null || deserializedPersonFile.getImageUrl().isBlank()) {
            imageHtml = """
                    <div class="image-container">
                        <img width=200 height=200 src="/general/view.webp" alt="a pretty view">
                    </div>
                    """;
        } else {
            imageHtml = String.format("""
                    <div class="image-container">
                        <img
                            class="person_image"
                            src="%s"
                            alt="" />
                    </div>
                    """,
                    Cleaners.cleanScript(safeAttr(deserializedPersonFile.getImageUrl())));
        }
        myMap.put("person_image", imageHtml);
    }

    /**
     * Add a lot of the basic information that does not require
     * special handling.
     * @param shouldShowPrivateInformation whether we are serving data to an authenticated person. If they are
     *                                     non-authenticated, we'll hide nearly all identifying information
     *                                     from living people.
     */
    private Map<String, String> fillInTemplatePartially(PersonFile personFile, UUID otherPerson, boolean shouldShowPrivateInformation) {
        var myMap = new HashMap<String, String>();
        myMap.put("id", safeAttr(personFile.getId().toString()));
        myMap.put("siblings", renderImmediateRelations(personFile.getSiblings(), otherPerson, shouldShowPrivateInformation));
        myMap.put("spouses", renderImmediateRelations(personFile.getSpouses(), otherPerson, shouldShowPrivateInformation));
        myMap.put("parents", renderImmediateRelations(personFile.getParents(), otherPerson, shouldShowPrivateInformation));
        myMap.put("children",renderImmediateRelations(personFile.getChildren(), otherPerson, shouldShowPrivateInformation));
        myMap.put("last_modified", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC))
                .format(personFile.getLastModified().truncatedTo(ChronoUnit.SECONDS)));
        myMap.put("last_modified_by", personFile.getLastModifiedBy());
        return myMap;
    }




    /**
     * This method's job is to adjust the immediate relations (parents, spouses, siblings, children) that
     * are stored as anchor tags.  The adjusted values are sent to the browser.  Adjustments include setting
     * the value as "private" if living, as "missing" if deleted, and the words inside the anchor tag are
     * replaced with the name in the personsDb.
     */
    String renderImmediateRelations(String relatives, UUID otherPerson, boolean shouldShowPrivateInformation) {
        String cleanedString = Cleaners.cleanScript(relatives);
        Matcher matcher = personAnchorRegex.matcher(cleanedString);

        /*
        This uses a pretty tricky regular expression, with a lot of matching groups, so
        this explanation is warranted:

        group 0: the whole darn thing
        group 1: <a href=
        group 2: person?id=a9bb92ff-1893-4d28-9405-33903a948216>
        group 3: person?id=
        group 4: a9bb92ff-1893-4d28-9405-33903a948216
        group 5: Gary
        group 6: </a>

         */
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String personid = matcher.group("personid");
            Person person = this.personDb.findExactlyOne("id", personid);
            String replacementText = "";
            if (person == null) {
                logger.logDebug(() -> "Error: at renderImmediateRelations, person was null when searching by id of " + personid + ".");
                replacementText = "<span title=\"The URL for this person did not point to a person - they may have been deleted\" class=\"missing-person\" href=\"#\">(MISSING)</span>";
                matcher.appendReplacement(sb, replacementText);
                continue;
            }
            // if they are living, and the user is not authenticated, replace their name with "private"
            if (oip.shouldObscureInformation(person.getBirthday(), person.getDeathday(), shouldShowPrivateInformation)) {
                if (otherPerson != null) {
                    replacementText = "<a href=\"$3$4&oid="+otherPerson+"\">Private$6";
                } else {
                    replacementText = "<a href=\"$3$4 \">Private$6";
                }
            } else {
                if (otherPerson != null) {
                    replacementText = "<a href=\"$3$4&oid="+otherPerson+"\">"+person.getName()+"$6";
                } else {
                    replacementText = "<a href=\"$3$4\">"+person.getName()+"$6";
                }
            }

            matcher.appendReplacement(sb, replacementText);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }



    /**
     * Basically this is the same as {@link #renderImmediateRelations(String, UUID, boolean)}
     * except that it is focused on building strings for the print page, so we don't really
     * want anything but names (no anchor text)
     */
    String renderImmediateRelationsForPrinting(String relatives) {
        String cleanedString = Cleaners.cleanScript(relatives);
        Matcher matcher = personAnchorRegex.matcher(cleanedString);

        /*
        This uses a pretty tricky regular expression, with a lot of matching groups, so
        this explanation is warranted:

        group 0: the whole darn thing
        group 1: <a href=
        group 2: person?id=a9bb92ff-1893-4d28-9405-33903a948216>
        group 3: person?id=
        group 4: a9bb92ff-1893-4d28-9405-33903a948216
        group 5: Gary
        group 6: </a>

         */
        StringBuilder sb = new StringBuilder();
        // if we are *not* on the first word, put a comma in front of each name
        boolean firstWord = true;
        while (matcher.find()) {
            String personid = matcher.group("personid");
            Person person = this.personDb.findExactlyOne("id", personid);
            String replacementText = "";
            if (person == null) {
                logger.logDebug(() -> "Error: at renderImmediateRelationsForPrinting, person was null when searching by id of " + personid + ".");
                replacementText = ! firstWord ? ", (MISSING)" : "(MISSING)";
                sb.append(replacementText);
                firstWord = false;
                continue;
            }
            replacementText = ! firstWord ? ", " + person.getName() : person.getName();
            firstWord = false;
            sb.append(replacementText);
        }
        return sb.toString();
    }



}
