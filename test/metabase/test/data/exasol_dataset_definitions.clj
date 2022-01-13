(ns metabase.test.data.exasol-dataset-definitions
  (:require
   [metabase.test.data.interface :as tx]))

(tx/defdataset exasol-data-types
  "Test data with all Exasol specific data types"
  [["data_types"
    ; Columns
    [{:field-name "number",        :base-type :type/Integer}
     {:field-name "name",          :base-type :type/Text}
     {:field-name "hash",          :base-type {:native "HASHTYPE"}}
     {:field-name "interval_ytm",  :base-type {:native "INTERVAL YEAR TO MONTH"}}
     {:field-name "interval_dts",  :base-type {:native "INTERVAL DAY TO SECOND"}}
     {:field-name "geo",           :base-type {:native "GEOMETRY"}}
;
     ]

    [; Rows
    ; [nil   nil]
    ; [1     "Name A"]

     [nil   nil        nil                                    nil  nil              nil]
     [1     "Name A"  "550e8400-e29b-11d4-a716-446655440000" "5-3" "2 12:50:10.123" "POINT(2 5)"]
     ;
     ]]])
