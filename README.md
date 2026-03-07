Memoria, a site for memories
============================

* To run with a sample database: `make local_dev`, wait until the
  message "System is ready" and then hit http://localhost:8080
  * To operate as an administrator, login at http://localhost:8080/login
    * username: admin
    * password: (see generated file "admin_password")
* To run tests: `make test`
* For help: `make`

See the [User manual](https://renomad.com/blogposts/using_memoria.html)

See the [development handbook](docs/development_handbook.md)

System requirements: 
--------------------
Developed on a MacBook Pro with OS 12.0.1, with OpenJDK 21, GNU Make 3.81 and Rsync 2.6.9
and on a Windows 10 64-bit professional, on Cygwin, OpenJDK 21, Gnu Make 4.4 and Rsync 3.2.7

Tech stack:
-----------

 - Architecture: Full stack Java monolith
 - Backend: Java, using Minum routing and utilities
 - Frontend: Java, using Minum templates
 - Database: Minum
 - CI/CD: Bash / Make
 - IAC: runbook with tests
 - Deployment: Single VPS
 - CDN: None

Directories:
------------

- docs: documentation for the project
- cloud_operations: documents and scripts for deployment to the cloud
- .git: necessary files for Git.
- sample_db: a test database
- scripts: scripts that are run during compilation / analyzing / preparation for deployment
- src: source code for the application
- ui_tests: a separate project directory for the UI end-to-end tests

Root-level files:
-----------------

- .gitignore: files we want Git to ignore.
- .gitattributes: some details of how we want Git to operate on our project
- Makefile: configuration for the build tool, Gnu Make
- memoria.iml: untracked by Git - a configuration file for Intellij (optional)
- mvnw, mvnw.cmd - Maven's "wrapper" files - a way to provide Maven without it being already installed.
- pom.xml: Maven's configuration file, for building and obtaining dependencies
- README.md: this file
- SYSTEM_RUNNING: if this exists, it means the application is running
