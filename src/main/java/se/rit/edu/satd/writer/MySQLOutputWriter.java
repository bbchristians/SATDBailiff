package se.rit.edu.satd.writer;

import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.SATDInstance;

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
            // TODO add URL to the SATDInstance so it can be referenced here
            final int projectId = this.getProjectId(conn, diff.getProjectName(), "TODO -- GET URL");
            final int oldProjectTagId = this.getTagId(conn, diff.getOldTag(), projectId);
            final int newProjectTagId = this.getTagId(conn, diff.getNewTag(), projectId);
            diff.getAllSATDInstances().forEach(satdInstance -> {
                try {
                    final int oldFileId = this.getSATDInFileId(conn, satdInstance, true);
                    final int newFileId = this.getSATDInFileId(conn, satdInstance, false);
                    final int satdInstanceId = this.getSATDInstanceId(conn, satdInstance,
                            newProjectTagId, oldProjectTagId, newFileId, oldFileId);

                    // TODO store commit data
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
     * @param satdInstance The SATD instance to draw data from
     * @param useOld True if the old file info in the SATDInstance should be used, else False
     * @return The ID for the SATD file instance
     * @throws SQLException Thrown if any SQL exceptions are encountered.
     */
    private int getSATDInFileId(Connection conn, SATDInstance satdInstance, boolean useOld) throws SQLException {
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT SATDInFile.f_id FROM SATDInFile WHERE " +
                "SATDInFile.f_comment=? AND SATDInFile.f_path=? AND " +
                "SATDInFile.start_line=? AND SATDInFile.end_line=?");
        queryStmt.setString(1, satdInstance.getCommentOld()); // f_comment
        queryStmt.setString(2, useOld ? satdInstance.getOldFile() : satdInstance.getNewFile()); // f_path
        queryStmt.setInt(3, useOld ? satdInstance.getStartLineNumberOldFile() : satdInstance.getStartLineNumberNewFile()); // start_line
        queryStmt.setInt(4, useOld ? satdInstance.getEndLineNumberOldFile() : satdInstance.getEndLineNumberNewFile()); // end_line
        final ResultSet res = queryStmt.executeQuery();
        if( res.next() ) {
            // Return the result if one was found
            return res.getInt(1);
        } else {
            // Otherwise, add it and then return the newly generated key
            final PreparedStatement updateStmt = conn.prepareStatement(
                    "INSERT INTO SATDInFile(f_comment, f_path, start_line, end_line) VALUES (?, ?, ?, ?);",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, satdInstance.getCommentOld()); // f_comment
            updateStmt.setString(2, useOld ? satdInstance.getOldFile() : satdInstance.getNewFile()); // f_path
            updateStmt.setInt(3, useOld ? satdInstance.getStartLineNumberOldFile() : satdInstance.getStartLineNumberNewFile()); // start_line
            updateStmt.setInt(4, useOld ? satdInstance.getEndLineNumberOldFile() : satdInstance.getEndLineNumberNewFile()); // end_line
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain a file instance ID.");
    }

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
}
