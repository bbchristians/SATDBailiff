package edu.rit.se.satd.comment;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * A class representing a list of words to ignore in comments
 * Stored statically and globally available
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class IgnorableWords {

    @NonNull
    private final Set<String> ignorableWords;

    @NonNull
    private static IgnorableWords instance = null;

    /**
     * Sets the words to ignore
     * @param words the words to ignore
     */
    public static void populateIgnorableWords(Set<String> words) {
        IgnorableWords.instance = new IgnorableWords(words);
    }

    /**
     * Removes all words to ignore
     */
    public static void noIgnorableWords() {
        IgnorableWords.populateIgnorableWords(new HashSet<>());
    }

    public static Set<String> getIgnorableWords() {
        if( IgnorableWords.instance == null ) {
            return new HashSet<>();
        }
        return IgnorableWords.instance.ignorableWords;
    }

}
