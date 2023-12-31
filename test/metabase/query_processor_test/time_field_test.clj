(ns metabase.query-processor-test.time-field-test
  (:require
   [clojure.test :refer :all]
   [metabase.driver :as driver]
   [metabase.query-processor.test-util :as qp.test-util]
   [metabase.test :as mt]))

(defn- time-query [filter-type & filter-args]
  (mt/formatted-rows [int identity identity]
    (mt/dataset test-data-with-time
      (mt/run-mbql-query users
        {:fields   [$id $name $last_login_time]
         :order-by [[:asc $id]]
         :filter   (into [filter-type $last_login_time] filter-args)}))))

(defn- normal-drivers-that-support-time-type []
  (filter mt/supports-time-type? (mt/normal-drivers)))

(deftest ^:parallel basic-test
  (mt/test-drivers (normal-drivers-that-support-time-type)
    (doseq [[message [start end]] {"Basic between query on a time field"
                                   ["08:00:00" "09:00:00"]

                                   "Basic between query on a time field with milliseconds in literal"
                                   ["08:00:00" "09:00:00"]}]
      (testing message
        (is (= (if (= :sqlite driver/*driver*)
                 [[1 "Plato Yeshua" "08:30:00"]
                  [4 "Simcha Yan"   "08:30:00"]]

                 [[1 "Plato Yeshua" "08:30:00Z"]
                  [4 "Simcha Yan"   "08:30:00Z"]])
               (time-query :between start end)))))))

(deftest ^:parallel greater-than-test
  (mt/test-drivers (normal-drivers-that-support-time-type)
    (is (= (if (= :sqlite driver/*driver*)
             [[3 "Kaneonuskatew Eiran" "16:15:00"]
              [5 "Quentin Sören" "17:30:00"]
              [10 "Frans Hevel" "19:30:00"]]

             [[3 "Kaneonuskatew Eiran" "16:15:00Z"]
              [5 "Quentin Sören" "17:30:00Z"]
              [10 "Frans Hevel" "19:30:00Z"]])
           (time-query :> "16:00:00Z")))))

(deftest ^:parallel equals-test
  (mt/test-drivers (normal-drivers-that-support-time-type)
    (is (= (if (= :sqlite driver/*driver*)
             [[3 "Kaneonuskatew Eiran" "16:15:00"]]
             [[3 "Kaneonuskatew Eiran" "16:15:00Z"]])
           (time-query := "16:15:00Z")))))

(deftest report-timezone-test
  (mt/test-drivers (normal-drivers-that-support-time-type)
    (mt/with-temporary-setting-values [report-timezone "America/Los_Angeles"]
      (is (= (cond
               (= :sqlite driver/*driver*)
               [[1 "Plato Yeshua" "08:30:00"]
                [4 "Simcha Yan" "08:30:00"]]

               ;; Databases like PostgreSQL ignore timezone information when
               ;; using a time field, the result below is what happens when the
               ;; 08:00 time is interpreted as UTC, then not adjusted to Pacific
               ;; time by the DB
               (qp.test-util/supports-report-timezone? driver/*driver*)
               [[1 "Plato Yeshua" "08:30:00-08:00"]
                [4 "Simcha Yan" "08:30:00-08:00"]]

               :else
               [[1 "Plato Yeshua" "08:30:00Z"]
                [4 "Simcha Yan" "08:30:00Z"]])
             (apply time-query :between (if (qp.test-util/supports-report-timezone? driver/*driver*)
                                          ["08:00:00"       "09:00:00"]
                                          ["08:00:00-00:00" "09:00:00-00:00"])))))))
