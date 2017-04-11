(ns stevedore.core
  (:gen-class))

  (require '[clojure.java.io :as io]
          '[pantomime.extract :as extract]
          '[clojurewerkz.elastisch.rest :as esrest]
          '[clojurewerkz.elastisch.rest.index :as esidx]
          '[clojurewerkz.elastisch.rest.document :as esdoc]
          '[clojure.pprint :as pprint]
          '[clj-http.client :as client]
          '[cheshire.core :as json]
         )


(defn -main
  "I don't do a whole lot ... yet."
  [& args] ; TODO; args is just a list

  (def esindex "cljidx") ; TODO: get this from args

  (let [conn (esrest/connect "http://127.0.0.1:9200")
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
    ; TODO: only create index if it's absent
    ; (try+
    ;   (esidx/create conn esindex {:mappings mapping-types :settings settings})
    ; (catch [:status 403] {:keys [request-time headers body]}
    ;   )
    ; (log/warn "NOT Found 404" request-time headers body))
    ; (catch Object _
    ; (log/error (:throwable &throw-context) "unexpected error")
    ; (throw+)))

    ; this one works  
    ; (pprint/pprint (extract/parse (clojure.java.io/resource "emls/36110.eml")))

    (defn arrange-for-indexing [tika-parsed-doc] 
          {
            ; :sha1
            :title (:subject tika-parsed-doc)
            :source_url "" ; TODO
            :file {
              :title (:subject tika-parsed-doc)
              :file (:text tika-parsed-doc)
            }
            :analyzed {
              :body (:text tika-parsed-doc)
              :metadata {
                "Content-Type" (:creation-date tika-parsed-doc)
                "Creation-Date"(:content-type tika-parsed-doc)
                "Message-From" (:message-from tika-parsed-doc)
                "Message-To"   (:message-to tika-parsed-doc)
                "Message-Cc"   (:message-cc tika-parsed-doc)
                "subject"      (:subject tika-parsed-doc)
                ; "attachments"  nil ; TODO
                "dkim_verified" false
              }
            }
            ; :_updatedAt ; TODO
          }
    )


    (def files (remove #(.isDirectory %) (file-seq (clojure.java.io/file (or (first args) "resources/emls/"))))
      (pprint/pprint 
        (map 
          #(
            (esdoc/create conn esindex "doc" (arrange-for-indexing %))
          )
          (map extract/parse files )
        )
      ) 
    ) ; end def files
  ) ; end let

) ; end defn