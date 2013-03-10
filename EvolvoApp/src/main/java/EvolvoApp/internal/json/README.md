The JSON Network Specification
==============================

This is a description of a format for representing networks in JSON.

Examples
========

The Empty Network
-----------------

At the most minimal level, the following specifies
an empty network.

    [      // a network is aways an array of three tables; here they're empty
      [],  // table of nodes
      [],  // a table of edges
      []   // a table of network attributes
    ]

Notes:
 - Networks must always be an array that has only three elements that are arrays.

Just the nodes
--------------

Here we create the nodes *Alex*, *Kristina*, *Anders*, *Justin*,
and *Samad*. The nodes have *Married* and *Origin* attributes.

The *Name* column has the string type;
the *Married* column has the boolean type; the *Origin* column
has the string type.

There are no edges in the network nor any network attributes.

    [    // the network array
      [  // a table of nodes
        ["Name"      , "Married", "Origin"],
        ["Alex"      , false    , "East Bay"],
        ["Kristina"  , true     , "Sweden"],
        ["Anders"    , true     , "Michigan"],
        ["Justin"    , false    , "Central California"],
        ["Samad"     , false    , "South Bay"],
      ],
      [], // an empty table of edges
      []  // an empty table of network attributes
    ]

Notes:
 - Headers:
    - The first column in the node table is the *header*. Table headers only specify name columns.
      They do not specify any rows or cell values of the table.
    - The header array must only contain strings and cannot have null values or empty strings.
 - Cell values:
    - The values of an entire column must all have the same type.
    - Except for the header, cell values can be null.
    - Besides strings and booleans, cell values can be whole numbers and floating numbers.
    - Cell values cannot be arrays or objects.

Some edges
----------

Here we create some edges -- they're all just edges between Alex
and everyone else.

    [
      [
        ["Name"      , "Married", "Origin"            ],
        ["Alex"      , false    , "East Bay"          ], // index 0
        ["Kristina"  , true     , "Sweden"            ], // index 1
        ["Anders"    , true     , "Michigan"          ], // index 2
        ["Justin"    , false    , "Central California"], // index 3
        ["Samad"     , false    , "South Bay"         ], // index 4
      ],
      [
        ["Source", "Target", "Years working together"],
        [0       , 1       , 8.0                     ],
        [2       , 0       , 0.2                     ],
        [0       , 3       , 0.8                     ],
        [4       , 0       , 0.8                     ],
      ],
      []
    ]

Notes:
 - The edge table must have at a minimum two columns: the source and target.
 - The first and second columns are always used to designate indices in the node table.
 - Nodes in the edge table are referred to their index in the node table. The first
   node in the table has index 0; the second has index 1; etc. Note that the first
   node does *not* have index 1, because the header column is not included in the index
   range.
 - The source and target column values must always be whole numbers. Null values
   are not accepted. They must refer to node indices.
 - The columns "Source" and "Target" and not created. The names of
   the first two columns are always ignored. But they cannot be null or empty strings.
 - Edges can have attributes just like nodes. In this case, edges just have a single attribute,
   *Years working together* with a floating number type.
