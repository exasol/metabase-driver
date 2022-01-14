(ns metabase.test.data.exasol-dataset-definitions
  (:require
   [metabase.test.data.interface :as tx]))

(tx/defdataset exasol-data-types
  "Test data with all Exasol specific data types"
  [["data_types"
    ; Columns
    [{:field-name "row_order",     :base-type :type/Integer}
     {:field-name "name",          :base-type :type/Text}
     {:field-name "hash",          :base-type {:native "HASHTYPE"}}
     {:field-name "interval_ytm",  :base-type {:native "INTERVAL YEAR TO MONTH"}}
     {:field-name "interval_dts",  :base-type {:native "INTERVAL DAY TO SECOND"}}
     {:field-name "geo",           :base-type {:native "GEOMETRY"}}]
    [; Rows
     [0 "null-values"      nil                                    nil  nil              nil]
     [1 "non-null-values" "550e8400-e29b-11d4-a716-446655440000" "5-3" "2 12:50:10.123" "POINT(2 5)"]]]])


(tx/defdataset geometry
  "Test data for testing geometry functions, see https://docs.exasol.com/sql_references/geospatialdata/geospatialdata_overview.htm#GeospatialObjects"
  [["geo"
    ; Columns
    [{:field-name "type",     :base-type :type/Text}
     {:field-name "geo",      :base-type {:native "GEOMETRY"}}]
    [; Rows
     ["point"   "POINT(2 5)"]
     ["linestring" "LINESTRING(11 1, 15 2, 15 10)"]
     ["linearring" "LINEARRING(2 1, 3 3, 4 1, 2 1)"]
     ["polygon" "POLYGON((5 1, 5 5, 9 7, 10 1, 5 1), (6 2, 6 3, 7 3, 7 2, 6 2))"]
     ["geometrycollection" "GEOMETRYCOLLECTION(POINT(2 5), LINESTRING(1 1, 15 2, 15 10))"]
     ["multipoint" "MULTIPOINT(0.1 1.4, 2.2 3, 1 6.4)"]
     ["multilinestring" "MULTILINESTRING((0 1, 2 3, 1 6), (4 4, 5 5))"]
     ["multipolygon" "MULTIPOLYGON(((0 0, 0 2, 2 2, 3 1, 0 0)), ((4 6, 8 9, 12 5, 4 6), (8 6, 9 6, 9 7, 8 7, 8 6)))"]]]])
