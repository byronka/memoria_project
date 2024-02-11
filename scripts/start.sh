#!/bin/sh

# This script is used for starting the system to prepare for local UI testing

#set -x

# by running the Jacoco agent in tcpserver mode, it allows us to obtain the code
# coverage data consistently - there was a bit of a snag in getting Jacoco to
# dump the coverage data when the program was killed on Cygwin/Windows, but this
# tcpserver approach works.
java -javaagent:scripts/jacocoagent.jar=output=tcpserver -jar target/inmra/inmra.jar &

# get the process id, pop it in a file (we'll use this to stop the process later)
echo $! > pid_inmra

# Check it's running
sleep 1

if [ -f ./SYSTEM_RUNNING ]
then
  echo System has properly started
else
  echo
  echo
  echo "***************************************************************"
  echo "WARNING! The system does not appear to be running after startup"
  echo "         (Could not find a file called SYSTEM_RUNNING)          "
  echo "***************************************************************"
  echo
fi

#set +x
