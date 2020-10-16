## Self-Admitted Technical Debt (SATD) Analyzer

This tool is intended to be used to mine SATD occurrences from
github repositories as a single or batch operation. 


### What is SATD?

Self-Admitted Technical Debt is a candid form of technical debt in which the contributor of the debt self-documents the location of the debt. This admission is typically accompanied with a description of a knownor potential defect or a statement detailing what remaining work must be done.  Well-known and frequently used examples of SATD include comments beginning with TODO, FIXME, BUG, XXX, or HACK. SATD can also take other forms of more complex language void of any of the previously mentioned keywords.  Any comment  detailing  a not-quite-right implementation  present  in  the  surrounding  code  can  be  classified  as SATD.

### Usage

To use the tool, first download the latest release, or clone the
repository locally.

**The following versions are required to run the tool:**
* Java 1.8+

If building the tool from source:
* Maven 3


#### Output

Before the tool can output any data, a mySQL server must be active to
receive the output data. The schema for the expected output can be found
in [sql/satd.sql](sql/satd.sql).

A `.properties` file is used to configure the
tool to connect to the database. The repository contains a
sample [.properties](mySQL.properties) file. The supplied 
`.properties` file should contain **all** the same fields. Extra fields will
be ignored.

#### Running the .JAR

The tool has one functionality -- mining SATD occurrences as a single
or batch operation. The tool comes packaged with a Command Line Interface
for ease of use, so it can be run like so:

```
java -jar <file.jar> -r <repository_file> -d <database_properties_file>`
```

The help menu output is as follows.

```
usage: satd-analyzer
 -a,--diff-algorithm <ALGORITHM>   the algorithm to use for diffing (Must
                                   be supported by JGit):
                                   - MYERS (default)
                                   - HISTOGRAM
 -d,--db-props <FILE>              .properties file containing database
                                   properties
 -e,--show-errors                  shows errors in output
 -h,--help                         display help menu
 -i,--ignore <WORDS>               a text file containing words to ignore.
                                   Comments containing any word in the
                                   text file will be ignored
 -l,--n_levenshtein <0.0-1.0>      the normalized levenshtein distance
                                   threshold which determines what
                                   similarity must be met to qualify SATD
                                   instances as changed
 -p,--password <PASSWORD>          password for Github authentication
 -r,--repos <FILE>                 new-line separated file containing git
                                   repositories
 -u,--username <USERNAME>          username for Github authentication
```

#### Building and Running the Tool

The project should be built using maven. To build the tool into
an executable `.jar`, use `mvn clean package`.

This project uses the implementation of another project (https://github.com/Tbabm/SATDDetector-Core) for SATD 
classification. A `.jar` of the linked project must be present in `lib/` in order for
this project to run. It should be noted, that the SATD classification model included
in that repository's released binaries differs from the model released with
this project's binaries. To use a different model in your own implementation,
follow the instructions in the aforementioned repository's readme,
and include the model files in `lib/models/`.
