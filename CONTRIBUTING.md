Contributing to the Memoria project
===================================

This is a full-stack family tree application, which allows users to view family relationships,
stories, photos, and videos.

I will explain how a new developer becomes familiarized with it:

Firstly, do you have Java 21 installed? The application will function on any Java beyond 21,
but the Jacoco code coverage tool relies on it being a major version of 21.

Second, you will want an IDE. As the primary author, I have been using Intellij IDEA, but any will do.

Third, you will need a copy of the software.  It is on Github, and can be cloned locally like this:

    git clone https://github.com/byronka/memoria_project

Fourth, to acclimatize, you should run the application on the command line and play around with
the functionality.  The fastest way to get started is to run the following:

    make classes            # builds the application
    make restore_sampledb   # installs a default database
    make run                # starts the application

(You can see all the possible actions by running `make` on the command line by itself, which shows the help) 

During the application startup, it will notice there is no admin user (because the users are removed from 
the default database) and will create a new one.  The password will be written to a file in the
top-level directory, called "admin_password".  The username is "admin"

For example, your credentials might be:

    username: admin
    password: jTUBq8Xk135AE3t2Q66v

To enter those credentials and operate as an administrator, visit http://localhost:8080/login

You will then see the home page with some people showing, and you can adjust anything.

At this point, I would suggest exploring the system, making any changes you want.  It is
always possible to reset the database by stopping the application, running `make restore_sampledb`,
and then starting it again with `make run` - although you will need to check the `admin_password`
file again to get your password.

Links for further context:

* An old (2024) walkthrough of the project.  Still valuable for 
  general practices: https://renomad.com/blogposts/exploring_memoria_1.html
* A page listing the features of the application: https://renomad.com/blogposts/memoria_features.html
* A page with a video of the full test suite: https://renomad.com/blogposts/deploy_process2.html

Enjoy! Let me know if you have any questions.

- Byron