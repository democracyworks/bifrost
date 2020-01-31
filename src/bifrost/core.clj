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

(def channel-closed-response
  {:response {:status 500
              :body "Channel was closed unexpectedly"}})

(defn response-channel-key []
  (keyword (gensym "bifrost-response-channel-")))

(defn interceptor*
  "The main interceptor logic. Saves a channel on the context in the `:enter`
  handler, and tries to take off of it in the `:leave` handler. Assocs a 504
  response if `timeout` is reached before the channel has a result.

  `->chan` is a function taking a context and returning an async channel that
  will eventually contain a response.

  `->response` is a function taking a (possibly nil) response that returns data
  to merge into the context."
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
  "Returns an async channel-based interceptor. Takes either a function
  returning a response channel, or a channel that accepts requests of the form
  `[response-channel ctx]` and places a single response on `response-channel`.

  Three results are possible:
  1. A response is available within `timeout`: coerces the response to a ring
     response and assocs it onto the context.
  2. A response is not available within `timeout`: assocs `timeout-response`
     onto the context.
  3. The channel closes without a response: returns context as-is on the
     assumption that another interceptor handled the response."
  ([fn-or-chan]
   (interceptor fn-or-chan default-timeout))
  ([fn-or-chan timeout]
   (if (fn? fn-or-chan)
     (fn-interceptor fn-or-chan timeout)
     (fn-interceptor (channel-adapter fn-or-chan) timeout))))

(defn fn-handler
  [f timeout]
  (interceptor* (comp f ctx->bifrost-request)
                (fn [response]
                  (or (api-response->ctx response)
                      channel-closed-response))
                timeout))

(defn handler
  "Like `interceptor`, but instead of ignoring closed channels, assocs
  `channel-closed-response` onto the context.

  This assumes that no other interceptor has access to the response channel.
  Generally speaking this should be used as a pedestal handler (i.e. last
  interceptor in the chain) to insure that no other interceptor has touched the
  response channel."
  ([fn-or-chan]
   (handler fn-or-chan default-timeout))
  ([fn-or-chan timeout]
   (if (fn? fn-or-chan)
     (fn-handler fn-or-chan timeout)
     (fn-handler (channel-adapter fn-or-chan) timeout))))
