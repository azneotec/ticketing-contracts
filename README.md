# ticketing-contracts

The contracts between ticketing services, published as versioned Java artifacts:

| Artifact (`io.github.azneotec.ticketing:…`) | Source | What it holds |
|---|---|---|
| `ticketing-contracts-grpc` | `proto/` | Protobuf messages + gRPC stubs for internal synchronous calls (ADR-010) |
| `ticketing-contracts-avro` | `avro/` | Avro `SpecificRecord` classes for Kafka events (ADR-030) |

Both artifacts share one version, taken from the Git tag.

## Layout
```
proto/ticketing/<context>/v<N>/*.proto     Protobuf package ticketing.<context>.v<N>
avro/<context>/v<N>/<Name>.avsc            Avro namespace io.github.azneotec.ticketing.<context>.v<N>
buf.yaml                                   Buf lint (STANDARD) and breaking (FILE) rules
contracts-grpc/, contracts-avro/           Gradle modules that generate and package the Java code
buildSrc/                                  Build logic: Avro code generation, Buf and Avro checks
samples/verify-failure-cases.sh            Proves breaking changes fail the build
```

Current contracts:
- `ticketing.catalogue.v1.CatalogueService`: `GetEvent`, `ListEvents` (paged). Java package `io.github.azneotec.ticketing.catalogue.grpc.v1`.
- `io.github.azneotec.ticketing.catalogue.v1.EventPublished`: emitted when an event is published.

Times are always an instant in UTC (`google.protobuf.Timestamp`, Avro `timestamp-millis`) plus an IANA zone id (`Europe/London`), so the local start time can be rebuilt.

## Build
```bash
./gradlew build publishToMavenLocal
```
Needs JDK 25 and [Buf](https://buf.build) (`brew install bufbuild/buf/buf`). Uses `ticketing.java-conventions` from mavenLocal (version in `gradle.properties`), so publish `ticketing-build-conventions` first.

`./gradlew check` also runs:
- `bufLint`: Buf's STANDARD naming and style rules.
- `bufBreaking`: compares `proto/` with the last `vX.Y.Z` tag using Buf's FILE rules.
- `checkAvroCompatibility`: checks that every `.avsc` is BACKWARD compatible with the same file in the last tag (Avro's `SchemaValidator`, "can read" strategy). It also checks that no published schema was deleted, and lints namespace/path, `doc` on every type and field, a default on every enum and on every new field.

Before the first release there is no tag, so both compatibility checks print "nothing to compare against" and pass.

Generated code (under `build/generated`) is not formatted or coverage-checked (conventions 0.1.1). The modules contain only generated code, so the coverage gate is skipped; the tests show the generated code works.

## Changing a contract
**Protobuf** (`buf breaking` enforces this):
- Fields are identified on the wire by number. Never change or reuse a number, and never change a field's type.
- Adding a field with a new number is safe.
- Removing or renaming a field fails under FILE rules even with `reserved`, because generated Java code (`getCity()`) would disappear for callers. Mark it `[deprecated = true]` instead; remove it in a new package (`v2`). If you do delete it in `v2`, add `reserved <number>;` and `reserved "<name>";`.

**Avro** (`checkAvroCompatibility` enforces this, BACKWARD):
- New fields need a default (often `["null", …]` with `"default": null`): consumers on the new schema read old events that lack the field.
- Enums need a default symbol: a reader maps symbols it does not know to it.
- Do not delete a published schema; publish a new `v<N+1>` alongside it instead.

## Using the Avro classes
Avro only instantiates classes from trusted packages (a defence against deserialization attacks). Every service that reads these events must start its JVM with:
```
-Dorg.apache.avro.SERIALIZABLE_PACKAGES=io.github.azneotec.ticketing
```

## Versioning and releasing
Same scheme as `ticketing-build-conventions` (`gradle/version.gradle.kts`): exactly on tag `vX.Y.Z` with a clean tree → `X.Y.Z`; after it, **or on it with uncommitted changes** → `X.Y.(Z+1)-SNAPSHOT`; no tag → `0.1.0-SNAPSHOT`. Semantic versioning: a new contract or field → minor; a breaking change only in a new package version (`v2`), released as a major.

To release:
1. `master` is clean; `./gradlew build` and `samples/verify-failure-cases.sh` pass.
2. `git tag -a vX.Y.Z -m "vX.Y.Z"`
3. `./gradlew build publishToMavenLocal`
4. `git push && git push origin vX.Y.Z`

From then on, the checks compare against the new tag.

A dirty working tree (anything in `git status --porcelain`) is always a `-SNAPSHOT`, even on a release tag, so a local build can never overwrite a release in `~/.m2` (since 0.1.1; proven by `samples/verify-failure-cases.sh`).

## Requirements
JDK 25, Buf. The Gradle wrapper downloads Gradle 9.9.0. Commits follow [Conventional Commits](https://www.conventionalcommits.org/); trunk-based on `master`.
