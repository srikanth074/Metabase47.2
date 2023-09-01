(ns metabase.query-processor.middleware.add-timezone-info-test
  (:require
   [clojure.test :refer :all]
   [metabase.driver :as driver]
   [metabase.lib.test-metadata :as meta]
   [metabase.query-processor.middleware.add-timezone-info
    :as add-timezone-info]
   [metabase.query-processor.store :as qp.store]
   [metabase.test :as mt]))

(driver/register! ::timezone-driver, :abstract? true)

(defmethod driver/database-supports? [::timezone-driver :set-timezone] [_driver _feature _db] true)

(driver/register! ::no-timezone-driver, :abstract? true)

(defmethod driver/database-supports? [::no-timezone-driver :set-timezone] [_driver _feature _db] false)

(defn- add-timezone-info [metadata]
  (qp.store/with-metadata-provider meta/metadata-provider
    ((add-timezone-info/add-timezone-info {} identity) metadata)))

(deftest ^:paralell post-processing-test
  (doseq [[driver timezone->expected] {::timezone-driver    {"US/Pacific" {:results_timezone   "US/Pacific"
                                                                           :requested_timezone "US/Pacific"}
                                                             nil          {:results_timezone "UTC"}}
                                       ::no-timezone-driver {"US/Pacific" {:results_timezone   "UTC"
                                                                           :requested_timezone "US/Pacific"}
                                                             nil          {:results_timezone "UTC"}}}
          [timezone expected]         timezone->expected]
    (testing driver
      (mt/with-report-timezone-id timezone
        (driver/with-driver driver
          (mt/with-database-timezone-id nil
            (is (= expected
                   (add-timezone-info {})))))))))
