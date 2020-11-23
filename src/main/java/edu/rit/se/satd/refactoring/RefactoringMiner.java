package edu.rit.se.satd.refactoring;

import edu.rit.se.satd.refactoring.model.RefactoringHistory;
import edu.rit.se.satd.refactoring.model.SatdRemoval;
import edu.rit.se.satd.writer.OutputWriter;
import edu.rit.se.satd.refactoring.model.RefInstance;

import lombok.SneakyThrows;
import org.refactoringminer.util.*;
import org.refactoringminer.api.*;
import org.refactoringminer.rm1.*;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import gr.uom.java.xmi.diff.CodeRange;

public class RefactoringMiner {

    public static void mineRemovalRefactorings (OutputWriter writer, String projectURL) throws SQLException {
        try {
            URL aURL = new URL(projectURL);
            String projectName = aURL.getPath().replaceFirst("\\/", "");

            System.out.println("--- Detecting refactorings on removed satd for project: ---");
            System.out.println("Locating removed SATD...");
            ArrayList<SatdRemoval> removals = writer.getRemovedDesignCommits( projectName, projectURL);

            System.out.println("Detecting refactorings...");
            //For each removed satd detect the refactorings based on start and end commit
            for (SatdRemoval removal : removals) {
                String startHash = removal.getLeftHash();
                String endHash = removal.getRightHash();
                detectCommentRefactorings(startHash, endHash, projectName, projectURL, writer);
            }
            System.out.println("Process complete");
        } catch(IOException e){
            System.out.println(e);
        }
    }

    private static void detectCommentRefactorings (String leftHash, String rightHash, String projectName, String projectUrl, OutputWriter writer){
        try {
            GitService gitService = new GitServiceImpl();
            GitHistoryRefactoringMiner miners = new GitHistoryRefactoringMinerImpl();
            Repository repo = gitService.cloneIfNotExists(
                    "repos/"+projectName,
                    projectUrl+".git");
            miners.detectBetweenCommits(repo,
                    leftHash, rightHash,
                    new RefactoringHandler() {
                        @SneakyThrows
                        @Override
                        public void handle(String commitId, List<Refactoring> refactorings) {
                            ArrayList<RefInstance> refactoringList = new ArrayList<>();
                            for (Refactoring ref : refactorings) {
                                String name = ref.getName();
                                String description = ref.toString();
                                RefInstance instance = new RefInstance( commitId,name,description);

                                instance.setRefactoringsBefore(getPriorHistory(ref));
                                instance.setRefactoringsAfter(getAfterHistory(ref));
                                refactoringList.add(instance);
                            }
                            writer.writeCommitRefactorings(refactoringList, projectName, projectUrl);

                            //For each refactoring we can write further info as well about file changes etc
                            for(RefInstance refactoring : refactoringList){
                                //History prior refactoring
                                ArrayList<RefactoringHistory>  priorHistory = refactoring.getRefactoringsBefore();
                                writer.writePreviousRefHistory(priorHistory, refactoring.getRefactoringID());

                                //History after refactoring
                                ArrayList<RefactoringHistory>  afterHistory = refactoring.getRefactoringsAfter();
                                writer.writeAfterRefHistory(afterHistory, refactoring.getRefactoringID());
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param ref
     * @return
     */
    private static ArrayList<RefactoringHistory> getPriorHistory(Refactoring ref ){
        List<CodeRange> beforeList= ref.leftSide();
        ArrayList<RefactoringHistory> beforeHistory = new ArrayList<>();
        for(CodeRange before:beforeList){
            beforeHistory.add(getHistory(before));
        }
        return beforeHistory;
    }

    /**
     *
     * @param ref
     * @return
     */
    private static ArrayList<RefactoringHistory> getAfterHistory (Refactoring ref){
        List<CodeRange> afterList = ref.rightSide();
        ArrayList<RefactoringHistory> afterHistory = new ArrayList<>();
        for(CodeRange after:afterList){
            afterHistory.add(getHistory(after));
        }
        return afterHistory;
    }

    /**
     *
     * @param object
     * @return
     */
    private static RefactoringHistory getHistory(CodeRange object){
        String filePath = object.getFilePath();
        int startLine = object.getStartLine();
        int endLine = object.getEndLine();
        int startColumn = object.getStartColumn();
        int endColumn = object.getEndColumn();
        String description = object.getDescription();
        String codeElement =  object.getCodeElement();
        RefactoringHistory history = new RefactoringHistory(filePath, startLine,endLine, startColumn,endColumn,description,codeElement);
        return history;
    }
}
