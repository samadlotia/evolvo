(ns srv-example.replace)

(use '[srv-example.util :only [json-response bad-request-response build-network]])

(def rootnet
  '[[ a n1]
    [n1  b]
    [n1  c]
    [n1  d]
    [n1 n2]])

(def subnets
  '{
    n1 {:internal [
                   [n11 n12]
                   [n12 n13]
                   [n13 n14]
                   ]
        :external [
                   [  a n11]
                   [n12  n2]
                   [n12 n21]
                   [n14   b]
                   [n14   c]
                   [n14   d]
                   ]}

    n2 {:internal [
                   [n24 n23]
                   [n23 n22]
                   [n22 n21]
                   ]
        :external [
                   [n21  n1]
                   [n21 n12]
                   ]}
    })
   

(def node-locations
  ; name   x   y
  '{a   [360  90]
    b   [  0 180]
    c   [  0  90]
    d   [  0   0]
    n1  [250  90]
    n2  [250 300]
    n11 [300  90]
    n12 [250  90]
    n13 [200  90]
    n14 [150  90]
    n21 [250 180]
    n22 [250 240]
    n23 [300 240]
    n24 [360 240]})

(defn expandable? [node]
  (contains? subnets node))

(def node-cols
  ["x" "y" "IncloadExpandable"])

(defn node-info [node]
  (conj (node-locations node) (expandable? node)))

(defn any-node-in-edge? [nodes edge]
  (let [[src trg] edge]
    (or (contains? nodes src)
        (contains? nodes trg))))

(def service-info
  {:action "replace"
   :node-column "name"})

(defn mk-root-network []
  (json-response
    (build-network rootnet node-info node-cols)
    service-info))

(defn mk-subnetwork [node expanded-nodes]
  (let [edges (subnets node)]
    (if (nil? edges)
      (bad-request-response "node is not expandable")
      (let [in-edges (:internal edges)
            ex-edges (:external edges)
            relevant-ex-edges (filter #(any-node-in-edge? expanded-nodes %) ex-edges)
            all-edges (concat in-edges relevant-ex-edges)]
        (json-response
            (build-network all-edges node-info node-cols))))))

(defn respond [params]
  (if (empty? params)
    (if (and (contains? params "node")
             (contains? params "nodes"))
      (let [node  (symbol (params "node"))
            nodes (set (map symbol (params "nodes")))]
        (mk-subnetwork node (set nodes)))
      (bad-request-response "no node or nodes specifid"))
    (mk-root-network)))