# metabase-driver

An Exasol driver for [Metabase](https://www.metabase.com).

## Development

Required build tools:

* node.js, yarn
* clojure, Leiningen

```shell
git clone https://github.com/exasol/metabase-driver.git
git clone https://github.com/metabase/metabase.git
cd metabase
yarn build
# Build
./bin/build
# Run
clojure -M:run
```
