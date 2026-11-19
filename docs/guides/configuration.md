# Configuration Reference

| Property | Default | Meaning |
|---|---|---|
| `migratory.enabled` | `true` | Run the migrations libraries declare. When `false`, the application includes their changelogs itself. Read when the migrations would run, so it also works on a native binary. |
| `migratory.drop-first` | `false` | Drop everything in the schema, the application's tables and the history table included, before migratory's run, so every start rebuilds the database. Development only; see [Changelog Rules](changelog-rules.md#rebuilding-the-schema-on-every-start). |

migratory also reads Spring Boot's `spring.liquibase.*` settings that apply to a Liquibase run; see
[Changelog Rules](changelog-rules.md#settings-that-apply).

## The declaration file

`META-INF/migratory.properties`: `name`, `changelog`, `after`, `before`. See
[Declaring Migrations](declaring-migrations.md).
