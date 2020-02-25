BEGIN;    

	SET @project_name = "apache/commons-validator";
    set @out_file = "csv/out.csv";

	SELECT 
		Projects.p_name as project_name, V1Tag.tag as v1_tag, V2Tag.tag as v2_tag,
        SATD.resolution, 
        FirstFile.f_path as v1_path, FirstFile.f_comment as v1_comment, 
        SecondFile.f_path as v2_path, SecondFile.f_comment as v2_comment
		FROM satd.Projects
		INNER JOIN satd.Tags as V1Tag
		ON Projects.p_id=V1Tag.p_id
		INNER JOIN satd.SATD
		On V1Tag.t_id=SATD.first_tag_id
        INNER JOIN satd.Tags as V2Tag
		ON SATD.second_tag_id=V2Tag.t_id
		INNER JOIN satd.SATDInFile as FirstFile
		on SATD.first_file=FirstFile.f_id
		INNER JOIN satd.SATDInFile as SecondFile
		on SATD.second_file=SecondFile.f_id
		WHERE Projects.p_name=@project_name
        INTO OUTFILE 'out/out.csv'
        FIELDS ENCLOSED BY '"'
		ESCAPED BY '"' 
		LINES TERMINATED BY '\r\n';
    
