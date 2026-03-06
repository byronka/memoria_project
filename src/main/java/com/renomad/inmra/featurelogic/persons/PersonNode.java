package com.renomad.inmra.featurelogic.persons;


import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class PersonNode {

    private final UUID id;
    private final String name;
    private final Gender gender;
    private final boolean isLiving;
    public static final PersonNode EMPTY = new PersonNode(new UUID(0L, 0L), "", Gender.UNKNOWN, false);

    /**
     * This is a list of relationship to person pairs.  For example,
     * "sibling" -> PersonNode@1234
     */
    private final List<Map.Entry<String, PersonNode>> connections;
    private final ReentrantLock connectionsLock = new ReentrantLock();

    public PersonNode(UUID id, String name, Gender gender, boolean isLiving) {
        this.id = id;
        this.name = name;
        this.gender = gender;
        this.isLiving = isLiving;
        this.connections = new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Gender getGender() {
        return gender;
    }

    public boolean isLiving() {
        return isLiving;
    }

    /**
     * return a copy of the data.  That way there is a bit more control
     * over when this data changes.  See {@link #setConnections} for setting the data.
     * see {@link PersonNode#connections}
     */
    public List<Map.Entry<String, PersonNode>> getConnections() {
        connectionsLock.lock();
        try {
            return new ArrayList<>(connections);
        } finally {
            connectionsLock.unlock();
        }
    }


    /**
     * This returns only parent connections, which is a way to guarantee
     * that the people we are searching are blood relations.
     */
    public List<Map.Entry<String, PersonNode>> getBloodConnections() {
        connectionsLock.lock();
        try {
            return connections.stream().filter(x -> x.getKey().equals("parent")).toList();
        } finally {
            connectionsLock.unlock();
        }
    }

    public void setConnections(List<Map.Entry<String, PersonNode>> connections) {
        connectionsLock.lock();
        try {
            this.connections.clear();
            this.connections.addAll(connections);
        } finally {
            connectionsLock.unlock();
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonNode that = (PersonNode) o;
        return isLiving == that.isLiving && Objects.equals(id, that.id) && Objects.equals(name, that.name) && gender == that.gender;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, gender, isLiving);
    }

    @Override
    public String toString() {
        return "PersonNode{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", gender=" + gender +
                ", isLiving=" + isLiving +
                '}';
    }
}