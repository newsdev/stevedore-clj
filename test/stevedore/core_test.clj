(ns stevedore.core-test
  (:require [clojure.test :refer :all]
            [stevedore.core :refer :all]))

; TODO: this should be a real fixture
(let [example-tika-doc {
			:meta/author '("Fake Person <fperson@example.com>"), 
			:meta/creation-date '("2016-03-07T04:23:15Z"), 
			:dc/creator '("Fake Person <fperson@example.com>"), 
			:creator '("Fake Person <fperson@example.com>"), 
			:message-from '("Fake Person <fperson@example.com>"), 
			:download-url "https://int-data-dumps.s3.amazonaws.com/cljidx/whatever.pdf", 
			:message-cc '("Huma Abedin <ha16@example.com>" "Jake Sullivan <jsullivan@example.com>" "John Podesta <jp66@example.com>" "John Podesta <john.podesta@example.com>" "Jennifer Palmieri <jpalmieri@example.com>"), 
			:x-parsed-by '("org.apache.tika.parser.DefaultParser" "org.apache.tika.parser.mail.RFC822Parser"), 
			:dc/title '("IMPT! Hotel bar closes at midnight"), 
			:author '("Fake Person <fperson@example.com>"), 
			:message-to '("Anotherfake Person <aperson@example.com>"), 
			:dcterms/created '("2016-03-07T04:23:15Z"), 
			:creation-date '("2016-03-07T04:23:15Z"), 
			:content-type '("message/rfc822"), 
			:subject '("IMPT! Hotel bar closes at midnight"), 
			:text "Team - we are about 15mins away from the hotel and I know we all want\r\nto be sure we get some sustenance when we return. SO, I encourage you\r\nto come thru the loading dock entrance to the hotel along with HRC.\r\nAnd then we can race you to the Motor Bar on the 2nd floor which is\r\nopen until midnight.\r\n\n\n"
		}
	]

	(deftest sha1-test
	  (testing "calculating sha1 sums"
	    (is (= (sha1-str "test.pdf") "636120a629e1539d87fa47afee8847a253690437"))))

	(deftest has-sha1-test
	  (testing "arranged-for-indexing doc will always have a sha1"
	    (is (not (nil? (:sha1 (arrange-for-indexing example-tika-doc)) )  ))) )

	(deftest has-title-test
	  (testing "arranged-for-indexing doc will always have a title"
	    (is (not (nil? (:title (arrange-for-indexing example-tika-doc)) )  ))) )
	(deftest has-source-url-test
	  (testing "arranged-for-indexing doc will always have a source-url"
	    (is (not (nil? (:source_url (arrange-for-indexing example-tika-doc)) )  ))) )

	(deftest has-source-url-test
	  (testing "arranged-for-indexing doc will always have a file.file"
	    (is (not (nil? (:file (:file (arrange-for-indexing example-tika-doc)) ))  ))) )


	(deftest has-file-title-test
	  (testing "arranged-for-indexing doc will always have a file.title"
	    (is (not (nil? (:title (:file (arrange-for-indexing example-tika-doc)) ))  ))) )

	(deftest has-analyzed-body-test
	  (testing "arranged-for-indexing doc will always have an analyzed.body"
	    (is (not (nil? (:body (:analyzed (arrange-for-indexing example-tika-doc)) ))  ))) )
)
