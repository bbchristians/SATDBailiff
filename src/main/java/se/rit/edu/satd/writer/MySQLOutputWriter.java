package se.rit.edu.satd.writer;

import se.rit.edu.git.models.CommitMetaData;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.util.GroupedComment;

import java.io.*;
import java.sql.*;
import java.util.Properties;

public class MySQLOutputWriter implements OutputWriter {

    private String dbURI;
    private String user;
    private String pass;

    public MySQLOutputWriter(String propertiesPath) throws IOException {
        final Properties properties = new Properties();
        properties.load(new FileInputStream(new File(propertiesPath)));

        this.dbURI = String.format("jdbc:mysql://%s:%s/%s?useSSL=false",
                properties.getProperty("URL"),
                properties.getProperty("PORT"),
                properties.getProperty("DB"));
        this.user = properties.getProperty("USERNAME");
        this.pass = properties.getProperty("PASSWORD");

        try {
            // Load driver
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void writeDiff(SATDDifference diff) throws IOException {
        try {
            final Connection conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
            final int projectId = this.getProjectId(conn, diff.getProjectName(), diff.getProjectURI());
            final int oldProjectTagId = this.getTagId(conn, diff.getOldTag(), projectId);
            final int newProjectTagId = this.getTagId(conn, diff.getNewTag(), projectId);
            diff.getAllSATDInstances().forEach(satdInstance -> {
                try {
                    final int oldFileId = this.getSATDInFileId(conn, satdInstance, true);
                    final int newFileId = this.getSATDInFileId(conn, satdInstance, false);
                    final int satdInstanceId = this.getSATDInstanceId(conn, satdInstance,
                            newProjectTagId, oldProjectTagId, newFileId, oldFileId);
                    storeCommitData(conn, satdInstance, satdInstanceId);
                } catch (SQLException e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            });
        } catch (SQLException e) {
            // Issues with SQL will be wrapped in an IOException to maintain interface consistency
            throw new IOException(e);
        }
    }

    /**
     * Gets the ID for the project and adds the project to the database if it is not present.
     * @param conn The DB Connection
     * @param projectName The name of the project to be saved to the DB
     * @param projectUrl The URL Of the project to be saved to the DB
     * @return The ID for the associated project
     * @throws SQLException thrown if any errors occur in SQL, or an issue is encountered
     *  obtaining the project's ID
     */
    private int getProjectId(Connection conn, String projectName, String projectUrl) throws SQLException {
        // Make query if Project exists
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT Projects.p_id FROM Projects WHERE Projects.p_name=?;");
        queryStmt.setString(1, projectName); // p_name
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO Projects(p_name, p_url) VALUES (?, ?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, projectName); // p_name
            updateStmt.setString(2, projectUrl); // p_url
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        // Some unpredicted issue was encountered, so just throw a new exception
        throw new SQLException("Could not obtain the project ID.");
    }

    /**
     * Gets the ID for the given tag in the given project, and inserts a new entry
     * if one does not already exist
     * @param conn The DB Connection
     * @param tag The string tag to add to the DB
     * @param projectId The ID of the project, FK to Projects
     * @return The ID for the given tag
     * @throws SQLException thrown if there is an error while executing any SQL logic
     */
    private int getTagId(Connection conn, String tag, int projectId) throws SQLException {
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT Tags.t_id FROM Tags WHERE Tags.tag=? AND Tags.p_id=?");
        queryStmt.setString(1, tag); // tag
        queryStmt.setInt(2, projectId); // p_id
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO Tags(tag, p_id) VALUES (?, ?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, tag); // tag
            updateStmt.setInt(2, projectId); // p_id
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain the tag ID for tag: " + tag);
    }

    /**
     * Gets the ID for the SATD file instance, and inserts it into the appropriate table
     * if it is not present
     * @param conn The DB Connection
     * @param satdInstance The SATD instance to draw models from
     * @param useOld True if the old file info in the SATDInstance should be used, else False
     * @return The ID for the SATD file instance
     * @throws SQLException Thrown if any SQL exceptions are encountered.
     */
    private int getSATDInFileId(Connection conn, SATDInstance satdInstance, boolean useOld) throws SQLException {
        // Get the correct values from the SATD Instance
        final String filePath = useOld ? satdInstance.getOldFile()
                : satdInstance.getNewFile();
        final int startLineNumber = useOld ? satdInstance.getStartLineNumberOldFile()
                : satdInstance.getStartLineNumberNewFile();
        final int endLineNumber = useOld ? satdInstance.getEndLineNumberOldFile()
                : satdInstance.getEndLineNumberNewFile();
        final GroupedComment comment = useOld ? satdInstance.getCommentGroupOld() :
                satdInstance.getCommentGroupNew();
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT SATDInFile.f_id FROM SATDInFile WHERE " +
                "SATDInFile.f_comment=? AND SATDInFile.f_path=? AND " +
                "SATDInFile.start_line=? AND SATDInFile.end_line=?");
        queryStmt.setString(1, comment.getComment()); // f_comment
        queryStmt.setString(2, filePath); // f_path
        queryStmt.setInt(3, startLineNumber); // start_line
        queryStmt.setInt(4, endLineNumber); // end_line
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO SATDInFile(f_comment, f_comment_type, f_path, start_line, end_line) " +
                            "VALUES (?,?,?,?,?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, comment.getComment()); // f_comment
            updateStmt.setString(2, comment.getCommentType()); // f_comment_type
            updateStmt.setString(3, filePath); // f_path
            updateStmt.setInt(4, startLineNumber); // start_line
            updateStmt.setInt(5, endLineNumber); // end_line
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain a file instance ID.");
    }

    /**
     * Gets the SATD Instance ID and adds the SATD Instance to the DB if needed
     * @param conn The DB Connection
     * @param satdInstance an SATD instance to lookup or store in the db
     * @param newTagId the ID of the new tag in the DB
     * @param oldTagId the ID of the old tag in the DB
     * @param newFileId the ID of the new file in the DB
     * @param oldFileId the ID of the old file in the DB
     * @return the ID of the SATDInstance in the DB
     * @throws SQLException throws if an error occurs while modifying DB
     */
    private int getSATDInstanceId(Connection conn, SATDInstance satdInstance,
                                  int newTagId, int oldTagId, int newFileId, int oldFileId) throws SQLException{
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT SATD.satd_id FROM SATD WHERE SATD.first_tag_id=? AND " +
                        "SATD.second_tag_id=? AND SATD.first_file=? AND SATD.second_file=?"
        );
        queryStmt.setInt(1, oldTagId); // first_tag_id
        queryStmt.setInt(2, newTagId); // second_tag_id
        queryStmt.setInt(3, oldFileId); // first_file
        queryStmt.setInt(4, newFileId); // second_file
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO SATD(first_tag_id, second_tag_id, first_file, second_file, resolution) " +
                            "VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setInt(1, oldTagId); // first_tag_id
            updateStmt.setInt(2, newTagId); // second_tag_id
            updateStmt.setInt(3, oldFileId); // first_file
            updateStmt.setInt(4, newFileId); // second_file
            updateStmt.setString(5, satdInstance.getResolution().name()); // resolution
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain an SATD instance ID.");
    }

    /**
     * Stores all commit data inside the SATD instance without duplicating any data
     * @param conn The DB Connection
     * @param satdInstance an SATDInstance to store the commit data of
     * @param satdInstanceId the ID of the SATDInstance in the DB
     * @throws SQLException thrown if any errors occur while interfacing with the DB
     * TODO Does the CommitType change depending on the resolution?
     *  -- Is initial blame actually the BETWEEN type when the SATD Is added?
     */
    private void storeCommitData(Connection conn, SATDInstance satdInstance, int satdInstanceId) throws SQLException {
        if( satdInstance.getInitialBlameCommits().stream()
                .anyMatch(commitMetaData -> !this.storeSingleCommit(conn, satdInstanceId,
                        commitMetaData, CommitType.BEFORE))) {
            // Exception thrown if any attempts to store a commit return false
            throw new SQLException("Error storing blame commits.");
        }
        if( satdInstance.getCommitsBetweenVersions().stream()
                .anyMatch(commitMetaData -> !this.storeSingleCommit(conn, satdInstanceId,
                        commitMetaData, CommitType.BETWEEN))) {
            // Exception thrown if any attempts to store a commit return false
            throw new SQLException("Error storing commits between versions.");
        }
        if( !storeSingleCommit(conn, satdInstanceId, satdInstance.getCommitAddressed(), CommitType.ADDRESSED) ) {
            throw new SQLException("Error storing addressing commit.");
        }
    }

    /**
     * Stores a single commit's data in the DB if needed
     * @param conn The DB Connection
     * @param satdInstanceId the ID of the SATDInstance in the DB
     * @param commitMetaData A metadata object of the commit
     * @param commitType the relation of the commit to the SATDInstance
     * @return True if no errors were encountered, else False
     */
    private boolean storeSingleCommit(Connection conn, int satdInstanceId,
                                      CommitMetaData commitMetaData, CommitType commitType) {
        try {
            final PreparedStatement queryStmt = conn.prepareStatement(
                    "SELECT * FROM Commits WHERE satd_id=? AND commit_hash=?;"
            );
            queryStmt.setInt(1, satdInstanceId); // satd_id
            queryStmt.setString(2, commitMetaData.getHash()); // commit_hash
            final ResultSet res = queryStmt.executeQuery();
            if (res.next()) {
                // Return the result if one was found
                return true;
            } else {
                // Will throw an SQLException if it fails
                this.storeCommitDataIfNeeded(conn, commitMetaData);
                // Otherwise, add it and then return the newly generated key
                final PreparedStatement updateStmt = conn.prepareStatement(
                        "INSERT INTO Commits(satd_id, commit_hash, commit_type) VALUES (?,?,?)");
                updateStmt.setInt(1, satdInstanceId); // satd_id
                updateStmt.setString(2, commitMetaData.getHash()); // commit_hash
                updateStmt.setString(3, commitType.name()); // commit_type
                updateStmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    /**
     * Stores commit data while avoid duplicate insertions
     * @param conn The DB Connection
     * @param commitMetaData the MetaData of the commit to store
     * @throws SQLException thrown if any errors are encountered while interfacing
     *      with the DB
     */
    private void storeCommitDataIfNeeded(Connection conn, CommitMetaData commitMetaData) throws SQLException {
        try {
            // Get CommitMetaData if not inserted already
            final PreparedStatement queryStmt = conn.prepareStatement(
                    "SELECT * FROM CommitMetaData WHERE commit_hash=?"
            );
            queryStmt.setString(1, commitMetaData.getHash()); // commit_hash
            final ResultSet res = queryStmt.executeQuery();
            if (!res.next()) {
                // Add the commit data if it is not found
                final PreparedStatement updateStmt = conn.prepareStatement(
                        "INSERT INTO CommitMetaData(commit_hash, author_name, author_email, " +
                                "committer_name, committer_email) VALUES (?,?,?,?,?)");
                updateStmt.setString(1, commitMetaData.getHash()); // commit_hash
                updateStmt.setString(2, commitMetaData.getAuthorName()); // author_name
                updateStmt.setString(3, commitMetaData.getAuthorEmail()); // author_email
                updateStmt.setString(4, commitMetaData.getCommitterName()); // committer_name
                updateStmt.setString(5, commitMetaData.getCommitterEmail()); // committer_email
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("SQL Error encountered when storing commit metadata.");
            throw e;
        }
    }

    /**
     * Types of relations between commits and the SATD Instance
     */
    private enum CommitType {
        BEFORE, // Commit was present in the SATDInstance when initially blamed
        BETWEEN, // Commit occurred between the first and second tag
        ADDRESSED // Commit resolved the SATD
    }


}
