package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import se.rit.edu.git.models.CommitMetaData;
import se.rit.edu.git.models.NullCommitMetaData;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.JavaParseUtil;
import se.rit.edu.util.MappingPair;
import se.rit.edu.util.NullGroupedComment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

public class SATDRemovedChangedMovedCommitLocator extends CommitLocator {

    @Override
    public void findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        try {
            final List<RevCommit> commitsBetween = CommitLocatorUtil.getCommitsBetween(gitInstance,
                    gitInstance.getRepository().resolve(v1), gitInstance.getRepository().resolve(v2));
            final DiffFormatter formatter = this.getFormatter(gitInstance);
            // Get commit to file mapping
            final Map<RevCommit, String> satdFileNamePerCommit =
                    this.getFileNamesInCommitMap(gitInstance, satdInstance, commitsBetween);
            RevCommit resolvedCommit = this.findCommitAddressed(gitInstance, commitsBetween, formatter,
                    satdFileNamePerCommit, satdInstance);
            satdInstance.setCommitAddressed(new CommitMetaData(resolvedCommit));
        } catch (IOException e) {
            System.err.println("Error resolving commit strings when finding addressed commit in SATDRemovedChangedMovedCommitLocator");
            satdInstance.setCommitAddressed(new NullCommitMetaData());
            satdInstance.setResolution(SATDInstance.SATDResolution.ERROR_UNKNOWN);
        }
    }

    private Map<RevCommit, String> getFileNamesInCommitMap(Git gitInstance, SATDInstance satdInstance, List<RevCommit> allCommits) {
        // If we know the SATD remained in the same file, then map all commits to the same file
        if( satdInstance.getOldFile().equals(satdInstance.getNewFile()) ) {
            return allCommits.stream()
                    .map(commit -> new MappingPair(commit, satdInstance.getOldFile()))
                    .collect(Collectors.toMap(pair -> (RevCommit)pair.getFirst(), pair -> (String)pair.getSecond()));
        }
        // Otherwise, map all commits to the name of the SATD file at the time
        final Map<RevCommit, String> retMap = new HashMap<>();
        retMap.put(allCommits.get(0), satdInstance.getOldFile());
        String curName = satdInstance.getOldFile();
        for(int i = 1; i < allCommits.size(); i++) {
            final String finalCurName = curName;
            curName = CommitLocator.getDiffEntries(
                    gitInstance,
                    allCommits.get(i-1).getTree(),
                    allCommits.get(i).getTree()).stream()
                    .filter(de -> de.getOldPath().equals(finalCurName))
                    .filter(de -> de.getChangeType().equals(DiffEntry.ChangeType.RENAME))
                    .map(DiffEntry::getNewPath)
                    .findFirst()
                    .orElse(finalCurName);
            retMap.put(allCommits.get(i), curName);
        }
        return retMap;
    }

    /**
     * Uses a binary search to find the commit which resolved the SATD
     * @param gitInstance the Git Instance
     * @param orderedCommits an ordered list of commits which will contain a commit which resolved the
     *                       SATD, or a renaming action
     * @param formatter a formatter to format a DiffEntry to obtain the edit values
     * @param commitFileMapping a mapping of RevCommits to the name of the file in those commits
     * @param satdInstance an instance of SATD
     * @return the RevCommit which resolved the SATD, or renamed the file containing it
     * @throws IOException thrown if any git errors occur
     */
    private RevCommit findCommitAddressed(
            Git gitInstance, List<RevCommit> orderedCommits, DiffFormatter formatter,
            Map<RevCommit, String> commitFileMapping, SATDInstance satdInstance) throws IOException {
        // Base case -- we found a single commit
        if( orderedCommits.size() == 1 ) {
            return orderedCommits.get(0);
        }

        // Other case -- there are more than 2 commits, so we must continue the search
        final RevCommit firstCommit = orderedCommits.get(orderedCommits.size()/2 - 1);
        final RevCommit secondCommit = orderedCommits.get(orderedCommits.size()/2);
        final GroupedComment matchingCommentInSecondFile = this.commitContainsSATD(
                gitInstance, secondCommit, satdInstance, commitFileMapping.get(secondCommit));
        // If the second commit does not contains the SATD
        if( matchingCommentInSecondFile instanceof NullGroupedComment ) {
            // Get the SATD Occurrence in the first file
            final GroupedComment matchingCommentInFirstFile = this.commitContainsSATD(
                    gitInstance, firstCommit, satdInstance, commitFileMapping.get(firstCommit));
            // Neither first, nor second file contains SATD, so it must have been addressed earlier
            if( matchingCommentInFirstFile instanceof NullGroupedComment ) {
                return this.findCommitAddressed(gitInstance, orderedCommits.subList(0, orderedCommits.size()/2),
                        formatter, commitFileMapping, satdInstance);
            }
            // We know the SATD was addressed here so we can set the final file location
            satdInstance.setNewFile(commitFileMapping.get(secondCommit));
            // See how this commit impacted the SATD
            final Optional<Edit> editBetween = CommitLocator.getDiffEntries(gitInstance, firstCommit.getTree(), secondCommit.getTree()).stream()
                    .filter(de -> de.getOldPath().equals(commitFileMapping.get(firstCommit)))
                    .filter(de -> de.getChangeType().equals(DiffEntry.ChangeType.MODIFY))
                    .map(de -> {
                        try {
                            return formatter.toFileHeader(de);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .flatMap(header -> header.toEditList().stream())
                    .filter(edit -> editOccursBetweenLines(edit, matchingCommentInFirstFile.getStartLine(),
                            matchingCommentInFirstFile.getEndLine()))
                    .findFirst();
            if( editBetween.isPresent() ) {
                // No lines were added to the range in this commit
                if( editBetween.get().getLengthB() == 0 ) {
                    satdInstance.setResolution(SATDInstance.SATDResolution.SATD_REMOVED);
                }
                // Some lines were added, so the comment may have been changed or removed
                else {
                    satdInstance.setResolution(SATDInstance.SATDResolution.SATD_POSSIBLY_REMOVED);
                }
            } else if( !commitFileMapping.get(secondCommit).equals(commitFileMapping.get(firstCommit)) ) {
                // If the SATD was not removed, but the file was renamed
                satdInstance.setResolution(SATDInstance.SATDResolution.FILE_PATH_CHANGED);
            } else {
                // We don't know what happened to the SATD
                System.err.println("Error finding resolution to SATD, no resolution will be set.");
            }
            return secondCommit;
        }
        // The SATD was found in the second file, so search for commits after this file
        return this.findCommitAddressed(gitInstance,
                orderedCommits.subList(orderedCommits.size()/2, orderedCommits.size()),
                formatter, commitFileMapping, satdInstance);
    }

    private GroupedComment commitContainsSATD(Git gitInstance, RevCommit commit,
                                       SATDInstance satdInstance, String curFileName) throws IOException {
        final TreeWalk thisRepoWalker = getTreeWalker(gitInstance.getRepository(), commit);
        // Walk through the repo at this commit until the pertinent file is located
        while ( thisRepoWalker.next() ) {
            // If the file is the one we care about
            if (thisRepoWalker.getPathString().equals(curFileName)) {
                final ObjectLoader fileLoader = gitInstance.getRepository()
                        .open(thisRepoWalker.getObjectId(0));
                // See if the comment that is SATD is still in the file
                // FIXME use case: File contains multiple of the same SATD comment
                return JavaParseUtil.parseFileForComments(fileLoader.openStream()).stream()
                        .filter(comment -> compareComments(comment, satdInstance.getCommentOld()))
                        .findFirst()
                        .orElse(new NullGroupedComment());

            }
        }
        System.err.println("File not found in repository when it should have been -- this should not occur!");
        return new NullGroupedComment();
    }

    private TreeWalk getTreeWalker(Repository repository, RevCommit commit) {
        TreeWalk treeWalk = new TreeWalk(repository);
        try {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(".java"));
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
        }
        return treeWalk;
    }

    private DiffFormatter getFormatter(Git gitInstance) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(gitInstance.getRepository());
        formatter.setContext(0);
        return formatter;
    }

    private static boolean compareComments(GroupedComment comment1, String comment2) {
        return comment1.getComment()
                // Replace newlines as they are a common edit that does not impact the SATD
                .replace("\n", " ")
                .trim()
                .equals(
                        comment2.replace("\n", " ")
                                .trim()
                );
    }

    private static boolean editOccursBetweenLines(Edit edit, int startLine, int endLine) {
        return
                // Starts before the start and ends after the start
                (edit.getBeginA() <= startLine && edit.getEndA() >= startLine ) ||
                // Starts before the end, and ends after the end
                (edit.getBeginA() <= endLine && edit.getEndA() >= endLine) ||
                // Starts after the start and ends before the end
                (edit.getBeginA() >= startLine && edit.getEndA() <= endLine);
    }

}
