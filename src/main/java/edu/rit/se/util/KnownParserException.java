package edu.rit.se.util;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@AllArgsConstructor
public class KnownParserException extends Exception {

    @Getter
    @NonNull
    private final String fileName;
}
