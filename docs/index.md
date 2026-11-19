# migratory

Liquibase migrations for Spring Boot libraries, discovered and run in order.

*Are you suggesting coconuts migrate?* No, but your libraries' tables can.

A library with its own tables has to get them into the application's database. Without migratory
that means either asking every application to include the library's changelog in the right place
of its own master changelog, or the library running its own Liquibase with code every library
copies. migratory replaces both with one file per library:

```properties
# META-INF/migratory.properties, in the library's jar
name=orders
changelog=classpath:com/example/orders/db/orders-changelog.xml
after=inventory
```

At startup migratory finds every such file on the class path, sorts them by `after` and `before`,
and runs them in one Liquibase update, before the application's own Liquibase and before anything
else that uses the database. Only what is on the class path runs; nobody lists optional pieces.

| Module | What it is |
|---|---|
| `migratory-spring-boot-starter` | what a library depends on: the auto-configuration plus Spring Boot's Liquibase starter |
| `migratory-autoconfigure` | the runner, its ordering against Spring Boot's Liquibase, native-image support |
| `migratory-core` | reading, validating and sorting declarations; plain Java |
| `migratory-bom` | the versions of the modules above |

Start with [Getting Started](guides/getting-started.md).
