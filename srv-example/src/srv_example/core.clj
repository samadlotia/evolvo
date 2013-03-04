(ns srv-example.core)

(use '[ring.adapter.jetty     :only [run-jetty]])
(use '[ring.middleware.params :only [wrap-params]])
(use '[ring.util.response     :only [not-found response]])
(use '[cheshire.core          :only [parse-stream]])
(use '[clojure.java.io        :only [reader]])

(require 'srv-example.augment)
(require 'srv-example.replace)

(def services
  {"augment"      #'srv-example.augment/respond
   "replace"      #'srv-example.replace/respond
   })

(defn handler [request]
  (let [path (rest (clojure.string/split (:uri request) #"\/+"))]
    (if (empty? path)
      (not-found "no service specified")
      (let [service-name   (first path)
            service-params (if (= (:request-method request) :post)
                             (parse-stream (reader (:body request)))
                             (:query-params request))]
        (if (contains? services service-name)
          (let [service (get services service-name)]
            (service service-params))
          (not-found "invalid service name"))))))

(def app
  (->> handler
    (wrap-params)))

(defn -main []
  (ring.adapter.jetty/run-jetty app {:port 8000 :join? false}))
