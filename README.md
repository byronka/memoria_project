Memoria, a family tree photo/video sharing web application
==========================================================


Features
--------

* Provides recent birthdays and anniversaries of passing for family members
* Photos of any size can be added - scaled-down copies are made for improved performance, 
  original is made available to family members for download
* Public information is discoverable by search engines
* Information on living persons is kept private by family password
* Generates graphical family tree relationships
* Highly performant with minimal resource needs
* Designed for universal usability, ensuring a seamless experience for users of all abilities.
* Videos of any size can be shared
* Biographies can be added to either of two sections - public, or family private
* Highly customizable - HTML template pages use simple CSS style sheets

[Features with photos](https://renomad.com/blogposts/memoria_features.html)

[The Memoria administrator manual, a work in progress](https://renomad.com/blogposts/using_memoria.html)

System requirements: 
--------------------
* Developed on a MacBook Pro with OS 12.0.1, with OpenJDK 21, GNU Make 3.81 and Rsync 2.6.9
and on a Windows 10 64-bit professional, on Cygwin, OpenJDK 21, Gnu Make 4.4 and Rsync 3.2.7
* Runs in production on a CentOS Linux server with 1 GB of ram and 2 cpus, handles up to 500 requests / second


Running
--------

* To run with a sample database: `make local_dev`, wait until the
  message "System is ready" and then hit http://localhost:8080
  * To operate as an administrator, login at http://localhost:8080/login
    * username: admin
    * password: (see generated file "admin_password")
* To run tests: `make test`
* For help: `make`


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
