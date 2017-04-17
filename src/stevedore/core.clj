(ns stevedore.core
  (:gen-class))

  (require '[clojure.java.io :as io]
          '[pantomime.extract :as extract]
          '[clojurewerkz.elastisch.rest :as esrest]
          '[clojurewerkz.elastisch.rest.index :as esidx]
          '[clojurewerkz.elastisch.rest.document :as esdoc]
          ; '[clojure.pprint :as pprint]
          '[clojure.string :as string]
          ; '[clj-http.client :as client]
          ; '[cheshire.core :as json]
          '[slingshot.slingshot :as slingshot]
         )
  (import org.elasticsearch.indices.IndexAlreadyExistsException)
  (let [esindexname "cljidx" ; TODO: get this from args
        s3-bucket "int-data-dumps"
        s3-path nil
        s3-basepath (str "https://" s3-bucket ".s3.amazonaws.com/" (or s3-path esindexname "/"))
        conn (esrest/connect "http://127.0.0.1:9200")
          
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
    (slingshot/try+
      (esidx/create conn esindexname {:mappings mapping-types :settings settings})
    (catch [:status 400] {:keys [ body]}
      (if (not (string/includes? body "index_already_exists_exception"))
        (slingshot/throw+)
        ; (prn "index already exists; that's okay (doing nothing)")
      )
    )
    )

    (defn -main
      "I don't do a whole lot ... yet."
      [& args] ; TODO; args is just a list



        ; via https://gist.github.com/hozumi/1472865
        (defn sha1-str [s]
          (->> (-> "sha1"
                   java.security.MessageDigest/getInstance
                   (.digest (.getBytes s)))
               (map #(.substring
                      (Integer/toString
                       (+ (bit-and % 0xff) 0x100) 16) 1))
               (apply str)))

        (defn arrange-for-indexing [tika-parsed-doc] 
              {
                ; :sha1 (sha1-str (:download-url tika-parsed-doc) )
                :title (:subject tika-parsed-doc)
                ; :source_url (:download-url tika-parsed-doc)
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
        (def input-files-path (or (first args) "resources/emls/"))
        (def files (remove #(.isDirectory %) (file-seq (clojure.java.io/file input-files-path))))
        (defn elasticsearchindex [rawdoc]
          (let [document (arrange-for-indexing rawdoc)]
            (def asdf (time (esdoc/create conn esindexname "doc" document) ))
            
            (prn (:_id asdf))
            (:title document)
          )
        )

        (def target-path (first (string/split input-files-path #"\*")))
        (defn make-download-url [filename]
          (def filename-basepath (string/replace-first filename target-path "" ))
          (str s3-basepath (if (or (= (first filename-basepath) \/) (= (last s3-basepath) \/)) "" "/") filename-basepath)
        )

        ; this should eventually treat emails different from blobs, etc.
        (defn parse-file [filename]
          (assoc (extract/parse filename) :download-url (make-download-url filename))
        )

        (let [parse-futures-list (doseq [filename files] 
          (future (elasticsearchindex (parse-file filename)))
          )]
            (map deref parse-futures-list )
            (shutdown-agents)

        )

        ; (defn upload-images [my-files]
        ;     (doall
        ;       (map-indexed
        ;        (fn [filename i]
        ;          (future (elasticsearchindex (parse-file filename))))
        ;        my-files)))

        ; (def f (upload-images files))
        ;   (map deref f)
        


            

    ) ; end defn main
  ) ; end let
