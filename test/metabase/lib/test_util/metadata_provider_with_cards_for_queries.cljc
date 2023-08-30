(ns metabase.lib.test-util.metadata-provider-with-cards-for-queries
  (:require
   [metabase.lib.metadata :as lib.metadata]
   [metabase.lib.test-util.mock-metadata-provider
    :as lib.tu.mock-metadata-provider]
   [metabase.lib.util :as lib.util]
   [metabase.util :as u]
   [metabase.util.malli :as mu]))

(mu/defn metadata-provider-with-cards-for-queries :- lib.metadata/MetadataProvider
  "Create a metadata provider that adds a Card for each query in `queries`. Cards do not include result
  metadata. Cards have IDs starting at `1` and increasing sequentially."
  [parent-metadata-provider :- lib.metadata/MetadataProvider
   queries                  :- [:sequential {:min 1} :map]]
  (lib.tu.mock-metadata-provider/mock-metadata-provider
   parent-metadata-provider
   {:cards (into []
                 (map-indexed
                  (fn [i query]
                    {:id            (inc i)
                     :name          (lib.util/format "Card %d" (inc i))
                     :database-id   (u/the-id (lib.metadata/database parent-metadata-provider))
                     :dataset-query query}))
                 queries)}))
