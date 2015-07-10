(ns buildviz.util)

(defn respond-with-json [content]
  {:body content})

(defn respond-with-csv [content]
  {:body content
   :headers {"Content-Type" "text/csv;charset=UTF-8"}})

(defn respond-with-xml [content]
  {:body content
   :headers {"Content-Type" "application/xml;charset=UTF-8"}})
