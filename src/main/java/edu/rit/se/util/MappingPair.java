package edu.rit.se.util;

/**
 * Helper class for java streams and collection mapping
 */
public class MappingPair {

    private Object first;
    private Object second;

    public MappingPair(Object first, Object second) {
        this.first = first;
        this.second = second;
    }

    public Object getFirst() {
        return first;
    }

    public Object getSecond() {
        return second;
    }
}

