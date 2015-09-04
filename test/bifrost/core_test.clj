(ns bifrost.core-test
  (:require [clojure.test :refer :all]
            [bifrost.core :refer :all]
            [clojure.core.async :as async]))

(deftest interceptor-test
  (testing "adds a key to the context where the response channel is"
    (let [key-test-ch (async/chan)
          async-interceptor (interceptor key-test-ch)
          enter (:enter async-interceptor)
          out-ctx (enter {:request {:request-method :get}})]
      (is (get-in out-ctx [:response-channels :key-test-ch]))))
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
          (is (= 500 (get-in final-ctx [:response :status])))
          (is (= "Timeout" (get-in final-ctx [:response :body])))))))
  (testing "will forward the ctx through if the response channel has been closed"
    (let [closed-test-ch (async/chan)
          async-interceptor (interceptor closed-test-ch)
          {:keys [enter leave]} async-interceptor
          request {:request-method :get
                   :bifrost-params {:test true}}
          ctx {:request request}
          _ (enter ctx)
          otherwise-created-ctx {:response {:status :201 :body "Handled by someone else"}}]
      (let [[response-ch request] (async/<!! closed-test-ch)]
        (async/close! response-ch)
        (let [final-ctx (leave otherwise-created-ctx)]
          (is (= final-ctx otherwise-created-ctx)))))))

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
