(ns bifrost.core
  (:require [io.pedestal.interceptor :as interceptor]
            [clojure.core.async :as async]
            [ring.util.response :as ring-resp]))

(def ^:dynamic *response-timeout* 10000)

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

(defn ctx->bifrost-request [ctx]
  (params-map (:request ctx)))

(def interceptor-xf
  (map (fn [[response-ch ctx]]
         [response-ch (ctx->bifrost-request ctx)])))

(defn api-response->ctx
  [api-response]
  (let [status (response->http-status api-response)]
    {:response (-> api-response
                   (dissoc :status)
                   ring-resp/response
                   (ring-resp/status status))}))

(def api-response-xf (map api-response->ctx))

(defn async-interceptor
  ([channel]
   (async-interceptor channel (gensym)))
  ([channel response-channel-key]
   (async-interceptor channel response-channel-key (map identity)))
  ([channel response-channel-key response-channel-xf]
   (interceptor/interceptor
    {:enter
     (fn [ctx]
       (let [response-channel (async/chan 1 response-channel-xf)]
         (async/>!! channel [response-channel ctx])
         (assoc-in ctx [:response-channels response-channel-key] response-channel)))
     :leave
     (fn [ctx]
       (if-let [response (async/alt!!
                           (async/timeout *response-timeout*) {:response {:status 504 :body "Timeout"}}
                           (get-in ctx [:response-channels response-channel-key]) ([r] r))]
         (merge ctx response)
         ctx))})))

(defmacro interceptor
  [channel]
  (let [response-channel-key (keyword channel)]
    `(let [request-ch# (async/chan 1 interceptor-xf)]
       (async/pipe request-ch# ~channel)
       (async-interceptor request-ch# ~response-channel-key api-response-xf))))
