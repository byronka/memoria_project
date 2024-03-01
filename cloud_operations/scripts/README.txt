If you have Java 21 installed, just run `java -jar inmra.jar`.  Otherwise, if
you prefer to use Docker, follow these instructions:

To start:
---------

docker compose up -d


To stop (important: run from this directory):
---------------------------------------------

docker compose down


Login
--------

To obtain admin password, the system must be running.  Then, run
this Docker command from the command-line:

  cat db/admin_password

The value shown is used as the password at http://localhost:8080/login, 
along with a user name of "admin"
