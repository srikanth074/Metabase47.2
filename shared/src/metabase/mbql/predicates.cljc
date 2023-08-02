(ns metabase.mbql.predicates
  "Predicate functions for checking whether something is a valid instance of a given MBQL clause."
  (:require
   [metabase.lib.schema.expression.temporal
    :as lib.schema.expression.temporal]
   [metabase.lib.schema.temporal-bucketing
    :as lib.schema.temporal-bucketing]
   [metabase.mbql.schema :as mbql.s]
   [metabase.util.malli.registry :as mr]))

(defn- validator [schema]
  (complement (mr/explainer schema)))

(def ^{:arglists '([unit])} DateTimeUnit?
  "Is `unit` a valid datetime bucketing unit?"
  (validator ::lib.schema.temporal-bucketing/unit))

(def ^{:arglists '([unit])} TimezoneId?
  "Is `unit` a valid datetime bucketing unit?"
  (validator ::lib.schema.expression.temporal/timezone-id))

(def ^{:arglists '([ag-clause])} Aggregation?
  "Is this a valid Aggregation clause?"
  (validator mbql.s/Aggregation))

(def ^{:arglists '([field-clause])} Field?
  "Is this a valid Field clause?"
  (validator mbql.s/Field))

(def ^{:arglists '([filter-clause])} Filter?
  "Is this a valid `:filter` clause?"
  (validator mbql.s/Filter))

(def ^{:arglists '([filter-clause])} DatetimeExpression?
  "Is this a valid DatetimeExpression clause?"
  (validator mbql.s/DatetimeExpression))

(def ^{:arglists '([field-clause])} FieldOrExpressionDef?
  "Is this a something that is valid as a top-level expression definition?"
  (validator mbql.s/FieldOrExpressionDef))
