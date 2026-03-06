package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.htmlparsing.HtmlParser;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.StacktraceUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FamilyGraphBuilder {

    private final AbstractDb<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final ILogger logger;
    protected final Map<UUID, PersonNode> personNodes;
    HtmlParser htmlParser;

    public FamilyGraphBuilder(
            AbstractDb<Person> personDb,
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
    }

    public void updateNode(PersonFile newPersonFileData) {
        PersonNode personNode = this.personNodes.get(newPersonFileData.getId());
        FamilyGraph.updateNode(personNode, newPersonFileData, this.personNodes, this.personDb.values(), this.personLruCache);
    }

    public void createNewNode(UUID id) {
        FamilyGraph.createNodeWithConnections(personLruCache.getCachedPersonFile(id.toString()), personDb.values(), personNodes, personLruCache);
    }

    public void deleteNode(UUID id) {
        PersonNode oldPersonNode = this.personNodes.get(id);
        FamilyGraph.deleteNode(oldPersonNode, this.personNodes);
    }

    /**
     * Rebuilds the family graph.
     */
    public void buildFamilyGraph() {


            this.personNodes.clear();

            for (var person : personDb.values()) {
                try {
                    FamilyGraph.createNodeWithConnections(personLruCache.getCachedPersonFile(person), personDb.values(), personNodes, personLruCache);
                } catch (FamilyGraphProcessingException ex) {
                    // if this exception is thrown, there is something wrong in our data
                    this.logger.logAsyncError(() -> "Error building family graph: " + ex.getMessage() + " " + StacktraceUtils.stackTraceToString(ex));
                }
            }

    }

    public Map<UUID, PersonNode> getPersonNodes() {
        return personNodes;
    }

}
