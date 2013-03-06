(ns srv-example.replace)

(use '[srv-example.util :only [json-response bad-request-response build-network]])
(use '[cheshire.core    :only [parse-string]])

(def root-net
  '[[ a n1]
    [n1  b]
    [n1  c]
    [n1  d]
    [n1 n2]])

(def sub-nets
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
  (contains? sub-nets node))

(def node-cols
  ["x" "y" "expandable"])

(defn node-info [node]
  (conj (node-locations node) (expandable? node)))

(defn any-node-in-edge? [nodes edge]
  (let [[src trg] edge]
    (or (contains? nodes src)
        (contains? nodes trg))))

(def service-info
  {:action "replace"
   :node-column "name"})

(defn root-network []
  (json-response
    (build-network root-net node-info node-cols)
    service-info))

(defn child-network [target extant-nodes]
  ;(prn target extant-nodes)
  (let [edges (sub-nets target)]
    (if (nil? edges)
      (bad-request-response "node is not expandable")
      (let [in-edges (:internal edges)
            ex-edges (:external edges)
            relevant-ex-edges (filter #(any-node-in-edge? extant-nodes %) ex-edges) ; only external edges that hit any extant-nodes
            all-edges (concat in-edges relevant-ex-edges)] ; internal edges + relevant external edges
        (json-response
            (build-network all-edges node-info node-cols))))))

(defn respond [params]
  ;(prn params)
  (if (contains? params "target")
    (let [target (symbol (params "target"))
          extant-nodes (params "extant-nodes")
          extant-nodes-syms (if extant-nodes (set (map symbol extant-nodes)) #{})]
      (child-network target extant-nodes-syms))
    (root-network)))
