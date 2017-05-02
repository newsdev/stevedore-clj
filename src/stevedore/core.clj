(ns stevedore.core
  (:gen-class))

  (require '[clojure.java.io :as io]
          '[pantomime.extract :as extract]
          '[clojurewerkz.elastisch.rest :as esrest]
          '[clojurewerkz.elastisch.rest.index :as esidx]
          '[clojurewerkz.elastisch.rest.document :as esdoc]
          '[clojure.string :as string]
          '[slingshot.slingshot :as slingshot]
          '[amazonica.aws.s3 :as s3]
          '[me.raynes.fs :as fs]
         )
  ;   (:require [clojure.zip :refer [lefts rights]])) ; consider this to save memory
  (import org.elasticsearch.indices.IndexAlreadyExistsException)
  (let [_default-es-index-name "cljidx"
        _default-s3-bucket "int-data-dumps"
        _default-s3-path nil
        _default-es-host "http://localhost:9200"
        _default-input-files-path "resources/emls/"
        tmpdir (fs/temp-dir "stvdrtmp")
          
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
        (if (and (not (string/includes? body "index_already_exists_exception")) (not (string/includes? body "IndexAlreadyExistsException")) )
          (slingshot/throw+)
          (prn (str "index " es-index " already exists; that's okay (doing nothing)"))
        )) ) )

    ; TODO:
    ;  - OCR
    ;  - various document formats (emails, etc.)
    ;  - from CSV
    ;  - handle tables of contents from ebooks
    ;  - custom Tika version

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
            :_updatedAt (new java.util.Date)
          })


    (defn elasticsearch-index-factory [conn es-index] (fn [document] (esdoc/put conn es-index "doc" (:sha1 document) document)))

    (defn make-download-url-factory [s3-basepath target-path ] 
      (fn [filename]
        (let [filename-basepath (string/replace-first filename target-path "" )]
          (doto (str s3-basepath (if (or (= (first filename-basepath) \/) (= (last s3-basepath) \/)) "" "/") filename-basepath) (println) ))))


    ; this should eventually treat emails different from blobs, etc.    
    (defn parse-file-factory [make-download-url]
      (fn 
        [{ download-url-fragment :download-url-fragment
           filepath :path }] 
        (assoc 
          (try (extract/parse filepath) (catch org.apache.tika.exception.TikaException e (println "there was an error parsing " filepath) nil) )
          :download-url (make-download-url download-url-fragment))))

    (defn write-to-temp [stream relative-path]
      "writes a stream to /tmp"
      (let 
        [file (io/file tmpdir relative-path)]
        (io/make-parents file)
        (io/copy stream file)
        (.deleteOnExit file) ; some JVM-specific thing
        (str file) ))
    (defn write-s3-to-temp 
      "downloads and writes to /tmp a file from S3 by its object-summary, preserving path under bucket"
      [object-summary]
      ; (prn (:key object-summary))
      (write-to-temp 
                      (:input-stream (s3/get-object (:bucket-name object-summary) (:key object-summary))) 
                      (:key object-summary)
                    ))

    (defn s3-files-factory 
      "returns a function taking an s3 path and returning a map with keys for the absolute path and download url fragment for each contained file."
      [input-files-path]
      (let [
        s3components (re-find (re-matcher #"[Ss]3:\/\/([^\/]+)/([^\/]+)" input-files-path))
        objects (:object-summaries (s3/list-objects :bucket-name (nth s3components 1 ) :prefix (nth s3components 2 )) )
        ]
        (map 
          (fn [object-summary] (hash-map :download-url-fragment (:key object-summary)
             :path (write-s3-to-temp object-summary))) (remove #(= (:size %) 0) objects ) )))

    (defn local-files-factory [input-files-path]
      "returns a function taking a local pathh and returning a map with keys for the absolute path and download url fragment for each contained file."      
      (map #(hash-map :download-url-fragment % :path %) (remove #(.isDirectory %) (file-seq (clojure.java.io/file input-files-path)))))

    (defn files-factory 
      "returns a function taking a local or s3 path and returning a map with keys for the absolute path and download url fragment for each contained file."
      [input-files-path] 
      (if (or (string/starts-with? input-files-path "s3://") (string/starts-with? input-files-path "S3://")) (s3-files-factory input-files-path) (local-files-factory input-files-path) ))

    (defn -main
      "I don't do a whole lot ... yet."
      [& args] ; TODO; args is just a list
               ; https://github.com/clojure/tools.cli

      (let [
          input-files-path (or (first args) _default-input-files-path)
          is-s3 (or (string/starts-with? input-files-path "s3://") (string/starts-with? input-files-path "S3://"))

          es-index (or false _default-es-index-name)
          es-host (or false _default-es-host)

          s3-bucket (if is-s3 (nth (string/split input-files-path #"/+") 1) (or false _default-s3-bucket))
          s3-path (if is-s3 nil (or false _default-s3-path))
          s3-basepath (str "https://" s3-bucket ".s3.amazonaws.com/" (if is-s3 nil (or s3-path es-index "/")))

          target-path (first (string/split input-files-path #"\*"))
          make-download-url (make-download-url-factory s3-basepath target-path)
          
          conn (esrest/connect es-host)
          files (files-factory input-files-path) 
        ]

        (ensure-elasticsearch-index-created! conn es-index)


        (defn elasticsearchindex! [rawdoc]
          (defn actually-index! [document] ((elasticsearch-index-factory conn es-index) document))
          (let [
                document (arrange-for-indexing rawdoc)
                indexed-document-metadata {:title (:title (:file document)) :id (:_id (actually-index! document) )}
               ]
            (prn indexed-document-metadata)
          )
        )

        (defn parse-file 
          "takes a hash of :path and :download-url-fragment"
          [ fileinfo] 
          ((parse-file-factory make-download-url) fileinfo)  )
        (let [parse-futures-list (doseq [fileinfo files] 
          (future (elasticsearchindex! (parse-file fileinfo)))
          )]
            (map deref parse-futures-list )
            (shutdown-agents)

        )
      ) ; end main-wide let.

    ) ; end defn main
  ) ; end let
