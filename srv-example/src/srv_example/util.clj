(ns srv-example.util)

(use '[clojure.string     :only [capitalize]])
(use '[cheshire.core      :only [generate-string]])
(use '[ring.util.response :only [response content-type header status]])

(defn add-service-headers [response service-info]
  (if (empty? service-info)
    response
    (let [[header-name header-val] (first service-info)
          proper-header-name (->> header-name
                               name
                               (str "Evolvo-"))]
      (add-service-headers
        (header response proper-header-name header-val)
        (rest service-info)))))

(defn json-response [service-response & [service-info]]
  (-> service-response
    generate-string
    response
    (content-type "application/json")
    (add-service-headers service-info)))

(defn bad-request-response [body]
  (->
    (response body)
    (status 400)))

(defn build-network [edges & [node-attrs node-attrs-header]]
  (let [nodes-array (distinct (flatten edges))
        node-indices (zipmap nodes-array (range))]
    [
     (if (or (nil? node-attrs) (nil? node-attrs-header))
       (cons '[name]
             (map vector nodes-array))
       (cons (cons 'name node-attrs-header)
             (map #(cons % (node-attrs %)) nodes-array)))
     (cons '[src trg] (map (fn [[src trg]] [(node-indices src) (node-indices trg)]) edges))
     []
     ]))
