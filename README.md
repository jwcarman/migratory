# migratory

[![CI](https://github.com/jwcarman/migratory/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/migratory/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https://raw.githubusercontent.com/jwcarman/migratory/main/pom.xml&query=//*[local-name()='java.version']/text()&label=Java&color=orange)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/maven-central/v/org.jwcarman.migratory/migratory-spring-boot-starter)](https://central.sonatype.com/artifact/org.jwcarman.migratory/migratory-spring-boot-starter)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/jwcarman/migratory/badge)](https://scorecard.dev/viewer/?uri=github.com/jwcarman/migratory)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_migratory&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_migratory)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_migratory&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_migratory)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_migratory&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_migratory)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_migratory&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=jwcarman_migratory)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_migratory&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jwcarman_migratory)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_migratory&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jwcarman_migratory)

Liquibase migrations for Spring Boot libraries, discovered and run in order. A library with its own
tables puts one small file in its jar saying which changelog it has and what it runs after.
migratory finds every such file on the class path, sorts them, and runs them in one Liquibase
update before the application's own Liquibase, against the same database and settings. The
application lists nothing, and no library lists the optional pieces that might not be installed.

*Are you suggesting coconuts migrate?* No, but your libraries' tables can.

**Documentation: [jwcarman.github.io/migratory](https://jwcarman.github.io/migratory/)**

## Requirements

- Java 25+
- Spring Boot 4.1+ (built against 4.1.2), with Liquibase

## Declaring a migration (in a library)

Depend on the starter:

```xml
<dependency>
    <groupId>org.jwcarman.migratory</groupId>
    <artifactId>migratory-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Add `src/main/resources/META-INF/migratory.properties`:

```properties
name=orders
changelog=classpath:com/example/orders/db/orders-changelog.xml
after=inventory
```

| Key | Required | Meaning |
|---|---|---|
| `name` | yes | unique name: lowercase letters and digits, words joined by `-` |
| `changelog` | yes | the changelog's `classpath:` location |
| `after` | no | comma-separated names this migration runs after |
| `before` | no | comma-separated names this migration runs before |

A name in `after` or `before` that no library declares is ignored, so a library can run after an
optional one when it is installed. Migrations ready at the same time run in order of name.

## Using libraries that declare migrations (in an application)

Nothing to add: the libraries bring the starter, and their tables exist before the application's
own changelog runs, so it can reference them. The starter turns on Spring Boot's Liquibase too, which
needs a changelog of the application's own: add one (an empty `databaseChangeLog: []` will do), or
set `spring.liquibase.enabled=false`, which leaves migratory running. To run the changelogs yourself (a schema per tenant,
migrations outside the application), set `migratory.enabled=false` and include them in your own
changelog; nothing runs twice when you switch.

## Building

```bash
./mvnw clean verify   # needs Docker: the tests use PostgreSQL through Testcontainers
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE)
