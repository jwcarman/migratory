# Roadmap

What we're looking at next. These are themes, not promises. Every feature starts with a design doc
in `docs/superpowers/specs/` before any code. What has shipped is in the [CHANGELOG](CHANGELOG.md).

## Next

- **who and roux move onto migratory.** Each who module and roux declare their migrations in
  `META-INF/migratory.properties`, and their own runners go away.

## Under consideration

- **Several migrations in one jar**, if a library ever needs it.
- **Contexts and labels for library migrations**, if an application needs to filter them.

## Not planned

- **Flyway.** migratory is built on Liquibase's changeset identity and history table.
- **Several data sources or a schema per tenant.** Such an application turns migratory off and runs
  the changelogs in its own per-tenant runs.
