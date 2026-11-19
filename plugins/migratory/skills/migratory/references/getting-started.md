# Getting Started

## In a library

Depend on the starter. Import the BOM to keep migratory's modules aligned:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jwcarman.migratory</groupId>
            <artifactId>migratory-bom</artifactId>
            <version>0.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.jwcarman.migratory</groupId>
        <artifactId>migratory-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

Put the library's changelog in a package of its own, and declare it in
`src/main/resources/META-INF/migratory.properties`:

```properties
name=inventory
changelog=classpath:com/example/inventory/db/inventory-changelog.xml
```

That's all. [Declaring Migrations](declaring-migrations.md) covers the file, and
[Ordering](ordering.md) covers `after` and `before`.

## In an application

An application that uses such libraries adds nothing: the libraries bring the starter. At startup
migratory logs the order it runs them in:

```text
Running migrations in order: inventory, orders
```

Then Spring Boot runs the application's own changelog, which can reference the libraries' tables.

Spring Boot's Liquibase needs a changelog of its own (`db/changelog/db.changelog-master.yaml` by
default). An application with none either adds an empty one (`databaseChangeLog: []`) or sets
`spring.liquibase.enabled=false`, which turns off only Spring Boot's run: migratory still runs the
libraries' migrations.

## The example

[`migratory-examples`](https://github.com/jwcarman/migratory/tree/main/migratory-examples) has two
libraries, `inventory` and `orders` (which runs after inventory), and an application using both.
