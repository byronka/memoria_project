package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.utils.Cleaners;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StringUtils;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.renomad.inmra.featurelogic.persons.FamilyGraph.*;
import static com.renomad.minum.utils.StringUtils.safeAttr;
import static com.renomad.minum.utils.StringUtils.safeHtml;

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

    public DetailedViewRenderer(
            IFileUtils fileUtils,
            IPersonLruCache personLruCache,
            FamilyGraphBuilder familyGraphBuilder,
            Lifespan lifespan) {

        this.personLruCache = personLruCache;
        this.familyGraphBuilder = familyGraphBuilder;
        this.lifespan = lifespan;
        personDetailPageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_detail_page.html"));

    }

    /**
     * Renders the detailed view of a person.  This is what typical users
     * get to see - the point of all our work.
     */
    public String renderPersonView(PersonFile deserializedPersonFile, String authHeader, String helpLink) {
        var myMap = fillInTemplatePartially(deserializedPersonFile);
        addImageToTemplate(deserializedPersonFile, myMap);
        addExtraFieldsToTemplate(deserializedPersonFile, myMap);
        List<FamilyGraph.Relationship> descendants = addDescendants(deserializedPersonFile, myMap, familyGraphBuilder.getPersonNodes());
        List<FamilyGraph.Relationship> ancestors = addAncestors(deserializedPersonFile, myMap, familyGraphBuilder.getPersonNodes());
        addCloseRelatives(deserializedPersonFile, myMap, familyGraphBuilder.getPersonNodes(), descendants, ancestors);
        String renderedLifespan = lifespan.renderLifespan(deserializedPersonFile);
        myMap.put("lifespan", renderedLifespan);
        myMap.put("header", authHeader);
        myMap.put("help_link", helpLink);

        return personDetailPageTemplateProcessor.renderTemplate(myMap);
    }


    private List<FamilyGraph.Relationship> addDescendants(PersonFile personFile,
                                                          Map<String, String> myMap,
                                                          Map<UUID, PersonNode> personNodes) {
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                personNodes.values().stream(), x -> x.getId().equals(personFile.getId()));
        List<FamilyGraph.Relationship> myDescendants = descendants(myPersonNode, 3);

        // group the ancestors by distance
        Map<Integer, Set<FamilyGraph.Relationship>> results = new HashMap<>();
        for(var relationship : myDescendants) {
            if (relationship.distance() == 0) continue;
            if (!results.containsKey(relationship.distance())) {
                results.put(relationship.distance(), new HashSet<>());
            }
            results.get(relationship.distance()).add(relationship);
        }

        // render the results, simply, separated by divs
        StringBuilder renderedNodes = new StringBuilder();
        for (var distance : results.keySet()) {
            renderedNodes.append("\n<div>\n");
            Set<FamilyGraph.Relationship> personNodesAtDistance = results.get(distance);
            String renderedPersonNodes = personNodesAtDistance.stream()
                    .map(x -> {
                        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                        com.renomad.inmra.featurelogic.persons.Date birthdate = cachedPersonFile.getBorn();
                        String bornString = birthdate.getPrettyString().isBlank() ? "" : "born " + birthdate.getPrettyString();
                        return String.format("<a data-borndate=\"%s\" data-personimagesrc=\"%s\" data-relationship=\"%s\" href=\"person?id=%s\">%s</a>",
                                bornString,
                                StringUtils.safeAttr(cachedPersonFile.getImageUrl()),
                                x.relationDescription(),
                                x.personNode().getId().toString(),
                                StringUtils.safeHtml(x.personNode().getName())
                        );
                    })
                    .collect(Collectors.joining("\n"));
            renderedNodes
                    .append(renderedPersonNodes)
                    .append("\n<span class=\"distance\">").append(distance).append("</span>")
                    .append("\n</div>\n");
        }

        myMap.put("descendants", renderedNodes.toString());
        return myDescendants;
    }

    private List<FamilyGraph.Relationship> addAncestors(PersonFile personFile,
                                                        Map<String, String> myMap,
                                                        Map<UUID, PersonNode> personNodes) {
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                personNodes.values().stream(), x -> x.getId().equals(personFile.getId()));
        List<FamilyGraph.Relationship> myAncestors = ancestors(myPersonNode, 3);

        // group the ancestors by distance
        Map<Integer, Set<FamilyGraph.Relationship>> results = new HashMap<>();
        for(var relationship : myAncestors) {
            if (relationship.distance() == 0) continue;
            if (!results.containsKey(relationship.distance())) {
                results.put(relationship.distance(), new HashSet<>());
            }
            results.get(relationship.distance()).add(relationship);
        }

        // render the results, simply, separated by divs
        StringBuilder renderedNodes = new StringBuilder();
        for (var distance : results.keySet()) {
            renderedNodes.append("\n<div>\n");
            Set<FamilyGraph.Relationship> personNodesAtDistance = results.get(distance);
            String renderedPersonNodes = personNodesAtDistance.stream()
                    .map(x -> {
                        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                        com.renomad.inmra.featurelogic.persons.Date birthdate = cachedPersonFile.getBorn();
                        String bornString = birthdate.getPrettyString().isBlank() ? "" : "born " + birthdate.getPrettyString();
                        return String.format("<a data-borndate=\"%s\" data-personimagesrc=\"%s\" data-relationship=\"%s\" href=\"person?id=%s\">%s</a>",
                                bornString,
                                StringUtils.safeAttr(cachedPersonFile.getImageUrl()),
                                x.relationDescription(),
                                x.personNode().getId().toString(),
                                StringUtils.safeHtml(x.personNode().getName())
                        );
                    })
                    .collect(Collectors.joining("\n"));
            renderedNodes
                    .append(renderedPersonNodes)
                    .append("\n<span class=\"distance\">").append(distance).append("</span>")
                    .append("\n</div>\n");
        }

        myMap.put("ancestors", renderedNodes.toString());
        return myAncestors;
    }

    private void addCloseRelatives(PersonFile personFile,
                                   Map<String, String> myMap,
                                   Map<UUID, PersonNode> personNodes,
                                   List<FamilyGraph.Relationship> descendants,
                                   List<FamilyGraph.Relationship> ancestors
    ) {
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                personNodes.values().stream(), x -> x.getId().equals(personFile.getId()));
        List<FamilyGraph.Relationship> myRelatives = closeRelativesIncludingMarriage(myPersonNode, 3);
        myRelatives.removeAll(descendants);
        myRelatives.removeAll(ancestors);

        myMap.put("relatives", myRelatives
                .stream()
                .sorted(Comparator.comparing(Relationship::distance))

                .map(x -> {
                    PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(x.personNode().getId().toString());
                    com.renomad.inmra.featurelogic.persons.Date birthdate = cachedPersonFile.getBorn();
                    String bornString = birthdate.getPrettyString().isBlank() ? "" : "born " + birthdate.getPrettyString();
                    return String.format("<a data-borndate=\"%s\" data-personimagesrc=\"%s\" data-relationship=\"%s\" href=\"person?id=%s\" class=\"distance-%d\">%s</a>",
                            bornString,
                            StringUtils.safeAttr(cachedPersonFile.getImageUrl()),
                            x.relationDescription(),
                            x.personNode().getId().toString(),
                            x.distance(),
                            StringUtils.safeHtml(x.personNode().getName()));
                })
                .collect(Collectors.joining("\n")));
    }

    /**
     * A person has extra fields.  These are optional - like if they have
     * a wedding date recorded.
     */
    private static void addExtraFieldsToTemplate(PersonFile deserializedPersonFile, Map<String, String> myMap) {
        List<PersonFile.ExtraFieldTriple> extraFields = deserializedPersonFile.getExtraFieldsAsList();

        String extraFieldsItem = extraFields.stream().map(x -> String.format("""
                <li>
                    <span class="label">%s</span>
                    <span>%s</span>
                </li>
                """,
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
                    <img width=200 height=200 src="/general/view.webp" alt="a pretty view">
                    """;
        } else {
            imageHtml = String.format("""
                    <img
                        height=250
                        width=200
                        class="person_image"
                        src="%s"
                        alt="" />
                    """,
                    safeAttr(deserializedPersonFile.getImageUrl()));
        }
        myMap.put("person_image", imageHtml);
    }

    /**
     * Add a lot of the basic information that does not require
     * special handling.
     */
    private static Map<String, String> fillInTemplatePartially(PersonFile deserializedPersonFile) {
        var myMap = new HashMap<String, String>();
        myMap.put("id", safeAttr(deserializedPersonFile.getId().toString()));
        myMap.put("name", safeHtml(deserializedPersonFile.getName()));
        myMap.put("name_attr", safeAttr(deserializedPersonFile.getName()));
        myMap.put("born", deserializedPersonFile.getBorn().toHtmlString());
        myMap.put("died", deserializedPersonFile.getDied().toHtmlString());
        myMap.put("gender", deserializedPersonFile.getGender().serialize());
        myMap.put("siblings", Cleaners.cleanScript(deserializedPersonFile.getSiblings()));
        myMap.put("spouses", Cleaners.cleanScript(deserializedPersonFile.getSpouses()));
        myMap.put("parents", Cleaners.cleanScript(deserializedPersonFile.getParents()));
        myMap.put("children", Cleaners.cleanScript(deserializedPersonFile.getChildren()));
        myMap.put("biography", Cleaners.cleanScript(deserializedPersonFile.getBiography()));
        myMap.put("descendants", deserializedPersonFile.getBiography());
        myMap.put("last_modified", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC))
                .format(deserializedPersonFile.getLastModified().truncatedTo(ChronoUnit.SECONDS)));
        myMap.put("last_modified_by", deserializedPersonFile.getLastModifiedBy());
        return myMap;
    }


}
