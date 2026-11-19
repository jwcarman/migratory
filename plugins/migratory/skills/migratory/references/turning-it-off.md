# Turning It Off

```properties
migratory.enabled=false
```

turns off every library's migration. The application then includes the changelogs itself, in its
own master changelog, in an order that satisfies the libraries' `after` and `before`:

```yaml
databaseChangeLog:
  - include:
      file: classpath:com/example/inventory/db/inventory-changelog.xml
  - include:
      file: classpath:com/example/orders/db/orders-changelog.xml
```

Do this when the application runs migrations outside the application, or once per tenant in a
schema of its own.

Liquibase records each changeset with the same path whether migratory runs it or the application's
changelog includes it, so an application can switch either way and nothing runs twice.

There is one switch for everything: migratory has no per-library settings. With it off, migratory
does not read the declarations at all, so a broken one in a library's jar does not stop the
application. (A native image is still built from the declarations; see
[Native Images](native-images.md).)

`spring.liquibase.enabled=false` does not turn migratory off; it turns off only Spring Boot's run of
the application's own changelog.

## Without Liquibase

The starter brings Liquibase. An application that depends on `migratory-autoconfigure` alone, with
declaring libraries on the class path but no Liquibase, fails startup:

```text
migratory found migrations (inventory, orders) but Liquibase is not on the classpath: add
spring-boot-starter-liquibase, or set migratory.enabled=false and include their changelogs yourself.
```

## Without a database

With no `DataSource`, no JDBC connection details and no `spring.liquibase.url`, migratory does
nothing, as Spring Boot's Liquibase does. An application with `spring.liquibase.url` and no
`DataSource`, such as one using R2DBC, gets its libraries' migrations run there. Web test slices such as `@WebMvcTest` start as usual.
