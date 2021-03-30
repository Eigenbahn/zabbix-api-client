# zabbix-api-client

Client library wrapper around [Zabbix HTTP API](https://www.zabbix.com/documentation/current/manual/api).


## Installation

Add the following dependency to your `project.clj` file:

    [eigenbahn/zabbix-api-client "1.0.0"]


## General Usage

#### Connection

All methods take a connection `conn` as a first argument. It's just a simple map in the form:

```clojure
{:url "<prometheus_base_url>" :user "<api_user>" :password "<api_password>"}
```

The :url field value must contain the base URL of the Zabbix instance. It is not expected to contain the "/api_jsonrpc.php" part.

So to connect to a local Zabbix instance running under `http://company.com/zabbix/`:

```clojure
(def zabbix-conn {:url "http://company.com/zabbix" :user "Admin" :password "zabbix"})
```

#### Authentication

Authentication is done systematically before each API call.

Manual retrieval of an auth token is possible by calling the `auth` method.

### Example usage

#### API version

```clojure
(api-version zabbix-conn)
;; -> "5.2.5"
```

#### auth

```clojure
(auth zabbix-conn)
;; -> "dd021d412befeea7a016d18b929a421c"
```

#### templates list

```clojure
(get-templates zabbix-conn)
;; -> [{"templateid" "10001"
;;      "name" "Linux by Zabbix agent"
;;      "description" "Official Linux template [...]"
;;      ;; [...]},
;;    ;; [...]
;;    ]
```

#### applications list

```clojure
(get-templates zabbix-conn)
;; -> [{"applicationid" "345",
;;      "flags" "0",
;;      "name" "Zabbix server",
;;      "hostid" "10084",
;;      "templateids" ["179"]},
;;     ;; [...]
;;    ]
```

And with filtering options:

```clojure
;; only those on (host) templates:
(get-templates zabbix-conn :templated true)

;; only those on hosts (not templates):
(get-templates zabbix-conn :templated false)

;; only those on hosts and inherited from templates:
(get-templates zabbix-conn :inherited true)

;; only those local to hosts (not inherited):
(get-templates zabbix-conn :templated false :inherited false)

;; only those on templates but with no host (yet) inheriting them:
(get-templates zabbix-conn :templated true :inherited false)
```

#### hosts list

```clojure
(get-hosts zabbix-conn)
;; -> [{"hostid" "10004"
;;      "name" "Zabbix server"
;;      "status" "0"
;;      ;; [...]},
;;    ;; [...]
;;    ]
```
