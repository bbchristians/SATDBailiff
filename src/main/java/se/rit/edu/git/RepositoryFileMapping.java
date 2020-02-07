package se.rit.edu.git;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RepositoryFileMapping {

    public static String NOT_FOUND = "Not_Found";

    public static Map<String, String> getFileMapping(Set<String> from, Set<String> to) {
        Map<String, String> mapping = new HashMap<>();
        for( String filePath : from ) {
            String[] dirTree = filePath.split("/");
            Set<String> possibleMatches = new HashSet<>(to);
            for( int i = 1; i < dirTree.length && possibleMatches.size() > 1; i++ ) {
                String curPathSearch = "/" + String.join("/", getLastNEntries(dirTree, i));
                for( String toPath : new HashSet<>(possibleMatches) ) {
                    if( !toPath.endsWith(curPathSearch) ) {
                        possibleMatches.remove(toPath);
                    }
                }
            }

            if( possibleMatches.size() == 1 ) {
                mapping.put(filePath, possibleMatches.iterator().next());
            } else {
                mapping.put(filePath, NOT_FOUND);
            }
        }
        return mapping;
    }

    private static String[] getLastNEntries(String[] base, int n) {
        String[] desiredEntries = new String[n];
        for( int i = 1; i <= n; i++ ) {
            desiredEntries[n-i] = base[base.length - i];
        }
        return desiredEntries;
    }


}
