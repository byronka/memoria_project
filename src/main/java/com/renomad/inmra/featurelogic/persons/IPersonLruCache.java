package com.renomad.inmra.featurelogic.persons;

public interface IPersonLruCache {

    /**
     * Add a mapping from a person's id (a UUID in string form)
     * to their file information
     */
    void putToPersonFileLruCache(String id, PersonFile personFile);

    /**
     * Remove a personFile from the cache
     * @param id a string version of a person's UUID identifier.
     */
    void removeFromPersonFileLruCache(String id);

    /**
     * Gets a {@link PersonFile} by UUID identifier, or else
     * return a {@link PersonFile#EMPTY}.
     * <br>
     * Will read from the cache if available, or from disk
     * if necessary.
     */
    PersonFile getCachedPersonFile(Person person);

    /**
     * Gets a {@link PersonFile} by UUID identifier, or else
     * return a {@link PersonFile#EMPTY}
     */
    PersonFile getCachedPersonFile(String uuidForPerson);
}
