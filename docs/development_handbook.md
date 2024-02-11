Developer handbook
==================

Table of contents
-----------------

- [New Developer Setup](#new-developer-setup)
- [To test with Chrome on localhost with invalid certificates](#to-test-with-chrome-on-localhost-with-invalid-certificates)
- [Logging](#logging)
- [Documentation](#documentation)
- [Feature tracking](#feature-tracking)


New Developer Setup
-------------------

Here's the general series of steps for a new developer:

1. Install the required JDK onto your machine if you don't already have it. Make sure to add the path to your
   JDK to your environment as JAVA_HOME, and also add the path to the Java binaries to your PATH variable.
2. Download and install IntelliJ Community Edition (which is free of charge) if you don't already have
   it. Find it here: https://www.jetbrains.com/idea/download/
3. Obtain this source code from Github, at https://github.com/byronka/large_sample_project
4. Run the tests on your computer, using the command line: `make test`
5. Run the application to get a feel for the user experience: `make local_dev` (and then go to http://localhost:8080)
6. Open the software with IntelliJ.  Navigate around.
7. Read through some of this developer documentation to get a feel for some of its unique characteristics.
8. Examine some tests in the system to get a feel for how the system works and how
   test automation is done (see the src/test directory).

Optional:
* Install "Code With Me" plugin for IntelliJ - Preferences > Plugins > Code WIth me
    * Use the new "Code With Me" icon in top bar to enable "Full Access" (turn off "start voice call")
    * Share the link with your friends


To test with Chrome on localhost with invalid certificates
----------------------------------------------------------

When testing this application, the secure (SSL) server will look for
a keystore and a keystore-password (see WebEngine checkSystemPropertiesForKeystore())

If it cannot find those values in the system properties, it will revert
back to using its own self-signed certificate.  Browsers like Chrome will
complain about this and balk at making a connection.  In order to calm
it down and let us run locally on SSL, do this:

In Chrome, go to chrome://flags/#allow-insecure-localhost
Set this option to enabled


Logging
-------

Logging capabilities are provided by the `Logger` class.  When the system starts, it
creates one instance of this class and then passes it down the call tree.  It is
stored and available for your use in the Context class.


Documentation
-------------

THe only way we can keep the complexity tamped down is by keeping vigilant about quality.
This takes many forms, and one of these is keeping good documentation.  Consider whether
a helpful explanation would get someone running sooner.  Please keep your words concise.
Brevity is the soul of wit.


Feature tracking
----------------

Following the pattern of using the simplest thing that works, the features are tracked
by writing them up and storing them in docs/todo/feature.  When they are finished, they
move to docs/todo/done

Java Flight Recorder
--------------------

In production, the system runs Java Flight Recorder.  This tool should allow us to 
see what was happening around the time of an error with the running system.

The recording is located at /tmp/recording.jfr

See the start.sh script for details on how JFR is configured