(ns BasicIncloadServer.expand_and_replace)

(use '[BasicIncloadServer.util :only [json-response bad-request-response]])

(def root-net
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
  '{a   [120  30]
    b   [  0  60]
    c   [  0  30]
    d   [  0   0]
    n1  [ 60  30]
    n2  [ 60 100]
    n11 [ 30  30]
    n12 [ 60  30]
    n13 [ 90  30]
    n14 [120  30]
    n21 [ 60  60]
    n22 [ 60  90]
    n23 [ 30  90]
    n24 [  0  90]})

(defn expandable? [node]
  (contains? subnets node))

(defn any-node-in-edge? [nodes edge]
  (let [[src trg] edge]
    (or (contains? nodes src)
        (contains? nodes trg))))

(defn node-sym-to-details [node-sym]
  (let [[x y] (node-locations node-sym)]
    {node-sym {:x x :y y :expandable? (expandable? node-sym)}}))

(defn mk-network [nodes edges]
  {"nodes" (map node-sym-to-details nodes)
   "edges" edges})

(defn mk-root-network []
  (let [edges root-net]
    (mk-network (distinct (flatten edges))
                edges)))

(defn mk-subnetwork [node expanded-nodes]
  (let [edges (subnets node)]
    (if (nil? edges)
      (bad-request-response "node is not expandable")
      (let [in-edges (:internal edges)
            ex-edges (:external edges)
            relevant-ex-edges (filter #(any-node-in-edge? expanded-nodes %) ex-edges)
            all-edges (concat in-edges relevant-ex-edges)]
        (json-response (mk-network (distinct (flatten all-edges))
                                   all-edges))))))

(def service-info
  {:input "all-expanded-nodes"
   :action "expand-and-replace"})

(defn respond [params]
  (if (contains? params "node")
    (let [node (symbol (params "node"))
          expanded-nodes (set (map symbol (clojure.string/split (params "expanded-nodes") #",")))]
      (mk-subnetwork node expanded-nodes))
    (json-response (mk-root-network) service-info)))
