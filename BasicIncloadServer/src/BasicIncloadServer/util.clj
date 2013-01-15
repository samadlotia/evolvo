(ns BasicIncloadServer.util)

(use '[cheshire.core :only [generate-string]])
(use '[ring.util.response :only [response content-type header status]])

(defn json-response [service-response & service-info]
  ((if (nil? service-info)
     identity
     #(header % "Incload-Info" (generate-string service-info)))
     (->
       (response (generate-string service-response))
       (content-type "application/json"))))

(defn bad-request-response [body]
  (->
    (response body)
    (status 400)))
