(ns mokuhan.parser
  (:require [fast-zip.core :as zip]
            [instaparse.core :as insta]
            [mokuhan.ast :as ast])
  (:import java.util.regex.Pattern))

(defn- re-quote [s]
  (re-pattern (Pattern/quote (str s))))

;;; {{name}}   -> variable
;;; {{{name}}} -> unescaped variable
;;; {{#persone}} <-> {{/person}} -> section
;;;   false or empty list -> delete
;;;   non empty list -> repeat
;;;   lambda -> call function
;;;   non-false -> context
;;; {{^name}} <-> {{/name}} -> inverted variable
;;; {{! blah }} -> comment
;;; {{> box}} -> partial
;;; {{=<% %>=}} -> set delimiter

(def default-delimiters
  {:open "{{" :close "}}"})

(defn generate-mustache-spec [{:keys [open close] :as delimiters}]
  (str "
<mustache> = *(*(tag / text / whitespace) (newline / <eof>))
text = !tag #'^[^\\r\\n\\s]+?(?=(?:" (re-quote open)  "|\\r?\\n|\\s|\\z))'
whitespace = #'\\s+'
newline = #'\\r?\\n'
eof = #'\\z'

<tag> = (set-delimiter / comment / section / variable)
<ident> = #'(?!\\d)[\\w\\Q$%&*-_+=|?<>\\E][\\w\\Q!$%&*-_+=|:?<>\\E]*?(?=\\s|\\.|" (re-quote close) ")'
name = ident *(<'.'> ident)

<variable> = !section !comment !set-delimiter (escaped-variable / unescaped-variable)
escaped-variable = !unescaped-variable <#'^" (re-quote open) "'> <*1 whitespace> name <*1 whitespace> <#'" (re-quote close) "'>
unescaped-variable = (!ampersand-unescaped-variable triple-mustache-unescaped-variable / ampersand-unescaped-variable)
<ampersand-unescaped-variable> = <#'^" (re-quote (str open "&")) "'> <*1 whitespace> name <*1 whitespace> <#'" (re-quote close) "'>
<triple-mustache-unescaped-variable> = <#'^" (re-quote "{{{") "'> <*1 whitespace> name <*1 whitespace> <#'" (re-quote "}}}") "'>

<section> = (open-section / close-section / open-inverted-section)
open-section = <#'^" (re-quote (str open "#")) "'> <*1 whitespace> name <*1 whitespace> <#'" (re-quote close) "'>
close-section = <#'^" (re-quote (str open "/")) "'> <*1 whitespace> name <*1 whitespace> <#'" (re-quote close) "'>
open-inverted-section = <#'^" (re-quote (str open "^")) "'> <*1 whitespace> name <*1 whitespace> <#'" (re-quote close) "'>

comment = <#'^" (re-quote (str open "!")) "'> #'(?:.|\\r?\\n)*?(?=" (re-quote close) ")' <#'" (re-quote close) "'>

set-delimiter = <#'^" (re-quote (str open "=")) "'> <*1 whitespace> new-open-delimiter <1* whitespace> new-close-delimiter <*1 whitespace> <#'" (re-quote (str "=" close)) "'> *rest
new-open-delimiter = #'[^\\s]+'
new-close-delimiter = #'[^\\s]+?(?=\\s*" (re-quote (str "=" close)) ")'
rest = #'(.|\\r?\\n)*$'
"))

(defn gen-parser [delimiters]
  (insta/parser (generate-mustache-spec delimiters) :input-format :abnf))

(def default-parser
  (gen-parser default-delimiters))

(defn parse*
  ([mustache]
   (parse* mustache {}))
  ([mustache opts]
   (let [parser (:parser opts default-parser)]
     (->> (dissoc opts :parser)
          (reduce-kv #(conj %1 %2 %3) [])
          (apply insta/parse parser mustache)))))


(defn- vec->ast-node [v]
  (case (first v)
    :text (ast/new-text (second v))
    :whitespace (ast/new-whitespace (second v))
    :newline (ast/new-newline (second v))
    :comment (ast/new-comment (second v))
    :escaped-variable (ast/new-escaped-variable (drop 1 (second v)))
    :unescaped-variable (ast/new-unescaped-variable (drop 1 (second v)))
    :open-section (ast/new-standard-section (drop 1 (second v)))
    :open-inverted-section (ast/new-inverted-section (drop 1 (second v)))))

(defn- meta-without-qualifiers [x]
  (some->> (meta x)
           (reduce-kv #(assoc %1 (-> %2 name keyword) %3) {})))

(defn parse
  ([mustache]
   (parse mustache {}))
  ([mustache opts]
   (loop [loc (ast/ast-zip)
          [elm & parsed] (parse* mustache opts)
          stack []]
     (if (nil? elm)
       (if (zip/up loc)
         (throw (ex-info "Unclosed section"
                         {:type ::unclosed-section
                          :tag (second elm)
                          :meta (meta-without-qualifiers elm)}))
         (zip/root loc))
       (case (first elm)
         (:open-section :open-inverted-section)
         (recur (-> loc (zip/append-child (vec->ast-node elm)) zip/down zip/rightmost)
                parsed
                (conj stack (second elm)))

         :close-section
         (if (= (peek stack) (second elm))
           (recur (-> loc zip/up) parsed (pop stack))
           (throw (ex-info "Unopened section"
                           {:type ::unopend-section
                            :tag (second elm)
                            :meta (meta-without-qualifiers elm)})))

         :set-delimiter
         (let [[_ [_ open] [_ close] [_ rest-of-mustache]] elm
               parser (gen-parser {:open open :close close})]
           (recur loc (parse* rest-of-mustache {:parser parser}) stack))

         (recur (-> loc (zip/append-child (vec->ast-node elm))) parsed stack))))))