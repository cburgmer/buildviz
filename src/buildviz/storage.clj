(ns buildviz.storage
  (:require [clojure.java.io :as io]
            [taoensso.nippy :as nippy]
            [clojure.tools.logging :as log]))

(import '[java.io DataInputStream DataOutputStream])

(defn store! [jobs filename]
  (log/info (format "Persisting to %s" filename))
  (with-open [w (io/output-stream filename)]
    (nippy/freeze-to-out! (DataOutputStream. w) jobs)))

(defn load-from-file [filename]
  (if (.exists (io/file filename))
    (with-open
      [r (io/input-stream filename)]
      (nippy/thaw-from-in! (DataInputStream. r)))
    {}))
