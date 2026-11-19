---
name: migratory
description: Sets up and uses the migratory library (org.jwcarman.migratory), which runs Liquibase changelogs that Spring Boot libraries declare in META-INF/migratory.properties, in after/before order, before the application's own Liquibase. Use this skill whenever a project depends on migratory (migratory-spring-boot-starter, migratory-bom, META-INF/migratory.properties, migratory.* properties), or the user asks to give a Spring Boot library its own database tables, ship a library's Liquibase changelog, order one library's migrations after another's, or move a library off its own Liquibase runner.
---

# migratory

migratory lets a Spring Boot library ship its own tables. The library declares its Liquibase
changelog in one file; migratory finds every declaration on the class path, sorts them by `after`
and `before`, and runs them in one Liquibase update before the application's own Liquibase.

Requirements: Java 25+, Spring Boot 4.1+, Liquibase (through the starter).

The `references/` folder holds migratory's own guides and changelog, copied from the repository
for this version of the skill. They are the source of truth: read the relevant guide before writing
code, and take property names and keys from it rather than from memory.

## Which migratory these guides describe

The guides are copied from main, which can be ahead of the latest release. `references/CHANGELOG.md`
says what arrived when: anything under `## [Unreleased]`, or under a version newer than the
project's, is not available there. Find the project's version (the `migratory-bom` import or the
starter's version) before using anything.

## Giving a library its tables

1. Depend on `org.jwcarman.migratory:migratory-spring-boot-starter`.
2. Put the changelog, and everything it includes, under one package of the library's own, such as
   `com/example/orders/db/`.
3. Add `src/main/resources/META-INF/migratory.properties`:

   ```properties
   name=orders
   changelog=classpath:com/example/orders/db/orders-changelog.xml
   after=inventory
   ```

   `name` is lowercase letters and digits joined by `-`; `changelog` is `classpath:` plus a path with
   no leading `/`; `after` and `before` are comma-separated names. No other keys are allowed. Read
   `references/declaring-migrations.md` and `references/ordering.md`.

4. Test it with a `@SpringBootTest` or `@JdbcTest` on a real database (`references/testing.md`).

## Rules that keep upgrades safe

- Never edit a released changeset; add a new one.
- Never move or rename a changelog file: its path is part of every changeset's identity.
- Only add: tables, nullable columns, indexes. See `references/changelog-rules.md`.

## Moving a library onto migratory

Keep the existing changelog at its existing path and declare it; remove the library's own runner;
tell applications to drop their include of it. Liquibase records the same path either way, so
nothing runs again. See `references/upgrading.md`.

## Applications

An application using such libraries adds nothing. To include the changelogs by hand instead, set
`migratory.enabled=false` (`references/turning-it-off.md`).

To rebuild the database on every start in development, set `migratory.drop-first=true`, never
`spring.liquibase.drop-first`, which fails startup while libraries declare migrations
(`references/changelog-rules.md`).
