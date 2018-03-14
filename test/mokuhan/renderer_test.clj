(ns mokuhan.renderer-test
  (:require [clojure.test :as t]
            [mokuhan.ast :as ast]
            [mokuhan.renderer :as sut]))

(t/deftest render-escaped-variable-test
  (t/testing "Single path"
    (let [v (ast/new-escaped-variable ["x"])]
      (t/testing "String"
        (t/is (= "Hi" (sut/render v {:x "Hi"}))))

      (t/testing "Integer"
        (t/is (= "42" (sut/render v {:x 42}))))

      (t/testing "HTML string"
        (t/is (= "&amp;&lt;&gt;&#39;&quot;" (sut/render v {:x "&<>'\""}))))

      (t/testing "Map"
        (t/is (= "{:foo 1}" (sut/render v {:x {:foo 1}}))))

      (t/testing "Vector"
        (t/is (= "[1 2]" (sut/render v {:x [1 2]}))))

      (t/testing "nil"
        (t/is (= "" (sut/render v {:x nil}))))

      (t/testing "missing"
        (t/is (= "" (sut/render v {}))))))

  (t/testing "Dotted path"
    (let [v (ast/new-escaped-variable ["x" "y"])]
      (t/testing "String"
        (t/is (= "Hi" (sut/render v {:x {:y "Hi"}}))))

      (t/testing "Integer"
        (t/is (= "42" (sut/render v {:x {:y 42}}))))

      (t/testing "HTML string"
        (t/is (= "&amp;&lt;&gt;&#39;&quot;" (sut/render v {:x {:y "&<>'\""}}))))

      (t/testing "Map"
        (t/is (= "{:foo 1}" (sut/render v {:x {:y {:foo 1}}}))))

      (t/testing "Vector"
        (t/is (= "[1 2]" (sut/render v {:x {:y [1 2]}}))))

      (t/testing "nil"
        (t/is (= "" (sut/render v {:x {:y nil}}))))

      (t/testing "missing"
        (t/is (= "" (sut/render v {:x {}})))))))
