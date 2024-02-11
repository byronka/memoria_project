Security
========

This code is for dealing with our server being run in a hostile environment.  Specifically,
certain traps are set (e.g. certain kinds of exceptions during TLS key negotiation, certain
endpoints sought out) and the consequence is usually being prevented from accessing the
system for a short while.