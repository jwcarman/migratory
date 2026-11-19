# Changelog Rules

migratory runs a library's changelog as written. These rules keep that safe across releases; it
cannot enforce them.

## A released changeset is never edited

Liquibase records a checksum for each changeset it runs and fails startup when a recorded
changeset's content changes. A change to a table is a new changeset.

## A changelog file never moves

Liquibase identifies a changeset by its id, author and the path of its changelog file. A moved or
renamed file makes Liquibase see new changesets and run them again. Keep the path the same forever,
or set the changelog's `logicalFilePath` to the old path.

## Changes only add

New tables, new nullable columns, new indexes. During a rolling deploy an older instance keeps
running against the new schema; a dropped or renamed column breaks it. A release that has to remove
something says so in its upgrade notes.

## Where the history goes

migratory records the libraries' changesets in the application's history table
(`DATABASECHANGELOG`, or whatever `spring.liquibase.database-change-log-table` names), beside the
application's own. A changeset's identity includes its file's path, so rows from different libraries
never collide.

## Settings that apply

The libraries' migrations run against the database and settings Spring Boot's Liquibase would use
for the application: the `@LiquibaseDataSource` bean, else `spring.liquibase.url` (with
`spring.liquibase.user`, `password` and `driver-class-name`), else `spring.liquibase.user` on the
primary data source's URL, else the primary `DataSource`; and `spring.liquibase.default-schema`,
`liquibase-schema`, `liquibase-tablespace`, the two table names, `analytics-enabled`, `ui-service`,
`show-summary`, `show-summary-output` and `license-key`.

Settings about the application's own changelog do not apply: `contexts`, `label-filter`,
`parameters`, `rollback-file`, `tag`, `clear-checksums`, `test-rollback-on-update`.

## `drop-first` is not supported

`spring.liquibase.drop-first=true` drops the schema when Spring Boot's run starts, which is after
migratory ran: the libraries' tables would be gone. An application that needs it sets
`migratory.enabled=false` and includes the libraries' changelogs in its own.

Liquibase extensions on the class path, such as `liquibase-sessionlock`, apply to migratory's run as
to the application's.
