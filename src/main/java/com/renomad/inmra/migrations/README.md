Migrations
==========

This is code for modifying the database contents when needed.

For example, if we have a database for users, and have added a "favorite color" field, we'll
want to add some default value for that to all the existing data so it matches the schema.

Note that the migrations are run in order, and have (some) code for running in reverse.  That is,
I have never tried running in reverse, but each migration has a forward and reverse option.