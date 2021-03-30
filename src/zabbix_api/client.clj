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
            [jsonista.core :as json]
            [tick.alpha.api :as t])
  (:use [slingshot.slingshot :only [try+]]))


(declare member?
         json-rpc-request-and-maybe-parse
         get-auth-token
         remove-nils ensure-inst-ts
         clojurize-timed-collection

         parse-generic-get-params
         parse-list-or-single-id-param

         object-type->history-id
         eval-type->id eval-operator->id
         event-source-type->source-id event-object-type->object-id
         severity->id serialize-zabbix-tag-filter)



;; DYNAMIC VARS

(def ^:dynamic content-level
  "Level of content returned for API response calls.
  Valid values:
  - `::http-client`: raw response from `clj-http.client`, good for debugging
  - `::body`: HTTP body parsing into a clojure data structure
  - `::data`: \"data\" part of the prometheus response
  - `::best`: only the most sensible data for each endpoint (default)"
  ::best)


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



;; API: GLOBAL

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



;; API: DATA MODEL

(defn get-templates
  "Retrieve the list of host templates.

  This corresponds to the [template.get](https://www.zabbix.com/documentation/current/manual/api/reference/template/get) method.
  See also doc for the [template](https://www.zabbix.com/documentation/current/manual/api/reference/template/object) object."
  [conn & {:keys [template-ids parent-template-ids group-ids host-ids graph-ids item-ids trigger-ids
                  with-items with-triggers with-graphs with-httptests
                  eval-type tag-filters
                  sort-by-fields
                  request-id]
           :as all-kw-args}]
  (let [auth-token (get-auth-token conn)

        template-ids (parse-list-or-single-id-param template-ids)
        parent-template-ids (parse-list-or-single-id-param parent-template-ids)
        group-ids (parse-list-or-single-id-param group-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        graph-ids (parse-list-or-single-id-param graph-ids)
        item-ids (parse-list-or-single-id-param item-ids)
        trigger-ids (parse-list-or-single-id-param trigger-ids)

        eval-type-id (eval-type->id eval-type)
        tag-filters (into (empty tag-filters) (map serialize-zabbix-tag-filter tag-filters))

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "template.get"
                                      :params (into {"templateids" template-ids
                                                     "parentTemplateids" parent-template-ids
                                                     "groupids" group-ids
                                                     "hostids" host-ids
                                                     "graphids" graph-ids
                                                     "itemids" item-ids
                                                     "triggerids" trigger-ids

                                                     "with_items" with-items
                                                     "with_triggers" with-triggers
                                                     "with_graphs" with-graphs
                                                     "with_httptests" with-httptests

                                                     "evaltype" eval-type-id
                                                     "tags" tag-filters

                                                     "sortfield" sort-by-fields}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-applications
  "Retrieve the list of applications.

  This corresponds to the [application.get](https://www.zabbix.com/documentation/current/manual/api/reference/application/get) method.
  See also doc for the [application](https://www.zabbix.com/documentation/current/manual/api/reference/application/object) object."
  [conn & {:keys [application-ids group-ids template-ids host-ids item-ids
                  inherited templated
                  hosts-q items-q discovery-rule-q application-discovery-q
                  sort-by-fields
                  request-id] :as all-kw-args}]
  (let [auth-token (get-auth-token conn)

        application-ids (parse-list-or-single-id-param application-ids)
        group-ids (parse-list-or-single-id-param group-ids)
        template-ids (parse-list-or-single-id-param template-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        item-ids (parse-list-or-single-id-param item-ids)

        generic-get-params (parse-generic-get-params all-kw-args)]

    (json-rpc-request-and-maybe-parse conn
                                      "application.get"
                                      :params (into {"itemids" item-ids
                                                     "groupids" group-ids
                                                     "templateids" template-ids
                                                     "hostids" host-ids
                                                     "applicationids" application-ids

                                                     "inherited" inherited
                                                     "templated" templated

                                                     "selectHosts" hosts-q
                                                     "selectItems" items-q
                                                     "selectDiscoveryRule" discovery-rule-q
                                                     "selectApplicationDiscovery" application-discovery-q

                                                     "sortfield" sort-by-fields}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-hosts
  "Retrieve the list of hosts.

  This corresponds to the [host.get](https://www.zabbix.com/documentation/current/manual/api/reference/host/get) method.
  See also doc for the [host](https://www.zabbix.com/documentation/current/manual/api/reference/host/object) object."
  [conn & {:keys [group-ids application-ids discovered-service-ids
                  graph-ids host-ids web-check-ids interface-ids
                  item-ids maintenance-ids proxy-ids template-ids trigger-ids
                  monitored-hosts proxy-hosts templated-hosts ; flags

                  severities
                  eval-type tag-filters
                  inherited-tags        ; bool

                  filter search inventory-search limit-for-subselects sort-by-fields

                  request-id]
           :as all-kw-args}]
  (let [auth-token (get-auth-token conn)

        group-ids (parse-list-or-single-id-param group-ids)
        application-ids (parse-list-or-single-id-param application-ids)
        discovered-service-ids (parse-list-or-single-id-param discovered-service-ids)
        graph-ids (parse-list-or-single-id-param graph-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        web-check-ids (parse-list-or-single-id-param web-check-ids)
        interface-ids (parse-list-or-single-id-param interface-ids)
        item-ids (parse-list-or-single-id-param item-ids)
        maintenance-ids (parse-list-or-single-id-param maintenance-ids)
        proxy-ids (parse-list-or-single-id-param proxy-ids)
        template-ids (parse-list-or-single-id-param template-ids)
        trigger-ids (parse-list-or-single-id-param trigger-ids)

        severities (when severities
                     (cond
                       (coll? severities) (map severity->id severities)
                       :default (severity->id severities)))

        eval-type-id (eval-type->id eval-type)
        tag-filters (into (empty tag-filters) (map serialize-zabbix-tag-filter tag-filters))

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "host.get"
                                      :params (into {"groupids" group-ids
                                                     "applicationids" application-ids
                                                     "dserviceids" discovered-service-ids
                                                     "graphids" graph-ids
                                                     "hostids" host-ids
                                                     "httptestids" web-check-ids
                                                     "interfaceids" interface-ids
                                                     "itemids" item-ids
                                                     "maintenanceids" maintenance-ids
                                                     "proxyids" proxy-ids
                                                     "templateids" template-ids
                                                     "triggerids" trigger-ids

                                                     "monitored_hosts" monitored-hosts
                                                     "proxy_hosts" proxy-hosts
                                                     "templated_hosts" templated-hosts

                                                     "severities" severities

                                                     "evaltype" eval-type-id
                                                     "tags" tag-filters

                                                     "inheritedTags" inherited-tags

                                                     "filter" filter
                                                     "search" search
                                                     "searchInventory" inventory-search
                                                     "limitSelects" limit-for-subselects
                                                     "sortfield" sort-by-fields}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-items
  "Retrieve the list of items.

  This corresponds to the [item.get](https://www.zabbix.com/documentation/current/manual/api/reference/item/get) method.
  See also doc for the [item](https://www.zabbix.com/documentation/current/manual/api/reference/item/object) object."
  [conn & {:keys [item-ids group-ids template-ids host-ids
                  proxy-ids interface-ids graph-ids trigger-ids application-ids
                  ;; webitems
                  inherited templated monitored
                  group host application
                  with-triggers
                  hosts-q interfaces-q triggers-q applications-q
                  discovery-rule-q item-discovery-q preprocessing-q
                  filter limit-for-subselects sort-by-fields

                  request-id]
           :as all-kw-args}]
  (let [auth-token (get-auth-token conn)

        item-ids (parse-list-or-single-id-param item-ids)
        group-ids (parse-list-or-single-id-param group-ids)
        template-ids (parse-list-or-single-id-param template-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        proxy-ids (parse-list-or-single-id-param proxy-ids)
        interface-ids (parse-list-or-single-id-param interface-ids)
        graph-ids (parse-list-or-single-id-param graph-ids)
        trigger-ids (parse-list-or-single-id-param trigger-ids)
        application-ids (parse-list-or-single-id-param application-ids)

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "item.get"
                                      :params (into {"itemids" item-ids
                                                     "groupids" group-ids
                                                     "templateids" template-ids
                                                     "hostids" host-ids
                                                     "proxyids" proxy-ids
                                                     "interfaceids" interface-ids
                                                     "graphids" graph-ids
                                                     "triggerids" trigger-ids
                                                     "applicationids" application-ids

                                                     "inherited" inherited
                                                     "templated" templated
                                                     "monitored" monitored

                                                     "group" group
                                                     "host" host
                                                     "application" application

                                                     "with_triggers" with-triggers

                                                     ;; select queries
                                                     "selectHosts" hosts-q
                                                     "selectInterfaces" interfaces-q
                                                     "selectTriggers" triggers-q
                                                     "selectApplications" applications-q
                                                     "selectDiscoveryRule" discovery-rule-q
                                                     "selectItemDiscovery" item-discovery-q
                                                     "selectPreprocessing" preprocessing-q

                                                     "filter" filter
                                                     "limitSelects" limit-for-subselects
                                                     "sortfield" sort-by-fields}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-triggers
  "Retrieve the list of triggers.

  This corresponds to the [trigger.get](https://www.zabbix.com/documentation/current/manual/api/reference/trigger/get) method.
  See also doc for the [trigger](https://www.zabbix.com/documentation/current/manual/api/reference/trigger/object) object."
  [conn & {:keys [trigger-ids group-ids template-ids host-ids item-ids application-ids
                  functions
                  group host
                  inherited templated dependant
                  monitored active      ; flags
                  maintenance

                  tag-filters

                  groups-q hosts-q items-q functions-q
                  discovery-rule-q last-event-q tags-q trigger-discovery-q

                  filter
                  limit-for-subselects
                  sort-by-fields

                  request-id]
           :as all-kw-args}]
  (let [auth-token (get-auth-token conn)

        trigger-ids (parse-list-or-single-id-param trigger-ids)
        group-ids (parse-list-or-single-id-param group-ids)
        template-ids (parse-list-or-single-id-param template-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        item-ids (parse-list-or-single-id-param item-ids)
        application-ids (parse-list-or-single-id-param application-ids)

        tag-filters (into (empty tag-filters) (map serialize-zabbix-tag-filter tag-filters))

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "trigger.get"
                                      :params (into {"triggerids" trigger-ids
                                                     "groupids" group-ids
                                                     "templateids" template-ids
                                                     "hostids" host-ids
                                                     "itemids" item-ids
                                                     "applicationids" application-ids

                                                     "functions" functions

                                                     "group" group
                                                     "host" host

                                                     "inherited" inherited
                                                     "templated" templated
                                                     "dependant" dependant
                                                     "monitored" monitored
                                                     "active" active
                                                     "maintenance" maintenance

                                                     "tags" tag-filters

                                                     ;; select queries
                                                     "selectGroups" groups-q
                                                     "selectHosts" hosts-q
                                                     "selectItems" items-q
                                                     "selectDiscoveryRule" discovery-rule-q
                                                     "selectLastEvent" last-event-q
                                                     "selectTags" tags-q
                                                     "selectTriggerDiscovery" trigger-discovery-q

                                                     "filter" filter
                                                     "limitSelects" limit-for-subselects
                                                     "sortfield" sort-by-fields}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))



;; API: VOLATILE DATA

(defn get-history
  "Retrieve timeseries data for hosts / items.

  This corresponds to the [history.get](https://www.zabbix.com/documentation/current/manual/api/reference/history/get) method.
  See also doc for the [history](https://www.zabbix.com/documentation/current/manual/api/reference/history/object) object.

  Many filmtering options are available through optional keyword arguments.

  Ordering of items: :SORT-BY-FIELDS
  This parameter expects either a list or a single string."
  [conn & {:keys [object-type host-ids item-ids
                  sort-by-fields from to
                  request-id]
           :as all-kw-args
           :or {object-type :unint
                sort-by-field   "clock"}}]
  (let [auth-token (get-auth-token conn)

        history-id (object-type->history-id object-type)
        host-ids (parse-list-or-single-id-param host-ids)
        item-ids (parse-list-or-single-id-param item-ids)

        from (ensure-inst-ts from)
        to (ensure-inst-ts to)

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "history.get"
                                      :params (into {"history" history-id
                                                     "hostids" host-ids
                                                     "itemids" item-ids
                                                     "sortfield" sort-by-fields
                                                     "time_from" from
                                                     "time_till" to}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-trends
  "Retrieve the list of trends.

  This corresponds to the [trend.get](https://www.zabbix.com/documentation/current/manual/api/reference/trend/get) method."
  [conn & {:keys [item-ids from to count-output limit output
                  request-id]
           :as all-kw-args
           :or {}}]
  (let [auth-token (get-auth-token conn)
        item-ids (parse-list-or-single-id-param item-ids)
        from (ensure-inst-ts from)
        to (ensure-inst-ts to)
        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "trend.get"
                                      :params (into {"itemids" item-ids
                                                     "time_from" from
                                                     "time_till" to
                                                     "countOutput" count-output
                                                     "limit" limit
                                                     "output" output}
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-events
  "Retrieve the list of events.

  This corresponds to the [event.get](https://www.zabbix.com/documentation/current/manual/api/reference/event/get) method.
  See also doc for the [event](https://www.zabbix.com/documentation/current/manual/api/reference/event/object) object.

  Many filmtering options are available through optional keyword arguments.

  Filter by instance ids: :EVENT-IDS :GROUP-IDS :HOST-IDS :OBJECT-IDS :APPLICATION-IDS.
  All these parameters expect either a list or a single id.

  Filter by type of event:
  - :SOURCE-TYPE, in `#{ :trigger :discovery-rule :agent-autoregistration :internal }`
  - :OBJECT-TYPE (depending of :SOURCE-TYPE, in:
    ```
    {:trigger #{:trigger}
     :discovery-rule #{:discovered-host :discovered-service}
     :agent-autoregistration #{:autoregistered-host}
     :internal #{:trigger :item :lld-rule}}
    ```

  Filter by severity: :SEVERITY, in `#{ :not-classified :information :warning :average :high :disaster }`

  Filter by tags w/ :EVAL-TYPE (#{:and+or :or}) and :TAG-FILTERS rules.
  TAG-FILTERS is expected to be a vector formatted like so: `[{:tag \"<TAG_NAME>\" :value \"<FILTER_VALUE>\" :operator <OPERATOR>} ...]`.
  <OPERATOR> in `#{:like :equal}`

  Filter by event id range: :EVENTID-FROM :EVENTID-TO.

  Filter by time range:
  - of event generation: :FROM :TO
  - of corresponding problems: :PROBLEM-T-FROM :PROBLEM-T-TO
  All these allow both unix timestamp and Clojure insts.

  Filter by values: :VALUES.
  This can be either a list or a single value."
  [conn & {:keys [event-ids group-ids host-ids object-ids application-ids
                  source-type object-type
                  acknowledged suppressed
                  severities
                  eval-type tag-filters
                  from to problem-t-from problem-t-to eventid-from eventid-to values
                  ;; select-hosts
                  request-id]
           :as all-kw-args
           :or {}}]
  (let [auth-token (get-auth-token conn)

        event-ids (parse-list-or-single-id-param event-ids)
        group-ids (parse-list-or-single-id-param group-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        object-ids (parse-list-or-single-id-param object-ids)
        application-ids (parse-list-or-single-id-param application-ids)

        source-id (event-source-type->source-id source-type)
        object-type-id (event-object-type->object-id source-type object-type)

        severities (when severities
                     (cond
                       (coll? severities) (map severity->id severities)
                       :default (severity->id severities)))

        eval-type-id (eval-type->id eval-type)
        tag-filters (into (empty tag-filters) (map serialize-zabbix-tag-filter tag-filters))

        from (ensure-inst-ts from)
        to (ensure-inst-ts to)
        problem-t-from (ensure-inst-ts problem-t-from)
        problem-t-to (ensure-inst-ts problem-t-to)

        eventid-from (when eventid-from
                       (str eventid-from))
        eventid-to (when eventid-to
                     (str eventid-to))

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "trend.get"
                                      :params (into {"eventids" event-ids
                                                     "groupids" group-ids
                                                     "hostids" host-ids
                                                     "objectids" object-ids
                                                     "applicationids" application-ids

                                                     "source" source-id
                                                     "object" object-type-id

                                                     "acknowledged" acknowledged
                                                     "suppressed" suppressed

                                                     "severities" severities

                                                     "evaltype" eval-type-id
                                                     "tags" tag-filters

                                                     "time_from" from
                                                     "time_till" to
                                                     "problem_time_from" problem-t-from
                                                     "problem_time_till" problem-t-to

                                                     "eventid_from" eventid-from
                                                     "eventid_till" eventid-to

                                                     "value" values

                                                     ;; "countOutput" count-output
                                                     ;; "limit" limit
                                                     ;; "output" output
                                                     }
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))

(defn get-problems
  "Retrieve the list of problems.

  This corresponds to the [problem.get](https://www.zabbix.com/documentation/current/manual/api/reference/problem/get) method.
  See also doc for the [problem](https://www.zabbix.com/documentation/current/manual/api/reference/problem/object) object.

  Many filmtering options are available through optional keyword arguments.

  Ordering of items: :SORT-BY-FIELDS
  This parameter expects either a list or a single string, even though the only accepted value is \"eventid\"."
  [conn & {:keys [event-ids group-ids host-ids object-ids application-ids
                  source-type object-type
                  acknowledged suppressed
                  severities
                  eval-type tag-filters
                  recent
                  from to
                  eventid-from eventid-to
                  sort-by-fields
                  request-id]
           :as all-kw-args
           :or {}}]
  (let [auth-token (get-auth-token conn)

        event-ids (parse-list-or-single-id-param event-ids)
        group-ids (parse-list-or-single-id-param group-ids)
        host-ids (parse-list-or-single-id-param host-ids)
        object-ids (parse-list-or-single-id-param object-ids)
        application-ids (parse-list-or-single-id-param application-ids)

        source-id (event-source-type->source-id source-type)
        object-type-id (event-object-type->object-id source-type object-type)

        severities (when severities
                     (cond
                       (coll? severities) (map severity->id severities)
                       :default (severity->id severities)))

        eval-type-id (eval-type->id eval-type)
        tag-filters (into (empty tag-filters) (map serialize-zabbix-tag-filter tag-filters))

        from (ensure-inst-ts from)
        to (ensure-inst-ts to)

        eventid-from (when eventid-from
                       (str eventid-from))
        eventid-to (when eventid-to
                     (str eventid-to))

        generic-get-params (parse-generic-get-params all-kw-args)]
    (json-rpc-request-and-maybe-parse conn
                                      "problem.get"
                                      :params (into {
                                                     "eventids" event-ids
                                                     "groupids" group-ids
                                                     "hostids" host-ids
                                                     "objectids" object-ids
                                                     "applicationids" application-ids

                                                     "source" source-id
                                                     "object" object-type-id

                                                     "acknowledged" acknowledged
                                                     "suppressed" suppressed

                                                     "severities" severities

                                                     "evaltype" eval-type-id
                                                     "tags" tag-filters

                                                     "recent" recent

                                                     "time_from" from
                                                     "time_till" to

                                                     "eventid_from" eventid-from
                                                     "eventid_till" eventid-to

                                                     "sortfield" sort-by-fields
                                                     }
                                                    generic-get-params)
                                      :auth auth-token
                                      :request-id request-id)))



;; IMPL - COMMON

(defn severity->id [severity]
  (get
   {:not-classified        0
    :information           1
    :warning               2
    :average               3
    :high                  4
    :disaster              5}
   severity))

(defn serialize-zabbix-tag-filter [tag-filter]
  (let [{:keys [tag value operator]} tag-filter
        operator (or operator :like)
        operator-id (eval-operator->id operator)]
    {"tag" tag
     "value" value
     "operator" operator-id}))



;; IMPL - API: VOLATILE DATA

(defn object-type->history-id [object-type]
  (get
   {:float 0
    :char  1
    :log   2
    :unint 3
    :text  4}
   object-type))

(defn eval-type->id [eval-type]
  (get
   {:and+or                0
    :or                    2}
   eval-type))

(defn eval-operator->id [eval-operator]
  (get
   {:like                  0
    :equal                 2}
   eval-operator))

(defn event-source-type->source-id [source-type]
  (get
   {:trigger                0
    :discovery-rule         1
    :agent-autoregistration 2
    :internal               3}
   source-type))

(defn event-object-type->object-id [source-type object-type]
  (get-in
   {:trigger
    {:trigger 0}

    :discovery-rule
    {:discovered-host 1
     :discovered-service 2}

    :agent-autoregistration
    {:autoregistered-host 3}

    :internal
    {:trigger 0
     :item 4
     :lld-rule 5}}
   [source-type object-type]))



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
                                        :body (json/write-value-as-string rq-body)})]
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

          ::best
          (let [result (get body "result")]
            (if (and (coll? result)
                     (map? (first result))
                     (member? "clock" (keys (first result))))
              (clojurize-timed-collection result)
              result))

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

(defn- entry-member-of-map? [entry coll]
  (let [[k v] entry]
    (and (contains? coll k)
         (= v (get coll k)))))

(defn member?
  "Returns a truthy value if V is found in collection COLL."
  [v coll]

  (when-not (coll? coll)
    (throw (ex-info "Argument `coll` is not a collection" {:ex-type :unexpected-type})))

  (cond
    (set? coll) (coll v)                ; sets can be used as fn

    (map? coll)
    (cond
      (and (vector? v)
           (= 2 (count v)))
      (entry-member-of-map? v coll)

      (and (map? v)
           (= 1 (count v)))
      (entry-member-of-map? (first v) coll)

      :default (throw (ex-info "Argument `coll` is a map, expecting `v` to be a vector of size 2 or map os size 1"
                               {:ex-type :unexpected-type,
                                :v v :coll coll})))

    :default (some #{v} coll)))



;; HELPERS: TIME

(defn- ensure-inst-ts [i]
  (cond
    (inst? i)
    (int (/ (inst-ms i) 1000))

    :default
    i))

(defn- zabbix-timestamp->instant [ts-s & [ns-part]]
  (let [s-dur (t/new-duration (edn/read-string ts-s) :seconds)
        ns-part (or (edn/read-string ns-part) 0)
        ns-dur (t/new-duration ns-part :nanos)]
    (t/+ (t/epoch) s-dur ns-dur)))

(defn- clojurize-timed-collection-entry [entry]
  (let [ts-s (get entry "clock")
        ns-part (get entry "ns")
        instant (zabbix-timestamp->instant ts-s)
        entry (-> entry
                  (dissoc "clock")
                  (dissoc "ns"))]
    [instant entry]))

(defn- clojurize-timed-collection [coll]
  (into {} (map clojurize-timed-collection-entry coll)))
