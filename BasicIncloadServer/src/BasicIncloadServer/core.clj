(ns BasicIncloadServer.core)

(use '[ring.adapter.jetty :only [run-jetty]])
(use '[ring.middleware.params :only [wrap-params]])
(use '[ring.util.response :only [not-found response]])
(use '[cheshire.core :only [parse-stream]])
(use '[clojure.java.io :only [reader]])

(require 'BasicIncloadServer.expand_from_node)
(require 'BasicIncloadServer.expand_from_all_nodes)
(require 'BasicIncloadServer.expand_and_replace)

(def services
  {"expand_from_node"      #'BasicIncloadServer.expand_from_node/respond
   "expand_from_all_nodes" #'BasicIncloadServer.expand_from_all_nodes/respond
   "expand_and_replace"    #'BasicIncloadServer.expand_and_replace/respond
   })

(defn service-meta-info [service-sym]
  (select-keys (meta service-sym) [:input-type :output-type]))

(defn handler-x [request]
  (println request)
  (response "{}"))

(defn handler [request]
  (let [path (rest (clojure.string/split (:uri request) #"\/+"))]
    (if (nil? path)
      (not-found "no method specified")
      (let [service-name   (first path)
            service-params (if (= (:request-method request) :post)
                             (parse-stream (reader (:body request)))
                             (:query-params request))]
        (println service-params)
        (if (contains? services service-name)
          (let [service (get services service-name)]
            (service service-params))
          (not-found "invalid method"))))))

(def app
  (->> handler
    (wrap-params)))

(defn -main []
  (ring.adapter.jetty/run-jetty app {:port 8000 :join? false}))
