BEGIN;    

	SET @project_name = "aaberg/sql2o";

	SELECT Projects.p_name, Tags.tag, SATD.resolution, FirstFile.f_path, FirstFile.f_comment, SecondFile.f_path, SecondFile.f_comment
		FROM satd.Projects
		INNER JOIN satd.Tags
		ON Projects.p_id=Tags.p_id
		INNER JOIN satd.SATD
		On Tags.t_id=SATD.first_tag_id
		INNER JOIN satd.SATDInFile FirstFile
		on SATD.first_file=FirstFile.f_id
		INNER JOIN satd.SATDInFile SecondFile
		on SATD.second_file=SecondFile.f_id
		WHERE Projects.p_name=@project_name;
    
