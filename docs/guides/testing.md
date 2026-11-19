# Testing

## In an application

`@SpringBootTest` runs migratory as the application does. So do Spring Boot's database test slices,
`@JdbcTest`, `@DataJpaTest` and `@DataJdbcTest`: they run Spring Boot's Liquibase, and migratory
runs before it. Keep such slices on a real database of the kind the libraries' changelogs are
written for:

```java
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {
  // ...
}
```

A slice that never touches the libraries' tables can set `migratory.enabled=false`.

## In a library

Test the library's migration the way an application meets it: a `@SpringBootTest` (or a `@JdbcTest`)
against a real database, with the library and its starter on the test class path, asserting the
tables exist. A test that declares the migrations of the libraries this one runs after needs those
libraries on its test class path too.
