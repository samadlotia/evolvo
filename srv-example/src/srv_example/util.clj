(ns srv-example.util)

(use '[clojure.string     :only [capitalize]])
(use '[cheshire.core      :only [generate-string]])
(use '[ring.util.response :only [response content-type header status]])

(defn add-header [service-info]
  (if (nil? service-info)
    identity
    (fn [response]
      (doseq [[k v] service-info]
        (header response (str "Evolvo-" (capitalize k)) v)))))

(defn json-response [service-response & [service-info]]
  (->
    (response (generate-string service-response))
    (content-type "application/json")
    ((add-header service-info))))

(defn bad-request-response [body]
  (->
    (response body)
    (status 400)))

(defn build-network [edges & [node-attrs node-attrs-header]]
  (let [nodes-array (distinct (flatten edges))
        node-indices (zipmap nodes-array (range))]
    {:nodes
     (if (or (nil? node-attrs) (nil? node-attrs-header))
       (cons '[name]
             (map vector nodes-array))
       (cons (cons 'name node-attrs-header)
             (map #(cons % (node-attrs %)) nodes-array)))
     :edges (cons '[src trg] (map (fn [[src trg]] [(node-indices src) (node-indices trg)]) edges))
     :expand-on-node-attribute "name"}))
