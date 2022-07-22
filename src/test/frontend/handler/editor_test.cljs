(ns frontend.handler.editor-test
  (:require [frontend.handler.editor :as editor]
            [clojure.test :refer [deftest is testing are]]
            [frontend.util :as util]
            [frontend.state :as state]
            [frontend.util.cursor :as cursor]))

(deftest extract-nearest-link-from-text-test
  (testing "Page, block and tag links"
    (is (= "page1"
           (editor/extract-nearest-link-from-text "[[page1]] [[page2]]" 0))
        "Finds first page link correctly based on cursor position")

    (is (= "page2"
           (editor/extract-nearest-link-from-text "[[page1]] [[page2]]" 10))
        "Finds second page link correctly based on cursor position")

    (is (= "tag"
           (editor/extract-nearest-link-from-text "#tag [[page1]]" 3))
        "Finds tag correctly")

    (is (= "61e057b9-f799-4532-9258-cfef6ce58370"
           (editor/extract-nearest-link-from-text
            "((61e057b9-f799-4532-9258-cfef6ce58370)) #todo" 5))
        "Finds block correctly"))

  (testing "Url links"
    (is (= "https://github.com/logseq/logseq"
           (editor/extract-nearest-link-from-text
            "https://github.com/logseq/logseq is #awesome :)" 0 editor/url-regex))
        "Finds url correctly")

    (is (not= "https://github.com/logseq/logseq"
              (editor/extract-nearest-link-from-text
               "https://github.com/logseq/logseq is #awesome :)" 0))
        "Doesn't find url if regex not passed")

    (is (= "https://github.com/logseq/logseq"
           (editor/extract-nearest-link-from-text
            "[logseq](https://github.com/logseq/logseq) is #awesome :)" 0 editor/url-regex))
        "Finds url in markdown link correctly"))

  (is (= "https://github.com/logseq/logseq"
         (editor/extract-nearest-link-from-text
          "[[https://github.com/logseq/logseq][logseq]] is #awesome :)" 0 editor/url-regex))
      "Finds url in org link correctly"))

(defn- keydown-not-matched-handler
  "Spied version of editor/keydown-not-matched-handler"
  [{:keys [value key format] :or {format "markdown"}}]
  (with-redefs [util/get-selected-text (constantly false)
                state/get-input (constantly #js {:value value})
                cursor/get-caret-pos (constantly {})]
    ((editor/keydown-not-matched-handler format)
     #js {:key key} nil)))

(deftest keydown-not-matched-handler-test
  (testing "Tag autocompletion"
    (keydown-not-matched-handler {:value "Some words " :key "#"})
    (is (= :page-search-hashtag (state/get-editor-action))
        "Autocomplete tags if starting new word")
    (state/set-editor-action! nil)

    (keydown-not-matched-handler {:value "String" :key "#"})
    (is (= nil (state/get-editor-action))
        "Don't autocomplete tags if not starting a new word")
    (state/set-editor-action! nil)

    (keydown-not-matched-handler {:value "`One backtick " :key "#"})
    (is (= :page-search-hashtag (state/get-editor-action))
        "Autocomplete tags if only one backtick")
    (state/set-editor-action! nil)

    (keydown-not-matched-handler {:value "`String#gsub and String`" :key "#"})
    (is (= nil (state/get-editor-action))
        "Don't autocomplete tags within backticks")
    (state/set-editor-action! nil)))

(defn- set-marker
  "Spied version of editor/set-marker"
  [marker content format]
  (let [actual-content (atom nil)]
    (with-redefs [editor/save-block-if-changed! (fn [_ content]
                                                  (reset! actual-content content))]
      (editor/set-marker {:block/marker marker :block/content content :block/format format})
      @actual-content)))

(deftest set-marker-org
  (are [marker content expect] (= expect (set-marker marker content :org))
    "TODO" "TODO content" "DOING content"
    "TODO" "** TODO content" "** DOING content"
    "TODO" "## TODO content" "DOING ## TODO content"
    "DONE" "DONE content" "content"))

(deftest set-marker-markdown
  (are [marker content expect] (= expect (set-marker marker content :markdown))
    "TODO" "TODO content" "DOING content"
    "TODO" "## TODO content" "## DOING content"
    "DONE" "DONE content" "content"))
