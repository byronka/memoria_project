#!/bin/sh

# This script is used for stopping the system after local UI testing

# if the user provides a name of the jacoco.exec file, use it, otherwise just use "jacoco.exec"
# to use this, for example: JACOCO_FILENAME=jacocoui.exec ./stop.sh
JACOCO_EXEC_FILE_NAME="${JACOCO_FILENAME:-jacoco.exec}"

if [ -f ./SYSTEM_RUNNING ]
then

  echo "getting the code coverage data"
  # this command will connect to the jacoco server running on port 6300 to obtain the
  # coverage data.  I found that when running Inmra on Windows / Cygwin as a background
  # process with ampersand, killing the process would cause the jacoco file dump to fail.
  # this was the only way to consistently obtain that data, and it should work fine
  # on other operating systems.
  java -jar scripts/jacococli.jar dump --destfile target/$JACOCO_EXEC_FILE_NAME

  echo "found a pid, with contents $(cat pid_inmra).  Killing that process id"

  # kill the application by reading the process id from the "pid_inmra"
  # file and "kill"-ing it (regular kill is nice.  we're not kill -9'ing here!)
  kill $(cat pid_inmra)

  echo "deleting the old pid_inmra file"
  rm pid_inmra

  echo "merging the Jacoco execution files"
  java -jar scripts/jacococli.jar merge target/jacoco.exec target/$JACOCO_EXEC_FILE_NAME --destfile target/jacoco.exec

  echo "rendering the code coverage report"
  ./mvnw jacoco:report
fi

#set +x
