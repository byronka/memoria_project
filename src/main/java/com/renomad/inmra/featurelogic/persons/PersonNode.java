package com.renomad.inmra.featurelogic.persons;


import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class PersonNode {

    private final UUID id;
    private final String name;
    private final Gender gender;
    private final List<Map.Entry<String, PersonNode>> connections;
    private final ReentrantLock connectionsLock = new ReentrantLock();

    public PersonNode(UUID id, String name, Gender gender) {
        this.id = id;
        this.name = name;
        this.gender = gender;
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

    /**
     * return a copy of the data.  That way there is a bit more control
     * over when this data changes.  See {@link #setConnections} for setting the data.
     */
    public List<Map.Entry<String, PersonNode>> getConnections() {
        connectionsLock.lock();
        try {
            return new ArrayList<>(connections);
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
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "PersonNode{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }

}