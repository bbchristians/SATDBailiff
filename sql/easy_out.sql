BEGIN;    
	-- Constants
	SET @project_name = "apache/camel";
    Set @instance_id = "-1";

	-- Query
	SELECT 
		SATD.satd_id, Projects.p_name as project_name, SATD.satd_instance_id, SATD.resolution,
         SecondCommit.commit_hash as resolution_commit, 
			FirstFile.f_path as v1_path, 
			FirstFile.containing_class as v1_class, FirstFile.containing_method as v1_method,
            FirstFile.f_comment as v1_comment, 
		SecondCommit.commit_hash as v2_commit, 
		SecondCommit.commit_date as v2_commit_date,
		SecondCommit.author_date as v2_author_date,
			SecondFile.f_path as v2_path, 
			SecondFile.containing_class as v2_class, SecondFile.containing_method as v2_method,
            SecondFile.f_comment as v2_comment
		FROM satd.SATD
		INNER JOIN satd.SATDInFile as FirstFile
			ON SATD.first_file = FirstFile.f_id
		INNER JOIN satd.SATDInFile as SecondFile
			ON SATD.second_file = SecondFile.f_id
		INNER JOIN satd.Commits as FirstCommit
			ON SATD.first_commit = FirstCommit.commit_hash 
				AND SATD.p_id = FirstCommit.p_id
		INNER JOIN satd.Commits as SecondCommit
			ON SATD.second_commit = SecondCommit.commit_hash
				AND SATD.p_id = SecondCommit.p_id
        INNER JOIN satd.Projects
			ON SATD.p_id=Projects.p_id
		-- WHERE Projects.p_name=@project_name 
        -- AND SecondCommit.commit_hash="849ae58cfb2d68bf8f6c7a5ee6598fc7363a4b67"
        -- WHERE SATD.satd_instance_id=@instance_id
        ORDER BY satd_id DESC;


    --Getting refactorings on a commit that removed an satd
    Set
       @commitHash = "0d1bc03ceaf04880c70dfe06ede90da7603f683c";
    SELECT
       refactorings.commit_hash,
       br.refactoringID,
       SATDInFile.f_comment as removedSATD,
       refactorings.type as refactoringType,
       refactorings.description as refactoringDescription,
       br.filePath as filepathBefore,
       br.description as beforeDescription,
       ar.filePath as filepathAfter,
       ar.description afterDescription
    FROM
       BeforeRefactoring as br
       INNER JOIN
          AfterRefactoring as ar
          ON ar.refID = br.refactoringID
       INNER JOIN
          RefactoringsRmv as refactorings
          ON refactorings.refactoringID = br.refactoringID
       INNER JOIN
          SATD
          ON SATD.second_commit = refactorings.commit_hash
       INNER JOIN
          SATDInFile
          ON SATDInFile.f_id = SATD.first_file
    WHERE
       refactorings.commit_hash = @commitHash
       AND SATD.resolution = "SATD_REMOVED"
       AND SATDInFile.type = "DESIGN";

    --Get removed satd on a given project
    Set
       @projectID = 1;
    SELECT
       SecondCommit.commit_hash as resolution_commit,
       SATD.satd_id,
       FirstFile.f_comment as removed_SATD_comment,
       RefactoringsRMV.refactoringID,
       RefactoringsRmv.type as refactoring_type,
       RefactoringsRMV.description as refactoring_description,
       FirstFile.type as removed_SATD_Type
    FROM
       satd.SATD
       INNER JOIN
          satd.SATDInFile as FirstFile
          ON SATD.first_file = FirstFile.f_id
       INNER JOIN
          satd.SATDInFile as SecondFile
          ON SATD.second_file = SecondFile.f_id
       INNER JOIN
          satd.Commits as FirstCommit
          ON SATD.first_commit = FirstCommit.commit_hash
          AND SATD.p_id = FirstCommit.p_id
       INNER JOIN
          satd.Commits as SecondCommit
          ON SATD.second_commit = SecondCommit.commit_hash
          AND SATD.p_id = SecondCommit.p_id
       INNER JOIN
          satd.Projects
          ON SATD.p_id = Projects.p_id
       INNER JOIN
          satd.RefactoringsRMV
          ON RefactoringsRmv.commit_hash = SecondCommit.commit_hash
    WHERE
       Projects.p_id = @projectID
       AND FirstFile.type = "DESIGN"
       AND SATD.resolution = "SATD_REMOVED"
    ORDER BY
       satd_id DESC ;