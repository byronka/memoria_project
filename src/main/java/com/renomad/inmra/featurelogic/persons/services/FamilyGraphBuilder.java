package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.minum.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.HtmlParser;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.ActionQueue;
import com.renomad.minum.utils.StacktraceUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FamilyGraphBuilder {

    private final Db<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final ILogger logger;
    protected final Map<UUID, PersonNode> personNodes;
    private final ActionQueue actionQueue;
    HtmlParser htmlParser;

    public FamilyGraphBuilder(
            Context context,
            Db<Person> personDb,
            IPersonLruCache personLruCache,
            ILogger logger) {
        this.personDb = personDb;
        this.personLruCache = personLruCache;
        this.logger = logger;
        htmlParser = new HtmlParser();

        // build the graph of relationships
        // --------------------------------

        // start by loading the person cache with persons
        for (var person : personDb.values()) {
            PersonFile personFile = personLruCache.getCachedPersonFile(person);
            personLruCache.putToPersonFileLruCache(personFile.getId().toString(), personFile);
        }

        // now build the graph
        this.personNodes = new ConcurrentHashMap<>();
        buildFamilyGraph();

        this.actionQueue = new ActionQueue("personCreateServicesActionQueue", context).initialize();
    }

    public void buildFamilyGraph() {
        buildFamilyGraph(null);
    }

    /**
     * Modifies the family graph.  If given null, will rebuild the whole thing. If
     * given a particular personFile, will rebuild it and its relations.
     */
    public void buildFamilyGraph(PersonFile personFile) {

        try {
            List<PersonFile> allPersons = personDb.values().stream()
                    .map(x -> personLruCache.getCachedPersonFile(x.getId().toString()))
                    .toList();
            List<PersonFile> personsToModify;
            if (personFile == null) {
                this.personNodes.clear();
                personsToModify = allPersons;
            } else {
                Set<UUID> personUUIDs = getUuidsOfSelfAndRelations(personFile);
                personsToModify = new ArrayList<>();
                for (UUID uuid : personUUIDs) {
                    this.personNodes.remove(uuid);
                    personsToModify.add(personLruCache.getCachedPersonFile(uuid.toString()));
                }
            }

            for (var person : personsToModify) {
                FamilyGraph.createNodeWithConnections(person, allPersons, personNodes);
            }
        } catch (FamilyGraphProcessingException ex) {
            // if this exception is thrown, there is something horribly wrong in our data
            this.logger.logAsyncError(() -> "Error building family graph: " + ex.getMessage() + " " + StacktraceUtils.stackTraceToString(ex));
        }
    }

    /**
     * Given a {@link PersonFile}, get all the related identifiers - in {@link UUID} form.
     * <br>
     * that means for each of the personfiles, as well as each of the relatives for each personfile.
     * <br>
     * This is used to determine which PersonNode's to delete when rebuilding.
     */
    private Set<UUID> getUuidsOfSelfAndRelations(PersonFile personFile) {
        Set<UUID> uuids = new HashSet<>();
        // add their own uuid
        uuids.add(personFile.getId());

        List<HtmlParseNode> parsedRelations = new ArrayList<>();
        // get the relatives from all the relations fields
        parsedRelations.addAll(htmlParser.parse(personFile.getSiblings()).stream()
                .filter(x -> x.tagInfo().tagName().equals(TagName.A)).toList());
        parsedRelations.addAll(htmlParser.parse(personFile.getChildren()).stream()
                .filter(x -> x.tagInfo().tagName().equals(TagName.A)).toList());
        parsedRelations.addAll(htmlParser.parse(personFile.getParents()).stream()
                .filter(x -> x.tagInfo().tagName().equals(TagName.A)).toList());
        parsedRelations.addAll(htmlParser.parse(personFile.getSpouses()).stream()
                .filter(x -> x.tagInfo().tagName().equals(TagName.A)).toList());
        for (var personAnchorElement : parsedRelations) {
            String hrefValue = personAnchorElement.tagInfo().attributes().get("href");
            String potentialUuid = hrefValue.replace("person?id=", "");
            try {
                UUID uuid = UUID.fromString(potentialUuid);
                uuids.add(uuid);
            } catch (IllegalArgumentException ignore) {
                // if not a UUID, just continue
            }
        }
        return uuids;
    }

    /**
     * rebuild the family tree
     */
    public void rebuildFamilyTree(PersonFile personFile) {
        actionQueue.enqueue(
                "rebuild the graph data structure of the family tree",
                () -> buildFamilyGraph(personFile));
    }

    public Map<UUID, PersonNode> getPersonNodes() {
        return personNodes;
    }
}
