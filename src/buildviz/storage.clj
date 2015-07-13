(ns buildviz.storage
  (:require [clojure.java.io :as io]
            [taoensso.nippy :as nippy]))

(import '[java.io DataInputStream DataOutputStream])

(defn store-jobs! [jobs filename]
  (with-open [w (io/output-stream filename)]
    (nippy/freeze-to-out! (DataOutputStream. w) jobs)))

(defn load-jobs [filename]
  (if (.exists (io/file filename))
    (with-open
      [r (io/input-stream filename)]
      (nippy/thaw-from-in! (DataInputStream. r)))
    {}))
