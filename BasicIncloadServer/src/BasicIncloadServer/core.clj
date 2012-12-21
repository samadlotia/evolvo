(ns BasicIncloadServer.core)

(use 'cheshire.core)

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

(defn json-response [response]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (generate-string response)})

(defn handler [request]
  (json-response 
    (let [path (clojure.string/split (:uri request) #"\/+")]
      (if (= (count path) 0)
        {"nodes" root-nodes
         "edges" (edges-between root-nodes)}
        (let [node (symbol (path 1))
              adj-edges-to-node (adj-edges node)]
          {"nodes" (adj-nodes node adj-edges-to-node)
           "edges" adj-edges-to-node})))))
