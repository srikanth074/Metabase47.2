(ns metabase.models.activity
  (:require
   [metabase.api.common :as api]
   [metabase.events :as events]
   [metabase.models.card :refer [Card]]
   [metabase.models.dashboard :refer [Dashboard]]
   [metabase.models.interface :as mi]
   [metabase.models.metric :refer [Metric]]
   [metabase.models.pulse :refer [Pulse]]
   [metabase.models.segment :refer [Segment]]
   [methodical.core :as methodical]
   [toucan2.core :as t2]))

;;; ------------------------------------------------- Perms Checking -------------------------------------------------

(def ^:private model->entity
  {"card"      Card
   "dashboard" Dashboard
   "metric"    Metric
   "pulse"     Pulse
   "segment"   Segment})

(defmulti can-?
  "Implementation for `can-read?`/`can-write?` for items in the activity feed. Dispatches off of the activity `:topic`,
  e.g. `:user-joined`. `perms-check-fn` is `can-read?` or `can-write?` and should be called as needed on models the
  activity records."
  {:arglists '([perms-check-fn activity])}
  (fn [_ {:keys [topic]}]
    topic))

;; For now only admins can see when another user joined -- we don't want every user knowing about every other user. In
;; the future we might want to change this and come up with some sort of system where we can determine which users get
;; to see other users -- perhaps if they are in a group together other than 'All Users'
(defmethod can-? :user-joined [_ _]
  api/*is-superuser?*)

;; For every other activity topic we'll look at the read/write perms for the object the activty is about (e.g. a Card
;; or Dashboard). For all other activity feed items with no model everyone can read/write
(defmethod can-? :default [perms-check-fn {model :model, model-id :model_id}]
  (if-let [object (when-let [entity (model->entity model)]
                    (entity model-id))]
    (perms-check-fn object)
    true))


;;; ----------------------------------------------- Entity & Lifecycle -----------------------------------------------

(def Activity
  "Used to be the toucan1 model name defined using [[toucan.models/defmodel]], now it's a reference to the toucan2 model name.
  We'll keep this till we replace all the symbols in our codebase."
  :model/Activity)

(methodical/defmethod t2/table-name :model/Activity [_model] :activity)

(t2/define-before-insert :model/Activity
  [activity]
  (let [defaults {:timestamp :%now
                  :details   {}}]
    (merge defaults activity)))

(t2/deftransforms :model/Activity
 {:details mi/transform-json
  :topic   mi/transform-keyword})

(doto :model/Activity
  (derive :metabase/model))

(defmethod mi/can-read? :model/Activity
  [& args]
  (apply can-? mi/can-read? args))

(defmethod mi/can-write? Activity
  [& args]
  (apply can-? mi/can-write? args))


;;; ------------------------------------------------------ Etc. ------------------------------------------------------

;; ## Persistence Functions

;; TODO - this is probably the exact wrong way to have written this functionality.
;; This could have been a multimethod or protocol, and various entity classes could implement it;
;; Furthermore, we could have just used *current-user-id* to get the responsible user, instead of leaving it open to
;; user error.

(defn record-activity!
  "Inserts a new `Activity` entry.

   Takes the following kwargs:
     :topic          Required.  The activity topic.
     :object         Optional.  The activity object being saved.
     :database-id    Optional.  ID of the `Database` related to the activity.
     :table-id       Optional.  ID of the `Table` related to the activity.
     :details-fn     Optional.  Gets called with `object` as the arg and the result is saved as the `:details` of the Activity.
     :user-id        Optional.  ID of the `User` responsible for the activity.  defaults to (events/object->user-id object)
     :model          Optional.  name of the model representing the activity.  defaults to (events/topic->model topic)
     :model-id       Optional.  ID of the model representing the activity.  defaults to (events/object->model-id topic object)

   ex: (record-activity!
        :topic       :event/segment-update
        :object      segment
        :database-id 1
        :table-id    13
        :details-fn  #(dissoc % :some-key))"
  [& {:keys [topic object details-fn database-id table-id user-id model model-id]}]
  {:pre [(keyword? topic)]}
  (let [object (or object {})]
    (first (t2/insert-returning-instances! Activity
                                           ;; strip off the `:event/` namespace of the topic, added in 0.48.0
                                           :topic       (keyword (name topic))
                                           :user_id     (or user-id (events/object->user-id object))
                                           :model       (or model (events/topic->model topic))
                                           :model_id    (or model-id (events/object->model-id topic object))
                                           :database_id database-id
                                           :table_id    table-id
                                           :custom_id   (:custom_id object)
                                           :details     (if (fn? details-fn)
                                                          (details-fn object)
                                                          object)))))
