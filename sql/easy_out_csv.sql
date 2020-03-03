BEGIN;    
	-- Constants
	SET @project_name = "apache/maven-archetype";

	-- Query
	SELECT 
		SATD.satd_id, Projects.p_name as project_name, SATD.resolution, 
		FirstCommit.commit_hash as v1_commit_hash, FirstCommit.commit_date as v1_commit_date,
			FirstFile.f_path as v1_path, 
			FirstFile.containing_class as v1_class, FirstFile.containing_method as v1_method,
            FirstFile.f_comment as v1_comment, 
		SecondCommit.commit_hash as v2_commit_hash, SecondCommit.commit_date v2_commit_date,
			SecondFile.f_path as v2_path, 
			SecondFile.containing_class as v2_class, SecondFile.containing_method as v2_method,
            SecondFile.f_comment as v2_comment
		FROM satd.SATD
		INNER JOIN satd.SATDInFile as FirstFile
        ON SATD.first_file = FirstFile.f_id
		INNER JOIN satd.SATDInFile as SecondFile
        ON SATD.second_file = SecondFile.f_id
		INNER JOIN satd.Commits as FirstCommit
        on SATD.first_commit=FirstCommit.commit_hash
		INNER JOIN satd.Commits as SecondCommit
        on SATD.second_commit=SecondCommit.commit_hash
        INNER JOIN satd.Projects
        on FirstCommit.p_id=Projects.p_id
		WHERE Projects.p_name=@project_name
        ORDER BY v2_commit_date DESC
		-- INTO OUTFILE 'C:/ProgramData/MySQL/MySQL Server 8.0/Uploads/out.csv'
-- 		FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
-- 		ESCAPED BY '"' 
-- 		LINES TERMINATED BY '\r\n';
    
