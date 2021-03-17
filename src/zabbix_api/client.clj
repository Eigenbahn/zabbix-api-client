(ns zabbix-api.client
  "Client library to the [Zabbix HTTP API](https://www.zabbix.com/documentation/current/manual/api).

  All methods take a connection `conn` as a first argument. It's just a simple map in the form:
  ```clojure
  {:url \"<zabbix_base_url>\"}
  ```
  The :url field value must contain the base URL of the Zabbix instance. It is not expected to contain the \"api_jsonrpc.php\" part.

  E.g. to connect to a local Zabbix instance running under \"http://company.com/zabbix/\":
  ```clojure
  {:url \"http://company.com/zabbix\"}
  ```"
  (:require [clj-http.client :as http-client]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [jsonista.core :as json])
  (:use [slingshot.slingshot :only [try+]]))


(declare json-rpc-request-and-maybe-parse
         get-auth-token
         remove-nils)



;; DYNAMIC VARS

(def ^:dynamic content-level
  "Level of content returned for API response calls.
  Valid values:
  - `::http-client`: raw response from `clj-http.client`, good for debugging
  - `::body`: HTTP body parsing into a clojure data structure
  - `::data`: \"data\" part of the prometheus response
  - `::best`: only the most sensible data for each endpoint (default)"
  ::data)


(def ^:dynamic convert-result
  "If true, parse convert the results into more clojuresque data structures.
  Time series are converted into maps, their timestamps converted into inst and scalar values parsed.

  Default value is true.

  Gets ignored if [[content-level]] is `::http-client`"
  true)


(def ^:dynamic api-path
  "Constant part in the API url.
  Can be overridden e.g. in case of passing through a reverse proxy."
  "/api_jsonrpc.php")



;; HELPERS - PARAMS

(def generic-get-params-keywords
  (->> ["countOutput" "editable" "excludeSearch" "filter" "limit" "output" "search" "searchByAny" "searchWildcardsEnabled" "sortorder" "startSearch"]
       (map keyword)
       set))

(defn parse-generic-get-params [all-keyword-params]
  (let [get-params (into (empty all-keyword-params) (filter #(generic-get-params-keywords (first %)) all-keyword-params))
        get-params (into (empty get-params) (map (fn [[k v]] [(name k) v]) get-params))] ; convert back keywords into strings
    (-> get-params
        (assoc "filter" (or (get get-params "filter") {})))))

(defn parse-list-or-single-id-param [p]
  (when p
    (cond
      (coll? p) (map str p)
      :default (str p))))



;; GLOBAL

(defn api-version
  "Get the API version.

  No auth needed

  This corresponds to the [apiinfo.version](https://www.zabbix.com/documentation/current/manual/api/reference/apiinfo/version) method."
  [conn & {:keys [request-id]}]
  (json-rpc-request-and-maybe-parse conn
                                    "apiinfo.version"
                                    :request-id request-id))



(defn auth
  "Authenticate & retrieve an auth token.

  This corresponds to the [user.login](https://www.zabbix.com/documentation/current/manual/api#authentication) method."
  [conn & {:keys [user password
                  request-id]}]
  (let [user (or user (:user conn))
        password (or password (:password conn))]
    (json-rpc-request-and-maybe-parse conn
                                      "user.login"
                                      :params {"user" user
                                               "password" password}
                                      :request-id request-id)))



;; DATA MODEL

(defn get-templates
  "Retrieve the list of host templates.

  This corresponds to the [template.get](https://www.zabbix.com/documentation/current/manual/api/reference/template/get) method."
  [conn & {:keys [request-id] :as all-kw-args}]
  (let [auth-token (get-auth-token conn)]
    (json-rpc-request-and-maybe-parse conn
                                      "template.get"
                                      :params (parse-generic-get-params all-kw-args)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-hosts
  "Retrieve the list of hosts.

  This corresponds to the [host.get](https://www.zabbix.com/documentation/current/manual/api/reference/host/get) method."
  [conn & {:keys [request-id] :as all-kw-args}]
  (let [auth-token (get-auth-token conn)]
    (json-rpc-request-and-maybe-parse conn
                                      "host.get"
                                      :params (parse-generic-get-params all-kw-args)
                                      :auth auth-token
                                      :request-id request-id)))




;; VOLATILE DATA

(defn object-type->history-id [object-type]
  (get
   {:float 0
    :char  1
    :log   2
    :unint 3
    :text  4}
   object-type))

(defn get-history
  "Retrieve the list of hosts.

  This corresponds to the [history.get](https://www.zabbix.com/documentation/current/manual/api/reference/history/get) method."
  [conn & {:keys [object-type host-ids item-ids sort-by-field
                  request-id]
           :as all-kw-args
           :or {object-type :unint
                sort-by-field   "clock"}}]
  (let [auth-token (get-auth-token conn)
        history-id (object-type->history-id object-type)
        host-ids (parse-list-or-single-id-param host-ids)
        item-ids (parse-list-or-single-id-param item-ids)
        generic-get-params (parse-generic-get-params all-kw-args)]


    (json-rpc-request-and-maybe-parse conn
                                      "history.get"
                                      :params (into {"history" history-id
                                                     "hostids" host-ids
                                                     "itemids" item-ids
                                                     "sortfield" sort-by-field}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)
    ))




;; HELPERS - HTTP

(defn json-rpc-request-and-maybe-parse [conn method & {:keys [auth params request-id]
                                                       :or   {params {}}}]
  (let [url (str (:url conn) api-path)
        rq-body {"jsonrpc" "2.0"
                 "method"  method
                 "id"      (or request-id 1)
                 "auth"    auth
                 "params"  (remove-nils params)}
        raw-resp (http-client/post url {:accept :json
                                        :content-type :json
                                        :body (json/write-value-as-string rq-body)
                                        })]
    (if (= content-level ::http-client)
      raw-resp

      (let [body (-> raw-resp
                     :body
                     json/read-value)]
        (case content-level
          ::body
          body

          ::data
          (get body "result")

          (throw (ex-info "Unexpected `content-level`" {:ex-type ::unexpected-content-level,
                                                        :input content-level})))))))

(defn get-auth-token [conn]
  (binding [content-level ::data]
    (auth conn)))




;; HELPERS: GENERIC

(defn keep-vals-in-coll
  "Return new collection of same type as COLL with only elements whose values satisfy PREDICATE."
  [coll predicate]
  (when (not (coll? coll))
    (throw (ex-info "Argument `coll` is not a collection"
                    {:ex-type :unexpected-type,
                     :coll coll})))
  (let [predicate (if (map? coll)
                    (comp predicate val)
                    predicate)]
    (into (empty coll) (filter predicate coll))))

(defn remove-vals-in-coll
  "Return new collection of same type as COLL with elements whose values satisfy PREDICATE removed."
  [coll predicate]
  (keep-vals-in-coll coll (complement predicate)))

(defn remove-nils [coll]
  (remove-vals-in-coll coll nil?))
