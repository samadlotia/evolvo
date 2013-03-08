What is Evolvo?
===============

__Evolvo__, [Latin](http://en.wiktionary.org/wiki/evolvo#Latin) for __I unfurl__ or __I draw out__,
is a system for navigating very large networks.
The problem with large networks is that they cannot be visualized given the limitations
of modern commodity hardware. Even if they could be visualized, it would be nearly impossible
for an individual to make sense of a massive and complex network. By composing a network into tiers
that are gradually expanded by the user, large networks can become viable. The user first
loads the top-most tier, then gradually drills down into lower tiers of the network by expanding
individual nodes. If the user finds that a node that was expanded is not useful,
the user can collapse the node and move on to another portion of the network.

Ask your doctor if Evolvo is right for you
------------------------------------------

Evolvo cannot be used for any large, complex network. It can only be used for networks
that can be organized into tiers or layers.

Here's an example from proteomics. Let's say
there's a network whose nodes are proteins and edges represent significant similarity
between two proteins. This network has hundreds of thousands of nodes and millions of edges.
Highly similar proteins are grouped together into families. Families, in turn, can be
grouped together into superfamilies. At the other end of the spectrum,
some protein nodes can be decomposed into domains. This network
can be organized into tiers: superfamilies, families, proteins, and domains.
This network is well-suited for Evolvo. The user would first see the superfamilies and their
relationships first. The user can expand a specific superfamily to see its constituting families.
The user could also expand another superfamily and get another set of families. If relationships
exist between these two groups of families, Evolvo will show them. The user can drill down into
families and get proteins and domains.

Communication
-------------

Evolvo follows a client-server model. The server offers pieces of the network based on user requests.
The client visualizes the network and issues requests to the server when the user wants to see
a different portion of the network. How the server retrieves pieces of the network is irrelevant to
the client. The client communicates with the server by HTTP, and all requests and
network responses are in JSON.

Getting Started
===============

Included in this repository is an example server (_srv-example_) and a
client app for Cytoscape (_EvolvoApp_).

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
 At the URL prompt, enter `http://localhost:8000/replace` or `http://localhost:8000/augment`. Click *OK*.

 1. Right click on a node, and choose *Apps* > *Evolvo: Expand*.
 Choose *Apps* > *Evolvo: Collapse* to do the reverse.
