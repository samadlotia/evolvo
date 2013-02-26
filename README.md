Getting Started
===============

Set up srv-example
--------------------------

 1. Install Leiningen, which is like Maven for Clojure projects.
 Leiningen can be installed with [Homebrew](http://mxcl.github.com/homebrew):

        brew install leiningen 

 1. In the `srv-example` directory, start the server:

        lein run

The server should now be running. You can ensure that it's running with `curl`:

    curl http://localhost:8000

You should then be getting some JSON back.

Set up EvolvoApp
-----------------

 1. In the `EvolvoApp` directory, type:

        mvn clean install

 1. Copy the EvolvoApp jar to CytoscapeConfiguration:

        cp target/EvolvoApp-0.1.jar ~/CytoscapeConfiguration/3/apps/installed/

Running Evolvo
---------------

 1. After starting Cytoscape, go to the menu bar and select *Apps* > *Evolvo: Open Network*.
 At the URL prompt, you should see `http://localhost:8000`. Click *OK*.

 1. Right click on a node, and choose *Apps* > *Evolvo: Expand*.
 Choose *Apps* > *Evolvo: Collapse* to do the reverse.
