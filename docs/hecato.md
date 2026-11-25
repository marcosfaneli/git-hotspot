# Hecato roadmap

Deterministic, offline-first architecture analysis. Never execute analyzed applications or build plugins. No LLM required. Preserve legacy hotspot CLI.

## First slice
Use the JDK compiler parsing API (parse only, no annotation processing or compilation) to inventory types, methods and mapping annotation candidates. Java 17 remains the tool baseline. Candidates are not resolved Spring endpoints. Unsupported syntax fails explicitly. No call graph or ownership inference yet.

## Next slices
1. Secure POM metadata parsing and stable graph identifiers.
2. Resolve Spring mappings and method calls; record unresolved edges.
3. JPA, JDBC and SQL evidence; distinguish mapping from actual access.
4. Graph queries, confirmed ownership catalog and deterministic rules.
5. Compact TXT/JSON and austere HTML projections, revision and input fingerprints.

Validate with synthetic fixtures, never corporate source. Compare identical inputs for deterministic output. Keep facts separate from inferences.
