(ns buildviz.util.url
  (:require [clojure.string :as str]))

(defn- hide-password [user-info]
  (when user-info
    (let [[username password] (str/split user-info #":" 2)]
      (if password
        (str/join ":" [username "*****"])
        username))))

(defn- printable-uri [uri]
  (java.net.URI. (.getScheme uri)
                 (hide-password (.getUserInfo uri))
                 (.getHost uri)
                 (.getPort uri)
                 (.getPath uri)
                 (.getQuery uri)
                 (.getFragment uri)))

(defprotocol URL
  (with-plain-text-password [this]))

(deftype PlainTextPasswordConciousURL [uri]
  URL
  (with-plain-text-password [this]
    (.toString uri))

  (toString [this]
    (.toString (printable-uri uri))))

(defn url [url-string]
  (when url-string
    (PlainTextPasswordConciousURL. (java.net.URI. url-string))))
