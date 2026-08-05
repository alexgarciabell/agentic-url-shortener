# Scenarios

## Greenfield

Creates a complete Java 21 Maven URL shortener from an empty workspace, including production code, H2, tests, Actuator,
and an executable JAR.

## Brownfield

Requires an existing `pom.xml`, snapshots source, applies a scoped change, verifies the full Maven build, and repairs
production code within bounded limits.

## Ambiguous

Documents a safe assumption and chooses Brownfield when a Maven project exists or Greenfield otherwise. The built-in
interpretation is a reversible URL-safety improvement: accept only valid HTTP/HTTPS destinations.
