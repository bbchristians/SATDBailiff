package se.rit.edu.satd.writer;

import se.rit.edu.satd.SATDDifference;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
            final int projectId = this.getProjectId(conn, diff.getProjectName(), "TODO -- GET URL");
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
                    "INSERT INTO Projects(p_name, p_url) VALUES(?, ?);",
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
}
