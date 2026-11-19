# Ordering

## Between libraries

`after=x` on a migration means it runs after `x`; `before=y` means it runs before `y`. Each takes
several comma-separated names.

migratory sorts the migrations so every declaration holds. When several are ready at the same time
it runs them in order of name, so the order depends only on the declarations, never on the
class-path order, and is the same on every machine. For example, with

| name | after |
|---|---|
| `who-jdbc` | |
| `who-rbac` | `who-jdbc` |
| `who-apikey` | `who-jdbc` |
| `roux` | `who-rbac` |

the order is `who-jdbc`, `who-apikey`, `who-rbac`, `roux`.

A name that no migration on the class path has is ignored, as Spring Boot ignores an absent class in
`@AutoConfiguration(after = ...)`. A library can therefore run after an optional library when it is
installed, and the same declaration works when it is not.

Declarations that form a cycle fail startup, naming the migrations in it.

The order only has to hold on each run. Liquibase skips changesets it has already recorded, so the
order can change between releases, a new library slotting in between two others, without anything
running again.

## Against the rest of the application

migratory's runner is a Spring Boot database initializer ordered ahead of every other. So:

- every `SpringLiquibase` bean runs after it: Spring Boot's run of the application's changelog, and
  any the application declares itself;
- JPA, and any bean that depends on database initialization, waits for it;
- it runs at startup even under `spring.main.lazy-initialization=true`, so the libraries' first
  queries find their tables.
