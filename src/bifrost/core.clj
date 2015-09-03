(ns bifrost.core
  (:require [io.pedestal.interceptor :refer [IntoInterceptor interceptor]]
            [clojure.core.async :as async]
            [ring.util.response :as ring-resp]))

(def ^:dynamic *response-timeout* 10000)

(def timeout-response
  {:status :error
   :error {:type :timeout
           :duration *response-timeout*}})

(defn error-response->http-status
  [error-response]
  (case (:type error-response)
    :semantic 400
    :validation 400
    :not-found 404
    :server 500
    :timeout 504
    500))

(defn response->http-status
  [response]
  (case (:status response)
    :ok 200
    :created 201
    :error (error-response->http-status (:error response))
    500))

(defn params-map
  [request]
  (case (:request-method request)
    (:get :delete) (merge (:query-params request)
                          (:path-params request)
                          (:bifrost-params request))
    (:post :put :patch) (merge (:query-params request)
                               (:form-params request)
                               (:edn-params request)
                               (:transit-params request)
                               (:json-params request)
                               (:body-params request)
                               (:path-params request)
                               (:bifrost-params request))))

(defn async-interceptor
  [channel]
  (interceptor
   {:enter
    (fn [ctx]
      (let [response-channel (async/chan 1)
            request (:request ctx)]
        (async/>!! channel [response-channel (params-map request)])
        (async/go
          (let [response (async/alt! (async/timeout *response-timeout*) timeout-response
                                     response-channel ([r] r))
                status (response->http-status response)]
            (assoc ctx :response
                   (-> response
                       (dissoc :status)
                       ring-resp/response
                       (ring-resp/status status)))))))}))

(extend-protocol IntoInterceptor
  clojure.core.async.impl.channels.ManyToManyChannel
  (-interceptor [ch] (async-interceptor ch)))
