(ns org.panchromatic.mokuhan.renderer-test
  (:require [clojure.test :as t]
            [org.panchromatic.mokuhan.renderer :as sut]
            [org.panchromatic.mokuhan.ast :as ast]))

(def ^:private delimiters
  {:open "{{" :close "}}"})

(t/deftest render-escaped-variable-test
  (t/testing "Single path"
    (let [v (ast/new-escaped-variable ["x"] delimiters)]
      (t/testing "String"
        (t/is (= "Hi" (sut/render v {:x "Hi"}))))

      (t/testing "Integer"
        (t/is (= "42" (sut/render v {:x 42}))))

      (t/testing "Boolean"
        (t/is (= "true" (sut/render v {:x true})))
        (t/is (= "false" (sut/render v {:x false}))))

      (t/testing "HTML string"
        (t/is (= "&amp;&lt;&gt;&#39;&quot;" (sut/render v {:x "&<>'\""}))))

      (t/testing "Map"
        (t/is (= "{:foo 1}" (sut/render v {:x {:foo 1}}))))

      (t/testing "Vector"
        (t/is (= "[1 2]" (sut/render v {:x [1 2]}))))

      (t/testing "Object"
        (t/is (= "object!" (sut/render v {:x (reify Object (toString [this] "object!"))}))))

      (t/testing "nil"
        (t/is (= "" (sut/render v {:x nil}))))

      (t/testing "missing"
        (t/is (= "" (sut/render v {}))))))

  (t/testing "Dotted path"
    (let [v (ast/new-escaped-variable ["x" "y"] delimiters)]
      (t/testing "String"
        (t/is (= "Hi" (sut/render v {:x {:y "Hi"}}))))

      (t/testing "Integer"
        (t/is (= "42" (sut/render v {:x {:y 42}}))))

      (t/testing "Boolean"
        (t/is (= "true" (sut/render v {:x {:y true}})))
        (t/is (= "false" (sut/render v {:x {:y false}}))))

      (t/testing "HTML string"
        (t/is (= "&amp;&lt;&gt;&#39;&quot;" (sut/render v {:x {:y "&<>'\""}}))))

      (t/testing "Map"
        (t/is (= "{:foo 1}" (sut/render v {:x {:y {:foo 1}}}))))

      (t/testing "Vector"
        (t/is (= "[1 2]" (sut/render v {:x {:y [1 2]}}))))

      (t/testing "nil"
        (t/is (= "" (sut/render v {:x {:y nil}}))))

      (t/testing "missing"
        (t/is (= "" (sut/render v {:x {}}))))))

  (t/testing "Include index of list"
    (let [v (ast/new-escaped-variable ["x" 1 "y"] delimiters)]
      (t/is (= "42" (sut/render v {:x [{:y 41} {:y 42}]})))

      (t/is (= "" (sut/render v {:x [{:y 41}]})))))

  (t/testing "Dot"
    (let [v (ast/new-escaped-variable ["."] delimiters)]
      (t/is (= "{:x 42}" (sut/render v {:x 42}))))))

(t/deftest render-standard-section-test
  (t/testing "single path section"
    (let [v (-> (ast/new-standard-section ["x"] delimiters)
                (update :contents conj (ast/new-text "!!")))]
      (t/is (= "!!"
               (sut/render v {:x true})
               (sut/render v {:x {}})
               (sut/render v {:x 42})
               (sut/render v {:x "Hello"})))

      (t/is (= ""
               (sut/render v {:x false})
               (sut/render v {:x []})
               (sut/render v {:x nil})
               (sut/render v {})
               (sut/render v nil)))

      (t/is (= "!!!!" (sut/render v {:x [1 1]})))

      (t/is (= "Hello!!" (sut/render v {:x #(str "Hello" %)})))))

  (t/testing "dotted path section"
    (let [v (-> (ast/new-standard-section ["x" "y"] delimiters)
                (update :contents conj (ast/new-text "!!")))]
      (t/is (= "!!"
               (sut/render v {:x {:y true}})
               (sut/render v {:x {:y {}}})
               (sut/render v {:x {:y 42}})
               (sut/render v {:x {:y "Hello"}})))

      (t/is (= ""
               (sut/render v {:x {:y false}})
               (sut/render v {:x {:y []}})
               (sut/render v {:x {:y nil}})
               (sut/render v {:x {}})
               (sut/render v {:x nil})))

      (t/is (= "!!!!" (sut/render v {:x {:y [1 1]}})))

      (t/is (= "Hello!!" (sut/render v {:x {:y #(str "Hello" %)}})))))

  (t/testing "nested section"
    (let [v (-> (ast/new-standard-section ["x"] delimiters)
                (update :contents conj (-> (ast/new-standard-section ["y"] delimiters)
                                           (update :contents conj (ast/new-text "!!")))))]
      (t/is (= "!!" (sut/render v {:x {:y true}})))
      (t/is (= "!!!!" (sut/render v {:x {:y [1 1]}})))
      (t/is (= "!!!!!!!!" (sut/render v {:x [{:y [1 1]} {:y [1 1]}]})))
      (t/is (= "!!!!"
               (sut/render v {:x [{:y [1 1]} {:y []}]})
               (sut/render v {:x [{:y true} {:y false} {:y true}]})))))

  (t/testing "nested and don't use outer key"
    (let [v (-> [(-> (ast/new-standard-section ["x"] delimiters)
                     (update :contents conj (-> (ast/new-standard-section ["y"] delimiters)
                                                (update :contents conj (ast/new-text "Hello")))))]
                ast/new-mustache)]
      (t/is (= "" (sut/render v {:x [{:y false}]
                                 :y true}))))))
