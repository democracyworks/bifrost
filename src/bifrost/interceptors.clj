(ns bifrost.interceptors
  (:require [io.pedestal.interceptor :refer [interceptor]]))

(defn update-interceptor
  [type source-key-path target-key-path f & args]
  (interceptor
   (let [context-key (case type
                       :enter :request
                       :leave :response)]
     {type
      (fn [ctx]
        (let [source-context-path (cons context-key source-key-path)
              target-context-path (cons context-key target-key-path)
              old-value (get-in ctx source-context-path)
              new-value (apply f old-value args)
              new-ctx (assoc-in ctx target-context-path new-value)]
          (if (= source-context-path target-context-path)
            new-ctx
            (update-in new-ctx (butlast source-context-path)
                       dissoc (last source-context-path)))))})))

(defn update-in-request
  ([key-path f] (update-in-request key-path key-path f))
  ([source-key-path target-key-path f & args]
   (apply update-interceptor :enter source-key-path target-key-path f args)))

(defn update-in-response
  ([key-path f] (update-in-response key-path key-path f))
  ([source-key-path target-key-path f & args]
   (apply update-interceptor :leave source-key-path target-key-path f args)))
