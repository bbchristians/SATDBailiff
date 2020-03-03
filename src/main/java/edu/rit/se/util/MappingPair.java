package edu.rit.se.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Helper class for java streams and collection mapping
 */
@AllArgsConstructor
public class MappingPair {

    @Getter
    private Object first;
    @Getter
    private Object second;
}

