(ns io.pedestal.http.route.spec
  (:require [clojure.spec :as s]
            [io.pedestal.interceptor :as interceptor])
  (:import java.util.regex.Pattern))



(s/def ::route-name        keyword?)
(s/def ::method            #{:any :get :put :post :delete :patch :options :head})
(s/def ::interceptors      (s/+ ::interceptor/interceptor))
(s/def ::path              string?)
(s/def ::path-re           #(instance? Pattern %))
(s/def ::path-parts        vector?)
(s/def ::matcher           fn?)

(s/def ::app-name          keyword?)
(s/def ::scheme            #{:http :https})
(s/def ::host              string?)
(s/def ::port              integer?)
(s/def ::path-params       (s/* keyword?))
(s/def ::path-constraints  map?)
(s/def ::query-constraints map?)

(s/def ::route (s/keys :req-un [::route-name ::method ::interceptors ::path-re ::path-parts ::matcher]
                       :opt-un [::app-name ::scheme ::host ::port ::path-params ::path-constraints ::query-constraints]))

(s/def ::routes (s/* ::route))
