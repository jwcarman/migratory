# Configuration Reference

| Property | Default | Meaning |
|---|---|---|
| `migratory.enabled` | `true` | Run the migrations libraries declare. When `false`, the application includes their changelogs itself. Read when the migrations would run, so it also works on a native binary. |

migratory also reads Spring Boot's `spring.liquibase.*` settings that apply to a Liquibase run; see
[Changelog Rules](changelog-rules.md#settings-that-apply).

## The declaration file

`META-INF/migratory.properties`: `name`, `changelog`, `after`, `before`. See
[Declaring Migrations](declaring-migrations.md).
