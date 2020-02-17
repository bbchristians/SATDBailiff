package se.rit.edu.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.Comment;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

public class JavaParseUtil {

    public static List<Comment> parseFileForComments(InputStream file) {
        final CompilationUnit parsedFile = StaticJavaParser.parse(file);
        // Check each comment for being SATD
        return parsedFile.getAllContainedComments()
                .stream()
                .filter(comment -> !comment.isJavadocComment())
                .collect(Collectors.toList());
    }
}
