Getting Started
===============

Set Up BasicIncloadServer
--------------------------

 1. Install Leiningen, which is like Maven for Clojure projects.
 Leiningen can be installed with [Homebrew](http://mxcl.github.com/homebrew):

        brew install leiningen 

 1. In the `BasicInloadServer` directory, start the REPL, a command-line prompt for interacting with Clojure programs:

        lein repl

 1. Copy these commands and paste them into the REPL:

        (use 'BasicIncloadServer.core)
        (use 'ring.adapter.jetty)
        (run-jetty handler {:port 8000})

The server should now be running. You can ensure that it's running with `curl`:

    curl http://localhost:8000

You should then be getting some JSON back.

Set up IncloadApp
-----------------

 1. In the `IncloadApp` directory, type:

        mvn clean install

 1. Copy the IncloadApp jar to CytoscapeConfiguration:

        cp target/IncloadApp-0.1.jar ~/CytoscapeConfiguration/3/apps/installed/

Running Incload
---------------

 1. After starting Cytoscape, go to the menu bar and select *Apps* > *Incload*.
 At the URL prompt, you should see `http://localhost:8000`. Click *OK*.

 1. Right click on a node, and choose *Apps* > *Incload: Expand*.
 Choose *Apps* > *Incload: Collapse* to do the reverse.
