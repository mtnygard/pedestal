; Copyright 2013 Relevance, Inc.
; Copyright 2014 Cognitect, Inc.

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns io.pedestal.http.route.definition.table
  (:require [clojure.spec :as s]
            [io.pedestal.http.route.definition :as route-definition]
            [io.pedestal.http.route.path :as path]
            [io.pedestal.http.route.spec :as spec]
            [io.pedestal.interceptor :as interceptor]))

(defn parse-handlers
  [ctx]
  (let [solo     (get-in ctx [:handlers :handler])
        multiple (get-in ctx [:handlers :interceptors])
        handlers (or multiple [solo])]
    (assoc ctx
           :interceptors (mapv interceptor/interceptor handlers)
           :last-handler (last handlers))))

(defn apply-default-route-name
  [{:keys [route-name interceptors last-handler] :as ctx}]
  (if route-name
    ctx
    (let [default-route-name (cond
                               (:name last-handler) (:name last-handler)
                               (symbol? last-handler) (route-definition/symbol->keyword last-handler)
                               :else nil)]
      (assert default-route-name (str "the last interceptor does not have a name and there is no explicit :route-name."))
      (assoc ctx :route-name default-route-name))))

(defn- remove-empty-constraints
  [ctx]
  (apply dissoc ctx
         (filter #(empty? (ctx %)) [:path-constraints :query-constraints])))

(defn parse-constraints
  [{:keys [constraints path-params] :as ctx}]
  (let [path-param?                          (fn [[k v]] (some #{k} path-params))
        [path-constraints query-constraints] ((juxt filter remove) path-param? constraints)]
    (-> ctx
        (update :path-constraints  merge (into {} (map route-definition/capture-constraint path-constraints)))
        (update :query-constraints merge query-constraints)
        remove-empty-constraints)))

(s/def ::handlers        (s/or :interceptors (s/+ ::interceptor/interceptor)
                               :handler ::interceptor/interceptor))
(s/def ::options         (s/keys :opt-un [::spec/host ::spec/scheme ::spec/app-name ::spec/port]))
(s/def ::route-table-row (s/cat :path        ::spec/path
                                :method      ::spec/method
                                :handlers    ::handlers
                                :route-name  (s/? (s/cat :_ #(= :route-name %) :name keyword?))
                                :constraints (s/? (s/cat :_ #(= :constraints %) :constraints map?))))
(s/def ::route-table     (s/+ ::route-table-row))

(defn route-table-row
  [opts rownum {:keys [path] :as route-row}]
  (-> (merge route-row opts)
      (assoc :row rownum)
      (assoc :route-name  (get-in route-row [:route-name :name]))
      (assoc :constraints (get-in route-row [:constraints :constraints]))
      (merge (path/parse-path path))
      parse-handlers
      apply-default-route-name
      parse-constraints
      path/merge-path-regex))

(defn- map-vals [f m]
  (reduce-kv
   (fn [o k v] (assoc o k (f v)))
   (with-meta {} (meta m))
   m))

(defn ensure-unique-route-names [routes]
  (let [counts (keep (fn [[rn rs]] (when (> (count rs) 1) rn)) (group-by :route-name routes))]
    (assert (empty? counts) (str "Route names or handlers appear more than once in the route spec: " counts))))

(defn expand-routes
  "Given a route specification, produce and return a sequence of
  route-maps suitable as input to a RouterSpecification"
  [opts routes]
  (let [parsed-opts   (s/conform ::options opts)
        parsed-routes (s/conform ::route-table routes)]
    (assert (not= ::s/invalid parsed-opts)   (s/explain ::options     opts))
    (assert (not= ::s/invalid parsed-routes) (s/explain ::route-table routes))
    (ensure-unique-route-names parsed-routes)
    (route-definition/ensure-routes-integrity
     (map-indexed (partial route-table-row parsed-opts) parsed-routes))))

(s/fdef expand-routes
        :args [::route-table]
        :ret  ::spec/routes)
