BEGIN;    
	-- Constants
	SET @project_name = "aeshell/aesh";

	-- Query
	SELECT 
		SATD.satd_id, Projects.p_name as project_name, SATD.satd_instance_id, SATD.resolution,
        SecondCommit.commit_hash as resolved_commit_v2, 
		FirstCommit.commit_date as v1_commit_date,
			FirstFile.f_path as v1_path, 
			FirstFile.containing_class as v1_class, FirstFile.containing_method as v1_method,
            FirstFile.f_comment as v1_comment, 
		SecondCommit.commit_date as v2_commit_date,
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
		-- WHERE Projects.p_name=@project_name
        ORDER BY satd_id DESC;
    
