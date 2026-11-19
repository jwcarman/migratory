# Upgrading

Each release that changes what a library or an application has to do gets a section here, with the
steps to upgrade.

## 0.1.0

The first release.

### Moving a library onto migratory

A library that ran its own Liquibase, or asked applications to include its changelog:

1. Depend on `migratory-spring-boot-starter`.
2. Add `META-INF/migratory.properties` naming the library's existing changelog, at its existing
   path, with `after` for the libraries whose tables it references.
3. Remove the library's own runner.
4. Tell applications to remove their include of the library's changelog. Liquibase records the same
   path either way, so their existing databases run nothing again.
