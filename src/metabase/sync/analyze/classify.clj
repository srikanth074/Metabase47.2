(ns metabase.sync.analyze.classify
  "Analysis sub-step that takes a fingerprint for a Field and infers and saves appropriate information like special
  type. Each 'classifier' takes the information available to it and decides whether or not to run. We currently have
  the following classifiers:

  1.  `name`: Looks at the name of a Field and infers a semantic type if possible
  2.  `no-preview-display`: Looks at average length of text Field recorded in fingerprint and decides whether or not we
      should hide this Field
  3.  `category`: Looks at the number of distinct values of Field and determines whether it can be a Category
  4.  `text-fingerprint`: Looks at percentages recorded in a text Fields' TextFingerprint and infers a semantic type if
      possible

  All classifier functions take two arguments, a `FieldInstance` and a possibly `nil` `Fingerprint`, and should return
  the Field with any appropriate changes (such as a new semantic type). If no changes are appropriate, a classifier may
  return nil. Error handling is handled by `run-classifiers` below, so individual classiers do not need to handle
  errors themselves.

  In the future, we plan to add more classifiers, including ML ones that run offline."
  (:require
   [clojure.data :as data]
   [metabase.lib.schema.metadata :as lib.schema.metadata]
   [metabase.models.interface :as mi]
   [metabase.sync.analyze.classifiers.category :as classifiers.category]
   [metabase.sync.analyze.classifiers.name :as classifiers.name]
   [metabase.sync.analyze.classifiers.no-preview-display
    :as classifiers.no-preview-display]
   [metabase.sync.analyze.classifiers.text-fingerprint
    :as classifiers.text-fingerprint]
   [metabase.sync.interface :as i]
   [metabase.sync.util :as sync-util]
   [metabase.util :as u]
   [metabase.util.log :as log]
   [metabase.util.malli :as mu]
   [toucan2.core :as t2]
   [metabase.lib.dispatch :as lib.dispatch]
   [metabase.query-processor.store :as qp.store]
   [metabase.lib.metadata :as lib.metadata]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                         CLASSIFYING INDIVIDUAL FIELDS                                          |
;;; +----------------------------------------------------------------------------------------------------------------+

(def ^:private values-that-can-be-set
  "Columns of Field that classifiers are allowed to set."
  #{:semantic_type :preview_display :has_field_values :entity_type})

(def ^:private FieldOrTableInstance
  [:or
   ::lib.schema.metadata/column
   ::lib.schema.metadata/table])

(mu/defn ^:private save-model-updates!
  "Save the updates in `updated-metadata` (can be either a `Field` or `Table`)."
  [original-metadata :- FieldOrTableInstance
   updated-metadata  :- FieldOrTableInstance]
  (assert (= (type original-metadata) (type updated-metadata)))
  (let [[_ values-to-set] (data/diff original-metadata updated-metadata)]
    (when (seq values-to-set)
      (log/debug (format "Based on classification, updating these values of %s: %s"
                         (sync-util/name-for-logging original-metadata)
                         values-to-set)))
    ;; Check that we're not trying to set anything that we're not allowed to
    (doseq [k (keys values-to-set)]
      (when-not (contains? values-that-can-be-set k)
        (throw (Exception. (format "Classifiers are not allowed to set the value of %s." k)))))
    ;; cool, now we should be ok to update the model
    (when values-to-set
      (t2/update! (case (lib.dispatch/dispatch-value original-metadata)
                    :metadata/column :model/Field
                    :metadata/table  :model/Table)
                  (u/the-id original-metadata)
                  (update-keys values-to-set u/->snake_case_en))
      true)))

(def ^:private classifiers
  "Various classifier functions available. These should all take two args, a `FieldInstance` and a possibly `nil`
  `Fingerprint`, and return `FieldInstance` with any inferred property changes, or `nil` if none could be inferred.
  Order is important!

  A classifier may see the original field (before any classifiers were run) in the metadata of the field at
  `:sync.classify/original`."
  [classifiers.name/infer-and-assoc-semantic-type
   classifiers.category/infer-is-category-or-list
   classifiers.no-preview-display/infer-no-preview-display
   classifiers.text-fingerprint/infer-semantic-type])

(mu/defn run-classifiers :- ::lib.schema.metadata/column
  "Run all the available `classifiers` against `field` and `fingerprint`, and return the resulting `field` with
  changes decided upon by the classifiers. The original field can be accessed in the metadata at
  `:sync.classify/original`."
  [field       :- ::lib.schema.metadata/column
   fingerprint :- [:maybe i/Fingerprint]]
  (reduce (fn [field classifier]
            (or (sync-util/with-error-handling (format "Error running classifier on %s"
                                                       (sync-util/name-for-logging field))
                  (classifier field fingerprint))
                field))
          (vary-meta field assoc :sync.classify/original field)
          classifiers))


(mu/defn ^:private classify!
  "Run various classifiers on `field` and its `fingerprint`, and save any detected changes."
  ([field :- ::lib.schema.metadata/column]
   (classify! field (or (:fingerprint field)
                        (when (qp.store/initialized?)
                          (:fingerprint (lib.metadata/field (qp.store/metadata-provider) (u/the-id field))))
                        (t2/select-one-fn :fingerprint :model/Field :id (u/the-id field)))))

  ([field      :- ::lib.schema.metadata/column
    fingerprint :- [:maybe i/Fingerprint]]
   (sync-util/with-error-handling (format "Error classifying %s" (sync-util/name-for-logging field))
     (let [updated-field (run-classifiers field fingerprint)]
       (when-not (= field updated-field)
         (save-model-updates! field updated-field))))))


;;; +------------------------------------------------------------------------------------------------------------------+
;;; |                                        CLASSIFYING ALL FIELDS IN A TABLE                                         |
;;; +------------------------------------------------------------------------------------------------------------------+

(mu/defn ^:private fields-to-classify :- [:maybe [::lib.schema.metadata/column]]
  "Return a sequences of Fields belonging to `table` for which we should attempt to determine semantic type. This
  should include Fields that have the latest fingerprint, but have not yet *completed* analysis."
  [table :- ::lib.schema.metadata/table]
  (seq (t2/select :model/Field
         :table_id            (u/the-id table)
         :fingerprint_version i/latest-fingerprint-version
         :last_analyzed       nil)))

(mu/defn classify-fields!
  "Run various classifiers on the appropriate FIELDS in a TABLE that have not been previously analyzed. These do things
  like inferring (and setting) the semantic types and preview display status for Fields belonging to TABLE."
  [table :- ::lib.schema.metadata/table]
  (when-let [fields (fields-to-classify table)]
    {:fields-classified (count fields)
     :fields-failed     (->> fields
                             (map classify!)
                             (filter (partial instance? Exception))
                             count)}))

(mu/defn ^:always-validate classify-table!
  "Run various classifiers on the `table`. These do things like inferring (and setting) entitiy type of `table`."
  [table :- ::lib.schema.metadata/table]
  (let [updated-table (sync-util/with-error-handling (format "Error running classifier on %s"
                                                             (sync-util/name-for-logging table))
                        (classifiers.name/infer-entity-type table))]
    (if (instance? Exception updated-table)
      table
      (save-model-updates! table updated-table))))

(mu/defn classify-tables-for-db!
  "Classify all tables found in a given database"
  [_database :- ::lib.schema.metadata/database
   tables :- [::lib.schema.metadata/table]
   log-progress-fn]
  {:total-tables      (count tables)
   :tables-classified (sync-util/sum-numbers (fn [table]
                                               (let [result (classify-table! table)]
                                                 (log-progress-fn "classify-tables" table)
                                                 (if result
                                                   1
                                                   0)))
                                             tables)})

(mu/defn classify-fields-for-db!
  "Classify all fields found in a given database"
  [_database :- ::lib.schema.metadata/database
   tables :- [::lib.schema.metadata/table]
   log-progress-fn]
  (apply merge-with +
         {:fields-classified 0, :fields-failed 0}
         (map (fn [table]
                (let [result (classify-fields! table)]
                  (log-progress-fn "classify-fields" table)
                  result))
              tables)))
