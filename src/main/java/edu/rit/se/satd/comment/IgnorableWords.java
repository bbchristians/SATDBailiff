package edu.rit.se.satd.comment;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class IgnorableWords {

    @NonNull
    private final Set<String> ignorableWords;

    @NonNull
    private static IgnorableWords instance = null;

    public static void populateIgnorableWords(Set<String> words) {
        IgnorableWords.instance = new IgnorableWords(words);
    }

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
