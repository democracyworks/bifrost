(ns bifrost.core-test
  (:require [clojure.test :refer :all]
            [bifrost.core :refer :all]
            [clojure.core.async :as async]))

(deftest interceptor-with-async-chan-test
  (testing "adds a key to the context where the response channel is"
    (let [key-test-ch (async/chan)
          async-interceptor (interceptor key-test-ch)
          enter (:enter async-interceptor)
          out-ctx (enter {:request {:request-method :get}})]
      (is (= 1 (count (:response-channels out-ctx))))))
  (testing "puts just the bifrosted request onto the request part of the channel"
    (let [request-only-test-ch (async/chan)
          async-interceptor (interceptor request-only-test-ch)
          enter (:enter async-interceptor)
          request {:request-method :get
                   :query-params {:test true}}
          ctx {:request request}]
      (enter ctx)
      (let [[response-ch request] (async/<!! request-only-test-ch)]
        (is (= request
               (ctx->bifrost-request ctx))))))
  (testing "takes a API-like response from the response-ch and puts a Ring-like response on the ctx"
    (let [response-test-ch (async/chan)
          async-interceptor (interceptor response-test-ch)
          {:keys [enter leave]} async-interceptor
          request {:request-method :get
                   :bifrost-params {:test true}}
          ctx {:request request}
          middle-ctx (enter ctx)]
      (let [[response-ch request] (async/<!! response-test-ch)]
        (async/>!! response-ch {:status :ok :bridge-endpoints #{"Midgard" "Asgard"}})
        (let [final-ctx (leave middle-ctx)]
          (is (= 200 (get-in final-ctx [:response :status])))
          (is (= {:bridge-endpoints #{"Midgard" "Asgard"}} (get-in final-ctx [:response :body])))))))
  (testing "will respond with a timeout if nothing happens on the response ch"
    (let [timeout-test-ch (async/chan)
          async-interceptor (interceptor timeout-test-ch)
          {:keys [enter leave]} async-interceptor
          request {:request-method :get
                   :bifrost-params {:test true}}
          ctx {:request request}
          middle-ctx (enter ctx)]
      (let [[response-ch request] (async/<!! timeout-test-ch)]
        (let [final-ctx (leave middle-ctx)]
          (is (= 504 (get-in final-ctx [:response :status])))
          (is (= "Bifrost timeout" (get-in final-ctx [:response :body])))))))
  (testing "will forward the ctx through if the response channel has been closed"
    (let [closed-test-ch (async/chan)
          async-interceptor (interceptor closed-test-ch)
          {:keys [enter leave]} async-interceptor
          request {:request-method :get
                   :bifrost-params {:test true}}
          ctx {:request request}
          middle-ctx (enter ctx)
          otherwise-created-ctx (merge middle-ctx
                                       {:response {:status :201 :body "Handled by someone else"}})]
      (let [[response-ch request] (async/<!! closed-test-ch)]
        (async/close! response-ch)
        (let [final-ctx (leave otherwise-created-ctx)]
          (is (= final-ctx otherwise-created-ctx)))))))

(deftest interceptor-with-fn-test
  (let [fn-with-response (fn [_]
                           (async/go
                             {:status :ok
                              :bridge-endpoints #{"Midgard" "Asgard"}}))
        async-identity (fn [arg]
                         (async/go arg))
        fn-timeout (fn [_]
                     (async/chan))
        fn-closed-chan (fn [_]
                         (let [c (async/chan)]
                           ;; fake a situation where the function
                           ;; closes the channel if it knows there's
                           ;; nothing worth doing
                           (async/close! c)
                           c))]
    (testing "adds a key to the context where the response channel is"
      (let [fn-interceptor (interceptor fn-with-response)
            enter (:enter fn-interceptor)
            out-ctx (enter {:request {:request-method :get}})]
        (is (= 1 (count (:response-channels out-ctx))))))
    (testing "calls the function with just the bifrosted request"
      (let [async-interceptor (interceptor async-identity)
            enter (:enter async-interceptor)
            request {:request-method :get
                     :query-params {:test true}}
            ctx {:request request}
            out-ctx (enter ctx)]
        (let [chan (-> out-ctx :response-channels vals first)
              bifrost-request (async/<!! chan)]
          (is (= bifrost-request
                 (ctx->bifrost-request ctx))))))
    (testing "takes a API-like response from the channel the function returns and puts a Ring-like response on the ctx"
      (let [{:keys [enter leave]} (interceptor fn-with-response)
            request {:request-method :get
                     :bifrost-params {:test true}}
            ctx {:request request}
            out-ctx (leave (enter ctx))]
        (is (= 200 (get-in out-ctx [:response :status])))
        (is (= {:bridge-endpoints #{"Midgard" "Asgard"}}
               (get-in out-ctx [:response :body])))))
    (testing "will respond with a timeout if nothing happens on the channel returned by the function"
      (let [{:keys [enter leave]} (interceptor fn-timeout)
            request {:request-method :get
                     :bifrost-params {:test true}}
            ctx {:request request}
            out-ctx (leave (enter ctx))]
        (is (= 504 (get-in out-ctx [:response :status])))
        (is (= "Bifrost timeout" (get-in out-ctx [:response :body])))))
    (testing "will forward the ctx through if the channel returned by the function has been closed"
      (let [{:keys [enter leave]} (interceptor fn-closed-chan)
            request {:request-method :get
                     :bifrost-params {:test true}}
            ctx {:request request}
            middle-ctx (enter ctx)
            otherwise-created-ctx (merge middle-ctx
                                         {:response
                                          {:status :201
                                           :body "Handled by someone else"}})
            out-ctx (leave otherwise-created-ctx)]
        (is (= out-ctx otherwise-created-ctx))))))

(deftest handler-with-fn-test
  (let [fn-with-response (fn [_]
                           (async/go
                             {:status :ok
                              :bridge-endpoints #{"Midgard" "Asgard"}}))
        async-identity (fn [arg]
                         (async/go arg))
        fn-timeout (fn [_]
                     (async/chan))
        fn-closed-chan (fn [_]
                         (let [c (async/chan)]
                           ;; fake a situation where the function
                           ;; closes the channel if it knows there's
                           ;; nothing worth doing
                           (async/close! c)
                           c))]
    (testing "adds a key to the context where the response channel is"
      (let [fn-interceptor (handler fn-with-response)
            enter (:enter fn-interceptor)
            out-ctx (enter {:request {:request-method :get}})]
        (is (= 1 (count (:response-channels out-ctx))))))
    (testing "calls the function with just the bifrosted request"
      (let [async-interceptor (handler async-identity)
            enter (:enter async-interceptor)
            request {:request-method :get
                     :query-params {:test true}}
            ctx {:request request}
            out-ctx (enter ctx)]
        (let [chan (-> out-ctx :response-channels vals first)
              bifrost-request (async/<!! chan)]
          (is (= bifrost-request
                 (ctx->bifrost-request ctx))))))
    (testing "takes a API-like response from the channel the function returns and puts a Ring-like response on the ctx"
      (let [{:keys [enter leave]} (handler fn-with-response)
            request {:request-method :get
                     :bifrost-params {:test true}}
            ctx {:request request}
            out-ctx (leave (enter ctx))]
        (is (= 200 (get-in out-ctx [:response :status])))
        (is (= {:bridge-endpoints #{"Midgard" "Asgard"}}
               (get-in out-ctx [:response :body])))))
    (testing "will respond with a timeout if nothing happens on the channel returned by the function"
      (let [{:keys [enter leave]} (handler fn-timeout)
            request {:request-method :get
                     :bifrost-params {:test true}}
            ctx {:request request}
            out-ctx (leave (enter ctx))]
        (is (= 504 (get-in out-ctx [:response :status])))
        (is (= "Bifrost timeout" (get-in out-ctx [:response :body])))))
    (testing "will respond with a closed channel response if the channel returned by the function has been closed"
      (let [{:keys [enter leave]} (handler fn-closed-chan)
            request {:request-method :get
                     :bifrost-params {:test true}}
            ctx {:request request}
            out-ctx (leave (enter ctx))]
        (is (= 500 (get-in out-ctx [:response :status])))
        (is (= "Channel was closed unexpectedly" (get-in out-ctx [:response :body])))))))

(deftest params-map-test
  (testing "GET/DELETE merges bifrost-params -> path-params -> query-params"
    (are [request-method]
        (= {:one 1, :two 2, :three 3, :b "bifrost", :p "path", :q "query"}
           (params-map {:request-method request-method
                        :bifrost-params {:one 1, :two 2, :three 3, :b "bifrost"}
                        :path-params {:two "two", :one "one", :p "path"}
                        :query-params {:one "uno", :q "query"}}))
      :get :delete))
  (testing "POST/PUT/PATCH merges bifrost-params -> path-params -> body-params ->
            json-params -> transit-params -> edn-params -> form-params ->
            query-params"
    (are [request-method]
        (= {:one 1, :two 2, :three 3, :bi "bifrost", :p "path", :q "query",
            :bo "body", :j "json", :t "transit", :e "edn", :f "form"}
           (params-map {:request-method request-method
                        :bifrost-params {:one 1, :two 2, :three 3, :bi "bifrost"}
                        :path-params {:two "two", :one "one", :p "path"}
                        :body-params {:bo "body"}
                        :json-params {:j "json"}
                        :transit-params {:t "transit"}
                        :edn-params {:e "edn"}
                        :form-params {:f "form"}
                        :query-params {:one "uno", :q "query"}}))
      :post :put :patch)))
