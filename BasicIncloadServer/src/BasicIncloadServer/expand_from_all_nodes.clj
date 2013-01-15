(ns BasicIncloadServer.expand_from_all_nodes)

(use '[BasicIncloadServer.util :only [json-response bad-request-response]])

(def network
  '[[d b]
    [a b]
    [b c]
    [d j]
    [a g]
    [e c]
    [g j]
    [g e]
    [e f]
    [j k]
    [k e]
    [k h]
    [e i]
    [f i]
    [j o]
    [e n]
    [k l]
    [h l]
    [h i]
    [o n]
    [i m]
    [c f]
    [b k]])

(def root-nodes (set '[a b c d]))

(defn edges-between [nodes]
  (let [is-edge-in-nodes (fn [[src trg]] (and (contains? nodes src) (contains? nodes trg)))]
    (filter is-edge-in-nodes network)))

(defn adj-edges [node]
  (let [is-node-in-edge (fn [[src trg]] (or (= node src) (= node trg)))]
    (filter is-node-in-edge network)))

(defn adj-nodes [node adj-edges]
  (let [opposite-node (fn [[src trg]] (if (= node src) trg src))]
    (map opposite-node adj-edges)))

(defn sym-edges-to-indices [nodes edges]
  (let [indices (zipmap nodes (range))]
    (map (fn [[src trg]] [(get indices src)
                          (get indices trg)])
         edges)))

(defn mk-network [nodes edges]
  {"nodes" nodes
   "edges" #_(sym-edges-to-indices nodes edges) edges})

(defn root-network []
  (mk-network
    root-nodes
    (edges-between root-nodes)))

(defn child-network [node expanded-nodes]
  (let [edges (edges-between
                (conj
                  (set (concat expanded-nodes
                               (adj-nodes node (adj-edges node))))
                  node))]
    (mk-network
      (distinct (flatten edges))
      edges)))

(def service-info
  {:input "all-expanded-nodes"
   :action "expand"})

(defn respond [params]
  (if (contains? params "expanded-nodes")
    (let [expanded-nodes  (map symbol (clojure.string/split (params "expanded-nodes") #","))
          node            (symbol (params "node"))]
        (json-response (child-network node expanded-nodes)))
    (json-response (root-network) service-info)))
