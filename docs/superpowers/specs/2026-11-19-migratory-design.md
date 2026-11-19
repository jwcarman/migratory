# migratory: libraries' Liquibase migrations, discovered and ordered

## Why

A library with its own tables has to get them into the application's database. Today that means
one of two things, both bad:

- The application includes the library's changelog from its own master changelog, in the right
  place. Every application has to know every library, and the order among them.
- The library runs its own Liquibase at startup. who does this on its migrations branch
  (`WhoLiquibase`), and roux copied it: about 25 lines that map `spring.liquibase.*` onto a
  `SpringLiquibase`, a `DatabaseInitializerDetector` with an order number, and a bean condition.
  Every library copies that, drifts from Spring Boot, and orders itself with numbers the libraries
  must agree on.

who also shows the second problem inside one project: its master changelog lists all seven modules
(jdbc, rbac, federated, enrollment, apikey, email-link, passkey) with `errorIfMissing="false"`, so
every startup logs "file was not found" for each module not installed.

migratory solves it once: each library jar says, in a small file, which changelog it has and what it
runs after. migratory finds those files, sorts them and runs them before the application's own
Liquibase.

## Goals

- A library declares its migration with one file in its jar and a dependency on the starter. No
  code.
- Migrations run in an order that satisfies every declared `after` and `before`, deterministically,
  before the application's own Liquibase run and before anything else that uses the database.
- Only what is on the classpath runs. Nobody lists optional pieces.
- An application can turn migratory off and include the changelogs itself, and switching between
  the two runs nothing twice.
- Works in a GraalVM native image with no configuration from libraries.

## Non-goals

- Flyway.
- Applications without Spring Boot (the core module has no Spring, but nothing is built for plain
  Java use).
- Migrating the application's own changelog. Spring Boot keeps doing that.
- Several data sources, or a schema per tenant. Such an application turns migratory off.
- Per-library switches. One switch turns everything off.

## Anti-goals

- Stopping the application's own Liquibase run. migratory never declares a `SpringLiquibase` bean
  (Spring Boot's `LiquibaseAutoConfiguration` is `@ConditionalOnMissingBean(SpringLiquibase.class)`).
- An order that depends on the classpath order, so it differs between machines.
- An application starting without a library's tables because a declaration was wrong. A bad
  declaration fails startup.

## Modules

Group `org.jwcarman.migratory`, version `0.1.0-SNAPSHOT`, Java 25, Spring Boot 4.1.2, the same
build conventions as codec (parent pom, license headers, spotless, `-Werror`, release via a GitHub
Release).

| Module | Contents |
|---|---|
| `migratory-parent` | the root pom |
| `migratory-bom` | the published modules' versions |
| `migratory-core` | reading declarations, validating them and sorting them. Plain Java, no Spring, no Liquibase |
| `migratory-autoconfigure` | the runner, the auto-configuration, the database-initializer detector, the native-image hints |
| `migratory-spring-boot-starter` | `migratory-autoconfigure` plus `spring-boot-starter-liquibase` |

A library depends on `migratory-spring-boot-starter` (who-jdbc would; roux would).

## Declaring a migration

Each jar that has a migration contains `META-INF/migratory.properties`:

```properties
name=who-rbac
changelog=classpath:org/jwcarman/who/rbac/db/who-rbac-changelog.xml
after=who-jdbc
```

| Key | Required | Meaning |
|---|---|---|
| `name` | yes | unique name; lowercase letters, digits and `-` |
| `changelog` | yes | a `classpath:` path to the changelog |
| `after` | no | comma-separated names this migration runs after |
| `before` | no | comma-separated names this migration runs before |

One migration per jar. Any other key is an error, so a typo (`afer`) fails rather than being
ignored (*my call*).

## Discovery

`ClassLoader.getResources("META-INF/migratory.properties")` on the default class loader: one URL
per jar or directory holding the file. No directory is listed, so it works in a native image (the
same mechanism as `META-INF/spring.factories`).

## Validation

Startup fails, naming the file's URL, when:

- `name` or `changelog` is missing or blank, or `name` breaks the naming rule;
- a key is unknown;
- `changelog` is not a `classpath:` path, or the resource does not exist;
- two files declare the same name;
- the ordering has a cycle (the message names the migrations in it).

A name in `after` or `before` that no file declares is ignored, like
`@AutoConfiguration(after = …)` for a class that is absent, so a library can run after an optional
one when it is installed.

## Ordering

A topological sort (Kahn's algorithm) over the declarations: `after=x` on `y` is an edge x → y,
`before=x` on `y` is an edge y → x. When several migrations are ready at once, they run in
alphabetical order of name. The order depends only on the set of declarations, never on the
classpath order.

The order only has to satisfy the declarations on each run; it may change between releases (a new
library slotting in). Liquibase skips changesets it has already recorded, so earlier runs never
constrain later ones.

At startup, migratory logs the order once at INFO.

## Running

- **One run.** migratory runs one Liquibase update over a root changelog it generates in memory,
  which includes each declared changelog in order (*my call*: one lock acquisition and one summary,
  rather than one per library).
- **Recorded paths.** Each changeset is recorded with the same file path as when an application's
  own master changelog does `<include file="…"/>` of that changelog, so an application can switch
  between migratory and including by hand and nothing runs twice. A test pins this, the way who's
  `LiquibaseChangelogTest` does today.
- **History table.** The application's (`DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`, or what
  `spring.liquibase.database-change-log-table` and `database-change-log-lock-table` name). Changesets
  are identified by id, author and path, so a library's rows never collide with the application's.
- **Data source.** The one Spring Boot's Liquibase would use: the `@LiquibaseDataSource` bean,
  else `spring.liquibase.url` (with `spring.liquibase.user`/`password`/`driver-class-name`), else
  `spring.liquibase.user` on the primary data source's URL, else the primary `DataSource`. who's
  `WhoLiquibase` mirrors Boot 4.1.2 exactly; migratory takes that code.
- **Settings.** `spring.liquibase.default-schema`, `liquibase-schema`, `liquibase-tablespace`, the
  table names, `analytics-enabled`, `ui-service`, `show-summary`, `show-summary-output` and
  `license-key` apply, as in who's runner. Settings about the application's changelog (`contexts`,
  `label-filter`, `parameters`, `rollback-file`, `tag`, `clear-checksums`, `test-rollback-on-update`)
  do not.
- **`drop-first`** is not supported: it would wipe the libraries' tables after migratory created
  them. The guide says so.
- **Extensions.** Liquibase extensions on the classpath (such as `liquibase-sessionlock`) apply,
  through Liquibase's service loader. migratory adds none.

## When it runs

| Situation | Result |
|---|---|
| declarations found, Liquibase on the classpath, `migratory.enabled` not `false` | migratory runs them |
| `migratory.enabled=false` (default `true`) | nothing runs; the application includes the changelogs itself |
| no declarations found | nothing runs |
| no `DataSource` bean and no `spring.liquibase.url` | nothing runs, quietly, as Spring Boot's own Liquibase does (*my call*: web slices such as `@WebMvcTest`, and contexts that exclude the data source, must keep working) |
| declarations found, Liquibase not on the classpath (only possible without the starter) | **startup fails**: "migratory found migrations (who-jdbc, roux, …) but Liquibase is not on the classpath: add spring-boot-starter-liquibase, or set migratory.enabled=false and include their changelogs yourself." |

`spring.liquibase.enabled=false` turns off only Spring Boot's run of the application's changelog; it
does not turn off migratory (*my call*: an application with no changelog of its own sets it, and
still needs the libraries' tables).

## Ordering against the rest of Spring

- migratory's runner is a database initializer: a `DatabaseInitializerDetector` registered in
  `META-INF/spring.factories`, ordered first (`Ordered.HIGHEST_PRECEDENCE`). JPA and every bean that
  depends on database initialization wait for it, and every `SpringLiquibase` bean (Spring Boot's or
  the application's) runs after it.
- The runner is not lazy: under `spring.main.lazy-initialization=true` it still runs at startup, so
  the application never starts without the libraries' tables.
- Test slices (`@JdbcTest`, `@DataJpaTest`, `@DataJdbcTest`) run it: the auto-configuration is
  listed in
  `META-INF/spring/org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization.imports`.

who's migrations branch has all of this working and tested (`WhoMigrationsAutoConfiguration`,
`WhoLiquibaseDatabaseInitializerDetector`); migratory takes it over.

## Native images

Spring's ahead-of-time step (`process-aot`) runs on the JVM with the application's full runtime
classpath, so it sees every declaring jar the image will contain. The order depends only on the
declarations, so it can be computed there.

- **The sorted list is baked in at build time.** migratory contributes to the AOT step: it
  discovers, validates and sorts the declarations, and puts the result into the image (generated
  code that builds the runner with the list, or a single generated resource the runner reads;
  builder's choice). In a native image migratory never searches jars at runtime. A bad declaration
  or a cycle fails the native build.
- **Resources.** The same contribution registers, as resources, everything under each declared
  changelog's directory (so changelogs it includes, and `sqlFile` scripts, are covered). A library
  adds no native configuration; its included changelogs must sit under its main changelog's
  directory (the guide says so).
- **`migratory.enabled` is checked at runtime, not as a `@Conditional`.** Spring evaluates
  conditions at build time for a native image, which would fix the switch when the image is built.
  The runner always exists (when Liquibase and a data source do), reads `migratory.enabled` when it
  runs, and does nothing when it is `false`, so the switch behaves the same on the JVM and in a
  native image.
- Liquibase itself already runs in a native image (who's native tests run its changelogs on
  PostgreSQL; Spring Boot supplies Liquibase's hints).

## Rules for libraries' changelogs

The guide states them; migratory cannot enforce them:

- A released changeset is never edited; a change is a new changeset.
- A changelog file never moves or is renamed (its path is part of each changeset's identity).
- Changes only add, unless a release says otherwise, so an older instance still running during a
  rolling deploy keeps working.

## Approaches considered

1. **A file per jar, found by name (chosen).** No code in libraries, works in a native image, and
   the jar's presence is the switch.
2. **A bean per library, with `@MigrateAfter` and `@MigrateBefore` on the `@Bean` method.** Can be
   conditional on properties, but needs Spring code in every library; with one global switch nothing
   needs the conditions.
3. **Scanning a directory** (`classpath*:META-INF/migratory/*.properties`). Several migrations per
   jar, but a native image cannot list directories.
4. **A Liquibase extension** (a virtual changelog the application includes). Needs the application
   to include it, which is the hand-wiring this removes.

Module layout: one jar was considered; the owner chose the idiomatic core / autoconfigure / starter /
bom split.

Precedent: Spring Boot orders auto-configurations with `after`/`before` and ignores absent targets;
`spring.factories` is found the same way.

## Open-source practices

migratory follows every practice who follows (owner's requirement); who's repository is the
reference for each file and workflow, adapted to migratory's names.

**Repository files:** `README.md` with who's badge set (CI, license, Java, Maven Central, OpenSSF
Scorecard, OpenSSF Best Practices, SonarCloud ratings, quality gate and coverage), `LICENSE`
(Apache 2.0), `CHANGELOG.md` (Keep a Changelog, `[Unreleased]`), `CONTRIBUTING.md`,
`CODE_OF_CONDUCT.md`, `SECURITY.md`, `GOVERNANCE.md`, `ROADMAP.md`, `context7.json`.

**GitHub:** `dependabot.yml`, issue templates (bug report, feature request, question), pull-request
template, CodeQL config; workflows `maven.yml` (build, tests, SonarCloud), `codeql.yml`,
`scorecard.yml`, `docs.yml` (mkdocs `--strict` to GitHub Pages), `api-docs.yml` (Javadoc),
`maven-publish.yml` (on `release: created`), `native.yml` (manual only), `fuzz.yml` (Jazzer).

**Build** (as who's root pom): flatten, enforcer, compiler with `-Werror`, dependency analysis,
surefire and failsafe, spotless, license headers (`-Plicense`), JaCoCo with a `migratory-coverage`
aggregate module for SonarCloud, an opt-in PIT profile, and a release profile with Central
publishing, sources, Javadoc, a CycloneDX SBOM and GPG signing.

**Extra modules:** `migratory-coverage` (aggregate coverage), `migratory-native-tests` (in a profile,
run on a build host through `scripts/native-remote.sh` with `NATIVE_HOST`, as who), and
`migratory-examples` (an application with two small library modules declaring migrations, one after
the other).

**Fuzzing:** Jazzer tests for the declaration reader and the sorter (malformed files, arbitrary
graphs: never an unexpected exception, never an order that breaks a declaration).

**Docs:** a mkdocs site (`docs/`, `mkdocs.yml`, pinned `requirements.txt`): getting started,
declaring a migration, ordering, turning it off and including by hand, the changelog rules, native
images, `drop-first`, test slices, and upgrading. `llms.txt` published with the site, as codec.

**Agent skill:** a Claude Code plugin like who's (`.claude-plugin/marketplace.json`,
`plugins/migratory/skills/migratory/SKILL.md`, references synced from the guides by
`plugins/SyncReferences.java` during `generate-resources`, `docs/agent-skill.md`).

**Left to the owner** (outside the repository): creating the GitHub repository, the SonarCloud
project, the OpenSSF Best Practices entry, the Context7 claim, GitHub Pages, and the release secrets
(Central and GPG). None of them exists yet. The build copies the workflows and configuration as
if they did; nothing in the local build depends on them, and the owner sets them up before the
first push (*my call*). The native tests use the same build host as who, named by `NATIVE_HOST`.

## Testing

- `migratory-core`: unit tests for reading, validation (each failure above) and sorting (ties,
  missing targets, `before`, cycles), with declaration files from test resources and test jars.
- `migratory-autoconfigure`: PostgreSQL through Testcontainers; ordering against Spring Boot's
  Liquibase, the data-source and settings cases, `migratory.enabled=false`, Liquibase missing,
  lazy initialization, a test slice, and the recorded-paths pin.
- A native-image test, run on a build host (as who does), with two declaring test jars.

## Afterwards (not this project)

who moves onto migratory: each module ships `META-INF/migratory.properties`, who-jdbc depends on the
starter, and `WhoLiquibase`, its detector, `who.migrations.enabled`, the master changelog's role at
runtime and the library API (`INITIALIZER_ORDER`, `getDataSource()`) go away. roux drops its runner
and ships a file. Each is planned separately, after migratory 0.1.0 is released.

## Left to the builder

- Class and package names below `org.jwcarman.migratory`.
- Generated code or a generated resource for the baked-in list (see Native images).
- How the in-memory root changelog is served to Liquibase (a resource accessor or similar), as long
  as the recorded-paths test passes.
- The failure-analysis mechanism and exact wording beyond the message above.
- How test jars are built for the discovery tests.
