(ns buildviz.safe-filenames-test
  (:require [buildviz.safe-filenames :as safe-filenames]
            [clojure
             [test :refer :all]]))

(deftest test-encode
  (testing "should encode a filename with dot"
    (is (= "my%2afilename"
           (safe-filenames/encode "my*filename")))))

(deftest test-decode
  (testing "should decode a filename with dot"
    (is (= "my*filename"
           (safe-filenames/decode "my%2afilename")))))

(deftest test-invalid-names
  (testing "should rename invalid names"
    (is (empty? (remove #(not= %
                               (safe-filenames/encode %))
                        '(; https://msdn.microsoft.com/en-us/library/aa365247(v=vs.85).aspx#naming_conventions
                          "CON" "PRN" "AUX" "NUL"
                          "COM1" "COM2" "COM3" "COM4" "COM5" "COM6" "COM7" "COM8" "COM9"
                          "LPT1" "LPT2" "LPT3" "LPT4" "LPT5" "LPT6" "LPT7" "LPT8" "LPT9"
                          "con"
                          "." ".."
                          "trailing." "trailing "
                          "/" "?" "<" ">" "\\" ":" "*" "|" "\""
                          "\u0000" "\u001b" "\u001f"
                          "\n" "\t" "\r" "\f"
                          ; https://en.wikipedia.org/wiki/C0_and_C1_control_codes
                          "\u0080" "\u009f"
                          "a\u001cb"))))))

(deftest test-valid-names
  (testing "should allow sub patterns invalid as whole"
    (is (empty? (remove #(= %
                            (safe-filenames/encode %))
                        '("aCON" "CONb"
                          "valid.mp3" ".valid" "valid" "LPT10" "笊,ざる.pdf"
                          "!@#$%^&{}[]()_+-=,.~'` abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"))))))

(deftest test-restore-encoded
  (testing "should do a roundtrip conversion"
    (is (empty? (remove #(= %
                            (safe-filenames/decode (safe-filenames/encode %)))
                        '(
                                        ; https://msdn.microsoft.com/en-us/library/aa365247(v=vs.85).aspx#naming_conventions
                          "CON" "PRN" "AUX" "NUL"
                          "COM1" "COM2" "COM3" "COM4" "COM5" "COM6" "COM7" "COM8" "COM9"
                          "LPT1" "LPT2" "LPT3" "LPT4" "LPT5" "LPT6" "LPT7" "LPT8" "LPT9"
                          "con"
                          "." ".."
                          "trailing." "trailing "
                          "/" "?" "<" ">" "\\" ":" "*" "|" "\""
                          "\u0000" "\u001b" "\u001f"
                          "\n" "\t" "\r" "\f"
                                        ; https://en.wikipedia.org/wiki/C0_and_C1_control_codes
                          "\u0080" "\u009f"
                          "a\u001cb"))))))
