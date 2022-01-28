# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.15.0] - 2022-01-28
### Deprecated
- We will drop support for synching of build servers (e.g. `java -cp buildviz-0.14.3-standalone.jar buildviz.go.sync`) in favour of [build-facts](https://github.com/cburgmer/build-facts). This version supports both the new endpoint for build-facts, and the legacy synching code, so migration is possible withing the same version of buildviz.

### Changed
- The error response for invalid JSON payloads will now return the exact key in camel-case, not the "Clojurized" version.

### Fixed
- The server correctly returns a 404 HTTP status code for unknown URLs instead of a 500.
