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
  (when api-response
    (let [status (response->http-status api-response)]
      {:response (-> api-response
                     (dissoc :status)
                     ring-resp/response
                     (ring-resp/status status))})))

(def timeout-response
  {:response {:status 504
              :body "Bifrost timeout"}})


(defn response-channel-key []
  (keyword (gensym "bifrost-response-channel-")))

(defn interceptor*
  [->chan ->response timeout]
  (let [response-channel-key (response-channel-key)]
    (interceptor/interceptor
     {:name ::interceptor
      :enter
      (fn [ctx]
        (assoc-in ctx [:response-channels response-channel-key]
                  (->chan ctx)))
      :leave
      (fn [ctx]
        (let [response (async/alt!!
                        (async/timeout timeout)
                        timeout-response

                        (get-in ctx [:response-channels response-channel-key])
                        ([r]
                         (->response r)))]
          (merge ctx response)))})))

(defn fn-interceptor
  [f timeout]
  (interceptor* (comp f ctx->bifrost-request)
                api-response->ctx
                timeout))

(defn- channel-adapter
  "Takes an async `channel` and returns a function that can be used with
  `interceptor*`."
  [channel]
  (let [request-ch (async/chan 1)]
    (async/pipe request-ch channel)
    (fn [ctx]
      (let [response-channel (async/chan 1)]
        (async/>!! request-ch [response-channel ctx])
        response-channel))))

(defn interceptor
  ([fn-or-chan]
   (interceptor fn-or-chan default-timeout))
  ([fn-or-chan timeout]
   (if (fn? fn-or-chan)
     (fn-interceptor fn-or-chan timeout)
     (fn-interceptor (channel-adapter fn-or-chan) timeout))))
