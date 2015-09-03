(ns bifrost.core-test
  (:require [clojure.test :refer :all]
            [bifrost.core :refer :all]
            [clojure.core.async :as async]))

(deftest async-interceptor-test
  (testing "puts incoming requests onto its channel arg"
    (let [ch (async/chan)
          async-interceptor (async-interceptor ch)
          request {:path-params {:foo "bar"}
                   :request-method :get}]
      (async/thread
        ((:enter async-interceptor) {:request request}))
      (let [[response-ch request-message] (async/alt!! ch ([r] r)
                                                       (async/timeout 1000) ::timeout)]
        (assert (not= request-message ::timeout))
        (async/close! response-ch)
        (is (= (:path-params request) request-message)))))
  (testing ":status :ok response results in 200 HTTP status response"
    (let [ch (async/chan)
          async-interceptor (async-interceptor ch)
          request {:path-params {:foo "bar"}
                   :request-method :get}
          _ (async/take! ch (fn [[response-ch _]] (async/put! response-ch
                                                              {:status :ok})))
          interceptor-response-ch ((:enter async-interceptor) {:request request})]
      (let [{:keys [response]} (async/alt!! interceptor-response-ch ([r] r)
                                          (async/timeout 1000) ::timeout)]
          (assert (not= response ::timeout))
          (is (= 200 (:status response))))))
  (testing ":status :error response results in 500 HTTP status response"
    (let [ch (async/chan)
          async-interceptor (async-interceptor ch)
          request {:path-params {:foo "bar"}
                   :request-method :get}
          _ (async/take! ch (fn [[response-ch _]] (async/put! response-ch
                                                              {:status :error})))
          interceptor-response-ch ((:enter async-interceptor) {:request request})]
      (let [{:keys [response]} (async/alt!! interceptor-response-ch ([r] r)
                                          (async/timeout 1000) ::timeout)]
          (assert (not= response ::timeout))
          (is (= 500 (:status response)))))))

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
