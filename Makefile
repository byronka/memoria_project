##
# Project name - used to set the jar's file name
##
PROJ_NAME := inmra


.PHONY: all
##
# default target(s)
##
all: help

.PHONY: classes
#: compile the system
classes:
	 ./mvnw compile

.PHONY: clean
#: clean up any output files
clean:
	 ./mvnw clean

.PHONY: test
#: run the tests
test: restore_sampledb
	 ./mvnw test

.PHONY: local_dev
#: set up for local dev testing - use sample db, run with code coverage
local_dev: clean restore_sampledb
	 @echo
	 @echo "********************************************************************"
	 @echo "After this runs, code coverage report will be in target/site/jacoco/"
	 @echo "********************************************************************"
	 @echo
	 @echo
	 bash -c "trap 'trap - SIGINT SIGTERM ERR; make render_coverage_report ;exit 1' SIGINT SIGTERM ERR; make run_with_coverage"

.PHONY: render_coverage_report
# take jacoco's intermediate code-coverage data files and convert into an HTML report
render_coverage_report:
	 ./mvnw jacoco:report

.PHONY: run_with_coverage
#: run the application, recording code coverage the whole time
run_with_coverage:
	 MAVEN_OPTS=-javaagent:scripts/jacocoagent.jar=destfile=target/jacoco.exec ./mvnw compile exec:java

.PHONY: uitests
#: starts the system, runs the ui tests, stops the system
uitests: jar restore_sampledb
	 bash -c "trap 'echo STOPPING && cd .. && JACOCO_FILENAME=jacoco_ui_1.exec scripts/stop.sh' EXIT; scripts/start.sh && cd ui_tests && ./mvnw -Dtest=Test1 test"

.PHONY: migrationtests
#: starts the system with an old database, causing many of the migrations to run
migrationtests: jar restore_old_sampledb
	 bash -c 'scripts/start.sh && sleep 2 && echo STOPPING && JACOCO_FILENAME=jacocomigration.exec scripts/stop.sh'

JMX_PROPERTIES=-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false
DEBUG_PROPERTIES=-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y

.PHONY: run
#: run the system
run:
	 MAVEN_OPTS="$(JMX_PROPERTIES)" ./mvnw compile exec:java

.PHONY: runjar
#: run the system off the jar
runjar:
	 java $(JMX_PROPERTIES) -jar target/inmra/inmra.jar

.PHONY: runjlink
#: run the system off the custom runtime
runjlink: jlink
	 ./target/jrt/bin/java $(JMX_PROPERTIES) -m memoria.project/com.renomad.inmra.Main

.PHONY: rundebug
#: run the system in debug mode
rundebug:
	 MAVEN_OPTS="$(DEBUG_PROPERTIES) $(JMX_PROPERTIES)" ./mvnw compile exec:java

.PHONY: runjardebug
#: run the system off the jar in debug mode
runjardebug: jlink
	 java $(DEBUG_PROPERTIES) $(JMX_PROPERTIES) -jar target/inmra/inmra.jar

.PHONY: runjlinkdebug
#: run the system in debug mode off the custom runtime
runjlinkdebug: jlink
	 ./target/jrt/bin/java $(DEBUG_PROPERTIES) $(JMX_PROPERTIES) -m memoria.project/com.renomad.inmra.Main

.PHONY: restore_sampledb
#: restore the backed-up database into the "target" directory
restore_sampledb:
	 @mkdir -p target
	 rm -fr target/simple_db
	 cp -a sample_db/simple_db target
	 @echo
	 @echo "restored files to target/simple_db"

.PHONY: restore_old_sampledb
#: restore the backed-up database into the "target" directory
restore_old_sampledb:
	 @mkdir -p target
	 rm -fr target/simple_db
	 cp -a sample_db/old_simple_db target/simple_db
	 @echo
	 @echo "restored files to target/simple_db"

.PHONY: backup_sampledb
#: backup the current directory in "target" to a file
backup_sampledb:
	 cd target && tar zcf simple_db.tar.gz simple_db && mv simple_db.tar.gz ../sample_db
	 @echo
	 @echo "created a compressed backup of the files in target/simple_db at sample_db/simple_db.tar.gz"

.PHONY: test_coverage
#: run tests, and build a coverage report
test_coverage:
	 @./mvnw jacoco:prepare-agent test jacoco:report

.PHONY: jar
#: jar up the program
jar:
	 ./mvnw package -Dmaven.test.skip
	 @echo "create a directory of target/$(PROJ_NAME)"
	 mkdir -p target/$(PROJ_NAME)
	 @echo "copy jar to target/$(PROJ_NAME)"
	 cp target/inmra-*-jar-with-dependencies.jar target/$(PROJ_NAME)/inmra.jar
	 @echo "Your new jar is at target/inmra/inmra.jar"

.PHONY: jlink
#: create a custom java runtime
jlink:
ifeq (,$(wildcard ./target/jrt/bin/java))
	 @echo "build and copy jars and dependencies to target/modules"
	 ./mvnw clean package -Dmaven.test.skip -Pjlink
	 @echo "create a custom java runtime"
	 jlink --add-modules memoria.project --module-path target/modules --strip-debug --no-header-files --no-man-pages --compress zip-9 --output ./target/jrt
	 @echo "Current JDK size"
	 du -sh ${JAVA_HOME}
	 @echo "Custom Runtime size"
	 du -sh ./target/jrt
endif

# a handy debugging tool.  If you want to see the value of any
# variable in this file, run something like this from the
# command line:
#
#     make print-FOO
#
print-%:
	    @echo $* = $($*)

.PHONY: help
# This is a handy helper.  This prints a menu of items
# from this file - just put hash+colon over a target and type
# the description of that target.  Run this from the command
# line with "make help"
help:
	 @echo
	 @echo Help
	 @echo ----
	 @echo
	 @grep -B1 -E "^[a-zA-Z0-9_-]+:([^\=]|$$)" Makefile \
     | grep -v -- -- \
     | sed 'N;s/\n/###/' \
     | sed -n 's/^#: \(.*\)###\(.*\):.*/\2###\1/p' \
     | column -t  -s '###'
