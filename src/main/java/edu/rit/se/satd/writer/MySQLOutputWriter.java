package edu.rit.se.satd.writer;

import edu.rit.se.git.model.CommitMetaData;
import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.refactoring.model.RefInstance;
import edu.rit.se.satd.refactoring.model.RefactoringHistory;
import edu.rit.se.satd.refactoring.model.SatdRemoval;
import org.checkerframework.checker.units.qual.A;

import java.io.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MySQLOutputWriter implements OutputWriter {

    private static final int COMMENTS_MAX_CHARS = 4096;

    private final Map<String, Integer> cachedProjectKeys = new HashMap<>();

    private final String dbURI;
    private final String user;
    private final String pass;

    private final ScheduledThreadPoolExecutor finalWriteExecutor;


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
        final int maxConnections = Integer.parseInt(properties.getProperty("MAX_CONNECTIONS", "151"));
        this.finalWriteExecutor = new ScheduledThreadPoolExecutor( Math.max(1, maxConnections - 1));

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
            // Write the first part synchronously, because we don't want it duplicated
            // and duplication is possible.
            conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
            int projectId;
            // Cache project key to shorten each write by one query
            if( this.cachedProjectKeys.containsKey(diff.getProjectName()) ) {
                projectId = this.cachedProjectKeys.get(diff.getProjectName());
            } else {
                projectId = this.getProjectId(conn, diff.getProjectName(), diff.getProjectURI());
                this.cachedProjectKeys.put(diff.getProjectName(), projectId);
            }
            final String oldCommitId = this.getCommitId(conn, new CommitMetaData(diff.getOldCommit()), projectId);
            final String newCommitId = this.getCommitId(conn, new CommitMetaData(diff.getNewCommit()), projectId);

            // Now finish the remaining writes async and allow time for the previous writer to complete.
            final Connection asyncConn = conn;
            conn = null;
            final Thread writeLastAsync = new Thread(() -> {
                try {
                    for (SATDInstance satdInstance : diff.getSatdInstances()) {
                        final int oldFileId = this.getSATDInFileId(asyncConn, satdInstance, true);
                        final int newFileId = this.getSATDInFileId(asyncConn, satdInstance, false);
                        this.getSATDInstanceId(asyncConn, satdInstance, newCommitId, oldCommitId, newFileId, oldFileId, projectId);
                    }
                } catch (SQLException e) {
                    throw new UncheckedIOException(new IOException(e));
                } finally {
                    try {
                        asyncConn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing SQL connection in thread");
                    }
                }
            });
            finalWriteExecutor.schedule(writeLastAsync, 100, TimeUnit.MILLISECONDS);

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
     * Returns list of key value pairs containing removed satd comments for a given project
     * @param projectName -  The name of the project
     * @param projectURL - The url of the project
     * @return - Hashmap
     * @throws SQLException
     */
   public Map<String,String> getRemovedSATD(String projectName, String projectURL) throws SQLException {
       HashMap<String,String> satd = new HashMap<>();
       Connection conn = null;
       try{
        //Connect to db
        conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
        String projectID = "" + getProjectId(conn, projectName, projectURL);
        //Prepare sql statement
        final PreparedStatement queryStmt = conn.prepareStatement(
                "SELECT SATDInFile.f_comment,SATDInFile.f_id\n" +
                        "FROM SATD\n" +
                        "INNER JOIN SATDInFile ON SATDInFile.f_id = SATD.first_file\n" +
                        "INNER JOIN Projects ON Projects.p_id = SATD.p_id\n" +
                        "WHERE SATD.resolution = ? AND Projects.p_id = ?;");
        queryStmt.setString(1, "SATD_REMOVED");
        queryStmt.setString(2, projectID);
        //Execute query
        final ResultSet res = queryStmt.executeQuery();

        //Save results in hashmap
        while(res.next()) {
            satd.put(res.getString(2),res.getString(1) );
        }
       } catch(SQLException e){
           System.err.println("SQL Error encountered while fetching removed SATD");
           throw e;
       } finally{
           try {
               conn.close();
           } catch (SQLException e) {
               System.err.println("Error closing SQL connection");
               throw e;
           }
       }
       return satd;
   }

   public ArrayList<SatdRemoval> getRemovedDesignCommits(String projectName, String projectUrl) throws SQLException {
       Connection conn = null;
       ArrayList<SatdRemoval> removals = new ArrayList<>();

       try {
           //Connect to db
           conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
           int projectID =  getProjectId(conn, projectName, projectUrl);
           //Prepare sql statement
           final PreparedStatement queryStmt = conn.prepareStatement(
                   "SELECT DISTINCT SATD.first_commit, SATD.second_commit, SATDInFile.f_id\n" +
                           "FROM SATDInFile\n" +
                           "INNER JOIN SATD ON (SATDInFile.f_id = SATD.first_file)\n" +
                           "WHERE SATDInFile.type = 'DESIGN' AND SATD.p_id = ?;");
           queryStmt.setInt(1, projectID);
           //Execute query
           final ResultSet res = queryStmt.executeQuery();
           //Save results in hashmap
           while(res.next()) {
               SatdRemoval removal = new SatdRemoval(res.getString(1), res.getString(2), res.getInt(3));
               removals.add(removal);
           }
       } catch(SQLException e){
           System.err.println("SQL Error encountered while fetching removed design satd.");
           throw e;
       }finally{
           try{
               conn.close();
           }catch(SQLException e){
               System.err.println("Error closing SQL connection");
               throw e;
           }
       }
       return removals;
   }

    public void writeCommitRefactorings(ArrayList<RefInstance> refactoringList,String projectName, String projectUrl ) throws SQLException {
        Connection conn = null;
        try {
            //Connect to db
            conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
            int projectID =  getProjectId(conn, projectName, projectUrl);
            //Write info for each refactoring related to this comment
            for(RefInstance refactoring : refactoringList){
                final PreparedStatement sqlQuery = conn.prepareStatement(
                        "INSERT INTO RefactoringsRmv\n" +
                                "VALUES (default,?,?,?,?);",
                        Statement.RETURN_GENERATED_KEYS);
                sqlQuery.setString(1,  refactoring.getCommitID());

                //project id
                refactoring.setProjectID(projectID);
                sqlQuery.setInt(2, projectID);

                sqlQuery.setString(3, refactoring.getRefactoringType());
                sqlQuery.setString(4, refactoring.getRefactoringDescription());
                sqlQuery.executeUpdate();
                final ResultSet res = sqlQuery.getGeneratedKeys();
                if (res.next()) {
                    refactoring.setRefactoringID(res.getInt(1));
                } else{
                    throw new SQLException("Could not obtain the refactoring ID.");
                }
            }
        }catch(SQLException e){
            System.err.println(e);
            throw e;
        }finally{
            try{
                conn.close();
            }catch(SQLException e){
                System.err.println("Error closing SQL connection");
                throw e;
            }
        }
    }

    public void writePreviousRefHistory(ArrayList<RefactoringHistory>  priorHistory, int refactoringID ) throws SQLException {
        Connection conn = null;
        try {
            //Connect to db
            conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
            //Write info for each history related to this refactoring
            for(RefactoringHistory prior: priorHistory){
                final PreparedStatement sqlQuery = conn.prepareStatement(
                        "INSERT INTO BeforeRefactoring\n" +
                                "VALUES (default,?, ?, ?,?,?,?,?,?);",
                        Statement.RETURN_GENERATED_KEYS);
                sqlQuery.setInt(1, refactoringID);
                sqlQuery.setString(2, prior.getFilePath());
                sqlQuery.setInt(3, prior.getStartLine());
                sqlQuery.setInt(4, prior.getEndLine());
                sqlQuery.setInt(5, prior.getStartColumn());
                sqlQuery.setInt(6, prior.getEndColumn());
                sqlQuery.setString(7, prior.getDescription());
                sqlQuery.setString(8, prior.getCodeElement());
                sqlQuery.executeUpdate();
                final ResultSet res = sqlQuery.getGeneratedKeys();
                if(!res.next())  throw new SQLException("Could not save prior history.");
            }
        } catch(SQLException e){
            System.err.println(e);
            throw e;
        }finally {
            try{
                conn.close();
            }catch(SQLException e){
                System.err.println("Error closing SQL connection");
                throw e;
            }
        }
    }

    public void writeAfterRefHistory(ArrayList<RefactoringHistory>  afterHistory , int refactoringID ) throws SQLException {
        Connection conn = null;
        try {
            //Connect to db
            conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
            //Write info for each history related to this refactoring
            for(RefactoringHistory prior: afterHistory){
                final PreparedStatement sqlQuery = conn.prepareStatement(
                        "INSERT INTO AfterRefactoring\n" +
                                "VALUES (default,?, ?, ?,?,?,?,?,?);",
                        Statement.RETURN_GENERATED_KEYS);
                sqlQuery.setInt(1, refactoringID);
                sqlQuery.setString(2, prior.getFilePath());
                sqlQuery.setInt(3, prior.getStartLine());
                sqlQuery.setInt(4, prior.getEndLine());
                sqlQuery.setInt(5, prior.getStartColumn());
                sqlQuery.setInt(6, prior.getEndColumn());
                sqlQuery.setString(7, prior.getDescription());
                sqlQuery.setString(8, prior.getCodeElement());
                sqlQuery.executeUpdate();
                final ResultSet res = sqlQuery.getGeneratedKeys();
                if(!res.next())  throw new SQLException("Could not save after history.");
            }
        } catch(SQLException e){
            System.err.println(e);
            throw e;
        }finally {
            try{
                conn.close();
            }catch(SQLException e){
                System.err.println("Error closing SQL connection");
                throw e;
            }
        }
    }

    /**
     * Saves Azure classifications in the db
     * @param predictions
     * @throws SQLException
     */
   public void writePredictionResults(Map<String, String> predictions) throws SQLException{
       Connection conn = null;
       try {
           //Connect to db
           conn = DriverManager.getConnection(this.dbURI, this.user, this.pass);
           //Write each prediction to db
           for (String id : predictions.keySet()) {
               String type = predictions.get(id);

               PreparedStatement query = conn.prepareStatement("UPDATE SATDInFile SET type = ? WHERE f_id = ?");
               query.setString(1, type);
               query.setString(2, id);
               query.executeUpdate();
           }
       } catch(SQLException e){
           System.err.println("SQL Error encountered while saving satd type classification");
           throw e;
       }finally{
           try{
               conn.close();
           }catch(SQLException e){
               System.err.println("Error closing SQL connection");
               throw e;
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
     * @param satdInstance The SATD instance to draw diff from
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
                                  String newCommitHash, String oldCommitHash,
                                  int newFileId, int oldFileId, int projectId) throws SQLException{
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
                            "resolution, satd_instance_id, p_id, parent_instance_id) " +
                            "VALUES (?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            updateStmt.setString(1, oldCommitHash); // first_commit
            updateStmt.setString(2, newCommitHash); // second_commit
            updateStmt.setInt(3, oldFileId); // first_file
            updateStmt.setInt(4, newFileId); // second_file
            updateStmt.setString(5, satdInstance.getResolution().name()); // resolution
            updateStmt.setInt(6, satdInstance.getId()); // satd_instance_id
            updateStmt.setInt(7, projectId); // p_id
            updateStmt.setInt(8, satdInstance.getParentId()); // parent_instance_id
            updateStmt.executeUpdate();
            final ResultSet updateRes = updateStmt.getGeneratedKeys();
            if (updateRes.next()) {
                return updateRes.getInt(1);
            }
        }
        throw new SQLException("Could not obtain an SATD instance ID.");
    }

    /**
     * Stores diff data while avoid duplicate insertions
     * @param conn The DB Connection
     * @param commitMetaData the MetaData of the diff to store
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
                // Add the diff data if it is not found
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
                    updateStmt.setTimestamp(6, new Timestamp(commitMetaData.getAuthorDate().getTime()), Calendar.getInstance()); // author_date
                } else {
                    updateStmt.setTimestamp(6, null);
                }
                if( commitMetaData.getCommitDate() != null ) {
                    updateStmt.setTimestamp(7, new Timestamp(commitMetaData.getCommitDate().getTime())); // commit_date
                } else {
                    updateStmt.setTimestamp(7, null);
                }
                updateStmt.setInt(8, projectId);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("SQL Error encountered when storing diff metadata.");
            throw e;
        }
        return commitMetaData.getHash();
    }

    private static String shortenStringToLength(String str, int length) {
        return str.substring(0, Math.min(str.length(), length));
    }

    @Override
    public void close() {
        // Shutdown the executor and then run each remaining task
        this.finalWriteExecutor.shutdownNow().forEach(Runnable::run);
    }


}
