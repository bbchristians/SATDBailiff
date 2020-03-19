package edu.rit.se.satd.writer;

import edu.rit.se.git.commit.CommitMetaData;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class MySQLOutputWriter implements OutputWriter {

    private static final int COMMENTS_MAX_CHARS = 4096;

    private String dbURI;
    private String user;
    private String pass;

    public MySQLOutputWriter(String propertiesPath) throws IOException {
        final Properties properties = new Properties();
        properties.load(new FileInputStream(new File(propertiesPath)));

        this.dbURI = String.format("jdbc:mysql://%s:%s/%s?useSSL=%s",
                properties.getProperty("URL"),
                properties.getProperty("PORT"),
                properties.getProperty("DB"),
                properties.getProperty("USE_SSL"));
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
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
            final int projectId = this.getProjectId(conn, diff.getProjectName(), diff.getProjectURI());
            final String oldCommitId = this.getCommitId(conn, new CommitMetaData(diff.getOldCommit()), projectId);
            final String newCommitId = this.getCommitId(conn, new CommitMetaData(diff.getNewCommit()), projectId);
            for( SATDInstance satdInstance : diff.getSatdInstances() ) {
                final int oldFileId = this.getSATDInFileId(conn, satdInstance, true);
                final int newFileId = this.getSATDInFileId(conn, satdInstance, false);
                this.getSATDInstanceId(conn, satdInstance, newCommitId, oldCommitId, newFileId, oldFileId);
            }
        } catch (SQLException e) {
            // Issues with SQL will be wrapped in an IOException to maintain interface consistency
            throw new IOException(e);
        } finally {
            if( conn != null ) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error closing SQL connection");
                }
            }
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
     * Gets the ID for the SATD file instance, and inserts it into the appropriate table
     * if it is not present
     * @param conn The DB Connection
     * @param satdInstance The SATD instance to draw commit from
     * @param useOld True if the old file info in the SATDInstance should be used, else False
     * @return The ID for the SATD file instance
     * @throws SQLException Thrown if any SQL exceptions are encountered.
     */
    private int getSATDInFileId(Connection conn, SATDInstance satdInstance, boolean useOld) throws SQLException {
        // Get the correct values from the SATD Instance
        final String filePath = useOld ? satdInstance.getOldInstance().getFileName()
                : satdInstance.getNewInstance().getFileName();
        final int startLineNumber = useOld ? satdInstance.getStartLineNumberOldFile()
                : satdInstance.getStartLineNumberNewFile();
        final int endLineNumber = useOld ? satdInstance.getEndLineNumberOldFile()
                : satdInstance.getEndLineNumberNewFile();
        final GroupedComment comment = useOld ? satdInstance.getOldInstance().getComment() :
                satdInstance.getNewInstance().getComment();
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT SATDInFile.f_id FROM SATDInFile WHERE " +
                "SATDInFile.f_comment=? AND SATDInFile.f_path=? AND " +
                "SATDInFile.start_line=? AND SATDInFile.end_line=?");
        queryStmt.setString(1, shortenStringToLength(
                comment.getComment().replace("\"", "\\\""), COMMENTS_MAX_CHARS)); // f_comment
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
                    "INSERT INTO SATDInFile(f_comment, f_comment_type, f_path, start_line, end_line, " +
                            "containing_class, containing_method) " +
                            "VALUES (?,?,?,?,?,?,?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, shortenStringToLength(
                    comment.getComment().replace("\"", "\\\""), COMMENTS_MAX_CHARS)); // f_comment
            updateStmt.setString(2, comment.getCommentType()); // f_comment_type
            updateStmt.setString(3, filePath); // f_path
            updateStmt.setInt(4, startLineNumber); // start_line
            updateStmt.setInt(5, endLineNumber); // end_line
            updateStmt.setString(6, comment.getContainingClass());
            updateStmt.setString(7, comment.getContainingMethod());
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain a file instance ID.");
    }

    private int getSATDInstanceId(Connection conn, SATDInstance satdInstance,
                                  String newCommitHash, String oldCommitHash, int newFileId, int oldFileId) throws SQLException{
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT SATD.satd_id FROM SATD WHERE SATD.first_commit=? AND " +
                        "SATD.second_commit=? AND SATD.first_file=? AND SATD.second_file=?"
        );
        queryStmt.setString(1, oldCommitHash); // first_tag_id
        queryStmt.setString(2, newCommitHash); // second_tag_id
        queryStmt.setInt(3, oldFileId); // first_file
        queryStmt.setInt(4, newFileId); // second_file
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO SATD(first_commit, second_commit, first_file, second_file, " +
                            "resolution, satd_instance_id) " +
                            "VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, oldCommitHash); // first_commit
            updateStmt.setString(2, newCommitHash); // second_commit
            updateStmt.setInt(3, oldFileId); // first_file
            updateStmt.setInt(4, newFileId); // second_file
            updateStmt.setString(5, satdInstance.getResolution().name()); // resolution
            updateStmt.setInt(6, satdInstance.getId()); // satd_instance_id
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain an SATD instance ID.");
    }

    /**
     * Stores commit data while avoid duplicate insertions
     * @param conn The DB Connection
     * @param commitMetaData the MetaData of the commit to store
     * @throws SQLException thrown if any errors are encountered while interfacing
     *      with the DB
     */
    private String getCommitId(Connection conn, CommitMetaData commitMetaData, int projectId) throws SQLException {
        try {
            // Get CommitMetaData if not inserted already
            final PreparedStatement queryStmt = conn.prepareStatement(
                    "SELECT * FROM Commits WHERE commit_hash=?"
            );
            queryStmt.setString(1, commitMetaData.getHash()); // commit_hash
            final ResultSet res = queryStmt.executeQuery();
            if (!res.next()) {
                // Add the commit data if it is not found
                final PreparedStatement updateStmt = conn.prepareStatement(
                        "INSERT INTO Commits(commit_hash, author_name, author_email, " +
                                "committer_name, committer_email, author_date, commit_date, p_id) " +
                                "VALUES (?,?,?,?,?,?,?,?)");
                updateStmt.setString(1, commitMetaData.getHash()); // commit_hash
                updateStmt.setString(2, commitMetaData.getAuthorName()); // author_name
                updateStmt.setString(3, commitMetaData.getAuthorEmail()); // author_email
                updateStmt.setString(4, commitMetaData.getCommitterName()); // committer_name
                updateStmt.setString(5, commitMetaData.getCommitterEmail()); // committer_email
                if( commitMetaData.getAuthorDate() != null ) {
                    updateStmt.setDate(6, new Date(commitMetaData.getAuthorDate().getTime())); // author_date
                } else {
                    updateStmt.setDate(6, null);
                }
                if( commitMetaData.getCommitDate() != null ) {
                    updateStmt.setDate(7, new Date(commitMetaData.getCommitDate().getTime())); // commit_date
                } else {
                    updateStmt.setDate(7, null);
                }
                updateStmt.setInt(8, projectId);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("SQL Error encountered when storing commit metadata.");
            throw e;
        }
        return commitMetaData.getHash();
    }

    private static String shortenStringToLength(String str, int length) {
        return str.substring(0, Math.min(str.length(), length));
    }


}
