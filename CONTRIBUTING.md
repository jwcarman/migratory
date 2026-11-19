# Contributing to migratory

Thanks for helping. migratory runs other libraries' database migrations at startup, so a mistake
in it can stop an application from starting or run a changeset in the wrong order. That shapes how
changes are made here.

**Found a vulnerability?** Don't open an issue or a pull request. Follow [SECURITY.md](SECURITY.md).

## Before you write code

- **Bugs:** open an issue with a way to reproduce it, ideally a failing test.
- **Features and behavior changes:** open an issue first and describe the problem. Larger features
  get a short design document in `docs/superpowers/specs/` before code; see
  [ROADMAP.md](ROADMAP.md) for what is already planned.
- Small fixes to docs and typos can go straight to a pull request.

## Building

You need Java 25 and Docker (the tests start PostgreSQL with Testcontainers).

```bash
./mvnw clean verify          # compile, test, and check formatting
./mvnw spotless:apply        # format the code
./mvnw clean verify -Pci     # with coverage, as CI runs it
./mvnw clean verify -Ppit    # mutation testing, now and then (not part of CI)
./mvnw -Plicense com.mycila:license-maven-plugin:check   # license headers
```

### Fuzzing

The declaration reader and the sorter have [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer)
fuzz tests, the `*FuzzTest` classes in `migratory-core`. In a normal build each replays its saved
inputs as a regular test. To generate new inputs, run one at a time with `JAZZER_FUZZ=1`:

```bash
JAZZER_FUZZ=1 ./mvnw test -pl migratory-core -Dtest=MigrationSorterFuzzTest
```

A failure writes the input to `src/test/resources/<package>/<Class>Inputs/<method>/` as
`crash-<hash>`: fix the bug and commit that file, so the input stays a regression test. The
[Fuzz workflow](.github/workflows/fuzz.yml) fuzzes each target for ten minutes every week.

### Native tests

`migratory-native-tests/native-app` uses the two example libraries; its `NativeAppIT` runs it
against PostgreSQL and checks the order the migrations ran in, and that `migratory.enabled=false`
works at runtime. Only the `native-tests` profile builds it.

```bash
# on the JVM, in seconds
./mvnw -Pnative-tests verify -pl migratory-native-tests/native-app -am
# as a native image, in minutes: needs GraalVM 25 as JAVA_HOME
./mvnw -Pnative-tests -Dnative verify -pl migratory-native-tests/native-app -am
# on a Linux build host over ssh (GraalVM and Docker there)
NATIVE_HOST=<ssh host> NATIVE_JAVA_HOME=<GraalVM home on the host> scripts/native-remote.sh
```

The [Native workflow](.github/workflows/native.yml) runs them when started by hand.

To work on the docs site, run `pip install --require-hashes -r docs/requirements.txt`, then
`mkdocs serve`. CI builds it with `mkdocs build --strict`, so broken links fail the build.

## What a pull request needs

- **Tests** for every behavior change. A change to ordering or discovery needs a test that fails
  without it.
- **No new warnings.** The build compiles with `-Werror`. Fix the cause instead of suppressing it.
- **Explicit imports only**, no `import foo.*`, including static imports.
- **Formatting:** `./mvnw spotless:apply` (Google Java Format).
- **Docs:** update the guide under `docs/guides/` that the change affects, and add a line under
  `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md). The agent skill carries copies of the guides
  and the changelog in `plugins/migratory/skills/migratory/references`; a build from the root
  refreshes them (`./mvnw -N generate-resources` is enough), so commit them with the change.
- **Commits:** a short, conventional summary such as `feat: ...`, `fix: ...`, `test: ...`,
  `docs: ...` or `build: ...`.

Sonar and CodeQL run on every pull request; please address what they find.

## Releases

Releases are cut by the maintainer from `main`. The version in the poms stays at the next
`-SNAPSHOT`; publishing a GitHub Release builds, signs and publishes to Maven Central. The Native
workflow must be green on the release commit.

## Governance

Who decides what, and how, is in [GOVERNANCE.md](GOVERNANCE.md).

## Conduct

Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
