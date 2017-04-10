(ns stevedore.core
  (:gen-class))

  (require '[clojure.java.io :as io]
          '[pantomime.extract :as extract]
          '[clojurewerkz.elastisch.rest :as esr]
          '[clojurewerkz.elastisch.rest.index :as esi]
          '[clojure.pprint :as pprint]
         )


(defn -main
  "I don't do a whole lot ... yet."
  [& args]

  (def esindex "cljidx") ; TODO: get this from args

  (def [conn (esr/connect "http://127.0.0.1:9200")
        mapping-types {"doc" {:properties {:title   {:type "string" :store "yes" :analyzer "keyword"}
                                            :source_url {:type "string" :store "yes" :index "not_analyzed"}
                                          }
                             }
                      }


        settings {
                    :analysis {
                      :analyzer {
                        :email_analyzer {
                          :type "custom"
                          :tokenizer "email_tokenizer"
                          :filter ["lowercase"]
                        }
                        :snowball_analyzer {
                          :type "snowball"
                          :language "English"
                        }

                      }
                      :tokenizer {
                        :email_tokenizer {
                          :type "pattern"
                          :pattern "([a-zA-Z0-9_\\.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-\\.]+)"
                          :group "0"
                        }
                      }
                    }
                  } 
        ]
    (esi/create conn esindex :mappings mapping-types :settings settings)])



  (println "connected to ES")
  ; this one works  
  ; (pprint/pprint (extract/parse (clojure.java.io/resource "emls/36110.eml")))

  (:require [clojurewerkz.elastisch.rest       :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]))





  (def files (file-seq (clojure.java.io/file "resources/emls/")))
  (pprint/pprint (map extract/parse (remove #(.isDirectory %) (take 5 files ))))
)