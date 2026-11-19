# Declaring Migrations

A library declares one migration in `META-INF/migratory.properties`, in its jar.

```properties
name=who-rbac
changelog=classpath:org/jwcarman/who/rbac/db/who-rbac-changelog.xml
after=who-jdbc
```

| Key | Required | Meaning |
|---|---|---|
| `name` | yes | unique across the class path; lowercase letters and digits, words joined by single `-` |
| `changelog` | yes | the changelog's location: `classpath:` followed by a path with no leading `/` |
| `after` | no | comma-separated names of migrations this one runs after |
| `before` | no | comma-separated names of migrations this one runs before |

Whitespace around values and names is ignored, as are empty entries (`after=a, b,`).

## What fails startup

Startup fails with Spring Boot's short "APPLICATION FAILED TO START" report, naming the file's
location, when:

- `name` or `changelog` is missing or blank, or `name` breaks the naming rule;
- the file has any other key (a typo such as `afer` fails rather than being ignored);
- `changelog` does not start with `classpath:`, starts with `classpath:/`, has an empty, `.` or
  `..` segment, sits at the root of the class path rather than in a directory, or names a resource that
  does not exist;
- a key is written twice;
- a migration names itself in `after` or `before`;
- two files declare the same name;
- the `after` and `before` declarations form a cycle (the message names the migrations in it).

## Where the changelog lives

Keep the changelog, and every changelog it includes, under one package of the library's own, such
as `com/example/orders/db/`, with no classes in it. A native image includes everything under the
declared changelog's directory and nothing else, classes kept there included; see
[Native Images](native-images.md).

The changelog's path is part of each of its changesets' identity in Liquibase's history table, so
it never moves: see [Changelog Rules](changelog-rules.md).

## One migration per jar

`META-INF/migratory.properties` has one fixed name, so a jar declares one migration. A library with
several modules declares one per module jar, ordered with `after`.
