DROP TABLE IF EXISTS satd.Commits, satd.SATD, satd.SATDInFile, satd.Projects;

CREATE TABLE IF NOT EXISTS satd.Projects (
	p_id INT AUTO_INCREMENT NOT NULL,
    p_name VARCHAR(255) NOT NULL UNIQUE,
    p_url VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (p_id)
);

CREATE TABLE IF NOT EXISTS satd.SATDInFile (
	f_id INT AUTO_INCREMENT,
    f_comment VARCHAR(4096),
    f_comment_type VARCHAR(32),
    f_path VARCHAR(512),
    start_line INT,
    end_line INT,
    containing_class VARCHAR(512),
    containing_method VARCHAR(512),
    `type` varchar(45) DEFAULT NULL,
    PRIMARY KEY (f_id)
);

CREATE TABLE IF NOT EXISTS satd.Commits(
	commit_hash varchar(256),
    p_id INT,
    author_name varchar(256),
    author_email varchar(256),
    author_date DATETIME,
    committer_name varchar(256),
    committer_email varchar(256),
    commit_date DATETIME,
    PRIMARY KEY (p_id, commit_hash),
    FOREIGN KEY (p_id) REFERENCES Projects(p_id)
);

CREATE TABLE IF NOT EXISTS satd.SATD (
	satd_id INT AUTO_INCREMENT,
    satd_instance_id INT, -- Not a key value, used only to associate SATD Instances
    parent_instance_id INT,
    p_id INT,
	first_commit varchar(256),
    second_commit varchar(256),
    first_file INT,
    second_file INT,
    resolution VARCHAR(64),
    PRIMARY KEY (satd_id),
    FOREIGN KEY (p_id) REFERENCES satd.Projects(p_id),
    FOREIGN KEY (p_id, first_commit) REFERENCES satd.Commits(p_id, commit_hash),
    FOREIGN KEY (p_id, second_commit) REFERENCES satd.Commits(p_id, commit_hash),
    FOREIGN KEY (first_file) REFERENCES satd.SATDInFile(f_id),
    FOREIGN KEY (second_file) REFERENCES satd.SATDInFile(f_id)
); 

CREATE TABLE IF NOT EXISTS  satd.RefactoringsRmv (
  `refactoringID` int NOT NULL AUTO_INCREMENT,
  `commit_hash` varchar(256) NOT NULL,
  `projectID` int DEFAULT NULL,
  `type` varchar(45) DEFAULT NULL,
  `description` mediumtext,
  PRIMARY KEY (`refactoringID`),
  UNIQUE KEY `idRefactorings_UNIQUE` (`refactoringID`),
  KEY `commit_hash_idx` (`commit_hash`),
  KEY `commit_hash_idx1` (`commit_hash`,`projectID`)
);

CREATE TABLE IF NOT EXISTS  satd.AfterRefactoring (
  `afterID` int NOT NULL AUTO_INCREMENT,
  `refID` int NOT NULL,
  `filePath` mediumtext,
  `startLine` int DEFAULT NULL,
  `endLine` int DEFAULT NULL,
  `startColumn` int DEFAULT NULL,
  `endColumn` int DEFAULT NULL,
  `description` mediumtext,
  `codeElement` mediumtext,
  PRIMARY KEY (`afterID`),
  KEY `idRefactorings_idx` (`refID`),
  CONSTRAINT `idRefactorings` FOREIGN KEY (`refID`) REFERENCES `RefactoringsRmv` (`refactoringID`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS  satd.BeforeRefactoring (
  `beforeID` int NOT NULL AUTO_INCREMENT,
  `refactoringID` int NOT NULL,
  `filePath` mediumtext,
  `startLine` int DEFAULT NULL,
  `endLine` int DEFAULT NULL,
  `startColumn` int DEFAULT NULL,
  `endColumn` int DEFAULT NULL,
  `description` mediumtext,
  `codeElement` mediumtext,
  PRIMARY KEY (`beforeID`)
);
