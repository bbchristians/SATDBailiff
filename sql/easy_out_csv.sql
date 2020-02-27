BEGIN;    
	-- Constants
	SET @project_name = "apache/commons-validator";

	-- Headers
	-- SELECT 'satd_id', 'project_name', 'v1_tag', 'v2_tag', 'resolution', 'v1_commit', 'v1_path', 'v1_class', 'v1_method', 'v1_comment', 
-- 		   'v2_commit', 'v2_path', 'v2_class', 'v2_method', 'v2_comment'
    
   -- UNION ALL

	-- Query
	SELECT 
		SATD.satd_id, Projects.p_name as project_name, V1Tag.tag as v1_tag, V2Tag.tag as v2_tag,
        SATD.resolution, 
        BeforeCommit.commit_hash as v1_commit, FirstFile.f_path as v1_path, 
			FirstFile.containing_class as v1_class, FirstFile.containing_method as v1_method,
            FirstFile.f_comment as v1_comment, 
        AddressedCommit.commit_hash as v2_commit, SecondFile.f_path as v2_path, 
			SecondFile.containing_class as v2_class, SecondFile.containing_method as v2_method,
            SecondFile.f_comment as v2_comment
		FROM satd.Projects
		INNER JOIN satd.Tags as V1Tag
		ON Projects.p_id=V1Tag.p_id
		INNER JOIN satd.SATD
		On V1Tag.t_id=SATD.first_tag_id
        INNER JOIN satd.Tags as V2Tag
		ON SATD.second_tag_id=V2Tag.t_id
		INNER JOIN satd.SATDInFile as FirstFile
        ON SATD.first_file = FirstFile.f_id
		INNER JOIN satd.SATDInFile as SecondFile
        ON SATD.second_file = SecondFile.f_id
        LEFT JOIN satd.Commits as BeforeCommit
        on SATD.satd_id=BeforeCommit.satd_id AND BeforeCommit.commit_type='BEFORE'
		-- INNER JOIN satd.Commits as BetweenCommit
		-- on SATD.satd_id=BetweenCommit.satd_id AND BetweenCommit.commit_type='BETWEEN'
        LEFT JOIN satd.Commits as AddressedCommit
        on SATD.satd_id=AddressedCommit.satd_id AND AddressedCommit.commit_type='ADDRESSED'
		WHERE Projects.p_name=@project_name
        ORDER BY satd_id ASC
--      INTO OUTFILE 'C:/ProgramData/MySQL/MySQL Server 8.0/Uploads/out.csv'
-- 		FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
-- 		ESCAPED BY '"' 
-- 		LINES TERMINATED BY '\r\n';
    
