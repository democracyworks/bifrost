(ns bifrost.core
  (:require [io.pedestal.interceptor :as interceptor]
            [clojure.core.async :as async]
            [ring.util.response :as ring-resp]))

(def default-timeout 10000)

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

(defn api-response->ctx
  [api-response]
  (let [status (response->http-status api-response)]
    {:response (-> api-response
                   (dissoc :status)
                   ring-resp/response
                   (ring-resp/status status))}))

(defn async-interceptor
  ([f]
   (async-interceptor f (gensym)))
  ([f response-channel-key]
   (async-interceptor f
                      response-channel-key
                      default-timeout))
  ([f response-channel-key timeout]
   (interceptor/interceptor
    {:enter
     (fn [ctx]
       (let [response-channel (f (ctx->bifrost-request ctx))]
         (assoc-in ctx [:response-channels response-channel-key] response-channel)))
     :leave
     (fn [ctx]
       (if-let [response (async/alt!!
                           (async/timeout timeout)
                           {:response {:status 504
                                       :body "Bifrost timeout"}}

                           (get-in ctx [:response-channels response-channel-key])
                           ([r] (api-response->ctx r)))]
         (merge ctx response)
         ctx))})))
