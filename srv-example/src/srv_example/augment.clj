(ns srv-example.augment)

(use '[srv-example.util :only (json-response bad-request-response build-network)])

(def all-edges
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
  (let [is-edge-in-nodes
        (fn [[src trg]] (and (contains? nodes src)
                             (contains? nodes trg)))]
    (filter is-edge-in-nodes all-edges)))

(defn adj-edges [node]
  (let [is-node-in-edge
        (fn [[src trg]] (or (= node src)
                            (= node trg)))]
    (filter is-node-in-edge all-edges)))

(defn adj-nodes [node adj-edges]
  (let [opposite-node
        (fn [[src trg]] (if (= node src) trg src))]
    (map opposite-node adj-edges)))

(defn root-network []
  (build-network
    (edges-between root-nodes)))

(defn child-network [node]
  (build-network (adj-edges node)))

(def service-info
  {:action "action"})

(defn respond [params]
  (if (contains? params "node")
    (json-response (child-network (symbol (get params "node"))))
    (json-response (root-network) service-info)))
