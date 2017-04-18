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
  (let [_default-es-index-name "cljidx" ; TODO: get this from args
        _default-s3-bucket "int-data-dumps"
        _default-s3-path nil
        _default-es-host "http://127.0.0.1:9200"
        _default-input-files-path "resources/emls/"
          
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
    
    (defn ensure-elasticsearch-index-created! [conn es-index]
      (slingshot/try+
        (esidx/create conn es-index {:mappings mapping-types :settings settings})
      (catch [:status 400] {:keys [ body]}
        (if (not (string/includes? body "index_already_exists_exception"))
          (slingshot/throw+)
          (prn (str "index " es-index " already exists; that's okay (doing nothing)"))
        )
      )
      )
    )

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
            :sha1 (sha1-str (:download-url tika-parsed-doc) )
            :title (:subject tika-parsed-doc)
            :source_url (:download-url tika-parsed-doc)
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


    (defn elasticsearch-index-function-maker [conn es-index] (fn [document] esdoc/create conn es-index "doc" document))

    (defn make-download-url-function-maker [s3-basepath target-path ] 
      (fn [filename]           
        (def filename-basepath (string/replace-first filename target-path "" ))
        (str s3-basepath (if (or (= (first filename-basepath) \/) (= (last s3-basepath) \/)) "" "/") filename-basepath)
      )
    )

    ; this should eventually treat emails different from blobs, etc.    
    (defn parse-file-function-maker [make-download-url]
      (fn [filename] (assoc (extract/parse filename) :download-url (make-download-url filename)))
    )


    (defn -main
      "I don't do a whole lot ... yet."
      [& args] ; TODO; args is just a list

      (let [
          es-index (or false _default-es-index-name)
          s3-bucket (or false _default-s3-bucket)
          s3-path (or false _default-s3-path)
          es-host (or false _default-es-host)
          input-files-path (or (first args) _default-input-files-path)

          s3-basepath (str "https://" s3-bucket ".s3.amazonaws.com/" (or s3-path es-index "/"))
          target-path (first (string/split input-files-path #"\*"))
          make-download-url (make-download-url-function-maker s3-basepath target-path)
          
          conn (esrest/connect "http://127.0.0.1:9200")
        ]

        (ensure-elasticsearch-index-created! conn es-index)

        (def files (remove #(.isDirectory %) (file-seq (clojure.java.io/file input-files-path))))
        (defn elasticsearchindex! [rawdoc]
          (let [document (arrange-for-indexing rawdoc)
                actually-index! ((elasticsearch-index-function-maker conn es-index) document)
                ]
            (def indexed-document-metadata {:title document :id (:_id (actually-index! document) )})
            ; (prn indexed-document-metadata)
          )
        )

        (defn parse-file [filename] ((parse-file-function-maker make-download-url) filename) )
        (let [parse-futures-list (doseq [filename files] 
          (def parsed-file (parse-file filename))
          ; (prn parsed-file)
          (future (elasticsearchindex! parsed-file))
          )]
            (map deref parse-futures-list )
            (shutdown-agents)

        )
      ) ; end function-wide let.

    ) ; end defn main
  ) ; end let
