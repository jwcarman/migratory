# migratory

Liquibase migrations for Spring Boot libraries, discovered and run in order.

*Are you suggesting coconuts migrate?* No, but your libraries' tables can.

A Spring Boot library that owns tables has no good way to ship them. Either every application
includes the library's changelog in its own master changelog, in the right order, leaving out the
optional modules it doesn't use, and remembers on every upgrade; or the library runs its own
Liquibase and gets ordering, test slices, lazy initialization and native images right on its own.
Schema scripts behind an `initialize-schema` switch avoid both, but they create tables once and
never evolve them.

migratory replaces all of that with one file in the library's jar:

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
