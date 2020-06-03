package edu.rit.se.util;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.javaparser.Position;
import edu.rit.se.git.GitUtil;
import edu.rit.se.satd.mining.commit.FileToFileMapping;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GumTreeUtil {

    public static Map<FileToFileMapping, List<Action>> getEditScript(Git git, RevCommit c1, RevCommit c2) {
        return GitUtil.getDiffEntries(git, c1, c2).stream()
                .filter(de -> de.getOldPath().endsWith(".java") && de.getNewPath().endsWith(".java"))
                .map(diffEntry -> {
                    final FilesToScript thisScript = new FilesToScript(
                            new FileToFileMapping(diffEntry.getOldPath(), "", diffEntry.getNewPath(), ""), new ArrayList<>());
                    try {
                        File fileSrc = GitUtil.getTempReferenceToFile(git, c1, diffEntry.getOldPath());
                        if( fileSrc == null ) {
                            return thisScript;
                        }
                        File fileDst = GitUtil.getTempReferenceToFile(git, c2, diffEntry.getNewPath());
                        if( fileDst == null ) {
                            return thisScript;
                        }

                        // Setup for GumTree
                        Run.initGenerators();
                        final ITree src = Generators.getInstance().getTree(fileSrc.getPath()).getRoot();
                        final ITree dst = Generators.getInstance().getTree(fileDst.getPath()).getRoot();
                        final Matcher matcher = Matchers.getInstance().getMatcher(src, dst);
                        matcher.match();
                        final String fileContentsSrc = getFileContents(fileSrc);
                        thisScript.key.setF1Contents(fileContentsSrc);
                        final String fileContentsDst = getFileContents(fileDst);
                        thisScript.key.setF2Contents(fileContentsDst);
                        if( fileContentsDst == null || fileContentsSrc == null ) {
                            return thisScript;
                        }
                        final ActionGenerator editScriptGenerator = new ActionGenerator(src, dst, matcher.getMappings());
                        editScriptGenerator.generate();
                        fileSrc.delete();
                        fileDst.delete();
                        thisScript.getValue().addAll(editScriptGenerator.getActions());
                        return thisScript;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toMap(FilesToScript::getKey, FilesToScript::getValue));
    }

    private static String getFileContents(File file) {
        try {
            Scanner reader = new Scanner(file);
            StringBuilder fileContents = new StringBuilder();
            while(reader.hasNext()) {
                fileContents.append(reader.nextLine()).append("\n");
            }
            return fileContents.toString();
        } catch (FileNotFoundException e) {
            System.err.println("Missing temp file during read.");
            return null;
        }
    }

    public static int getGumtreePosFromJavaParserPos(String fileContents, Position pos) {
        String[] lines = fileContents.split("\n");
        if( pos.line - 1 > lines.length ) {
            throw new RuntimeException("This is a problem");
        }
        int totalBeforeCurLine = 0;
        for( int i = 0; i < pos.line - 1; i++ ) {
            totalBeforeCurLine += lines[i].length() + 1; // +2 because of the removed newlines
        }
        return totalBeforeCurLine + pos.column;
    }

    @AllArgsConstructor
    private static class FilesToScript {

        @Getter
        private FileToFileMapping key;
        @Getter
        private List<Action> value;
    }
}
