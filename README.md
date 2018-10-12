## Current Build Status ##
Jenkins CI : [![Build Status](https://ci.phenotips.org/job/patient-network/badge/icon)](https://ci.phenotips.org/job/patient-network)

# About #

This project is a patient matching extension for [PhenoTips](https://github.com/phenotips/phenotips).

## Major tools and resources involved used by this project ##
* The [Human Phenotype Ontology (HPO)](http://www.human-phenotype-ontology.org/) - a standardized vocabulary of phenotypic abnormalities encountered in human disease; contains approximately 10,000 terms and is being developed using information from [OMIM](http://omim.org/) and the medical literature
* [Apache Solr](http://lucene.apache.org/solr/) - an enterprise search platform
* [XWiki](http://xwiki.org) - an enterprise web application development framework

# Building instructions #

This project uses [Apache Maven](http://maven.apache.org/) for the whole lifecycle management. From the project's description:

> Apache Maven is a software project management and comprehension tool.
> Based on the concept of a project object model (POM), Maven can manage
> a project's build, reporting and documentation from a central piece of information.

In short, Maven handles everything related to building the project: downloading the required dependencies, compiling the Java files, running tests, building the jars or final zips, and even more advanced goals such as performing style checks, creating releases and deploying them to a remote repository. All these steps are configured declaratively with as little custom settings as possible, since the philosophy of maven is **"convention over configuration"**, relying on well-defined best practices and defaults, while allowing custom variations where needed.

Building the entire project is as simple as `mvn install`, but first the environment must be set-up:

* Make sure a proper JDK is installed, Java SE 1.8 or higher. Just a JRE isn't enough, since the project requires compilation.
* Install maven by [downloading it](http://maven.apache.org/download.html) and following the [installation instructions](http://maven.apache.org/download.html#Installation).
* Clone the sources of the project locally, using one of:
    * `git clone git://github.com/phenotips/patient-network.git` if you need a read-only clone
    * `git clone git@github.com:phenotips/patient-network.git` if you also want to commit changes back to the project (and you have the right to do so)
    * download an [archive of the latest release](https://github.com/phenotips/patient-network/tags) if you don't want to use version control at all
* It is advisable to increase the amount of memory available to Maven: `export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=256m"`
* `cd` into the `patient-network` folder. `ls` and make sure `pom.xml` is there.
* Execute `mvn install` at the command line to build the project
    * note that the first build will take a while longer, because all the required dependencies are downloaded, but all the subsequent builds should only take a few minutes

# Issue tracker #

You can report issues, make feature requests and watch our progress on [our JIRA](https://phenotips.atlassian.net/projects/PN/issues/).

# LICENSE #

PhenoTips and its related tools are distributed under the [AGPL version 3](http://www.gnu.org/licenses/agpl-3.0.html) (GNU Affero General Public License), a well known free software/open source license recognized both by the Free Software Foundation and the Open Source Initiative.
This means that every change made to the code must also be distributed under AGPL, and any composite works that build on top of PhenoTips must use a compatible license.
For more information, please see the [PhenoTips Licensing page](https://phenotips.org/Licensing).
