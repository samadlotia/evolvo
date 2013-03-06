(ns srv-example.augment)

(use '[srv-example.util :only (json-response bad-request-response build-network)])
(use '[clojure.string   :only [split]])
(use '[cheshire.core    :only [parse-string]])

(def all-edges
  "These edges constitute the entire, fully-expanded network."
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

(def root-nodes
  "The nodes that constitute the root network; the root network's edges are inferred from all-edges."
  '#{a b c d})

(defn adj-edges [nodes edges internal-only]
  "Returns a seq of edges that are adjacent to the given nodes; nodes must be a set."
  (let [is-node-in-edge
        (fn [edge] ((if internal-only every? some)
                      #(contains? nodes %)
                      edge))]
    (filter is-node-in-edge edges)))

(defn extant-edges [target-node extant-nodes]
  (let [target-adj-edges (adj-edges #{target-node} all-edges false)
        target-adj-nodes (set (flatten target-adj-edges))
        extant-adj-edges (adj-edges extant-nodes all-edges false)]
    (adj-edges target-adj-nodes extant-adj-edges false)))

(defn root-network []
  (->
    root-nodes
    (adj-edges all-edges true)
    build-network))

(defn child-network [target-node extant-nodes]
  (->
    #{target-node}
    (adj-edges all-edges false)
    (concat (extant-edges target-node extant-nodes))
    build-network))

(def service-info
  {"action" "augment"})

(defn respond [params]
  (if (contains? params "target")
    (let [target (symbol (get params "target"))
          extant-nodes-str (get params "extant-nodes")
          extant-nodes (if extant-nodes-str (set (map symbol (parse-string extant-nodes-str))) #{})]
      (json-response (child-network target extant-nodes)))
    (json-response (root-network) service-info)))
