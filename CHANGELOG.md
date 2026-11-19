# Changelog

All notable changes to migratory are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and migratory uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- First release: libraries declare a Liquibase changelog in `META-INF/migratory.properties`, and
  migratory runs every declared changelog in dependency order before the application's own
  Liquibase.
- `migratory.drop-first` rebuilds the whole schema on every start, for development databases;
  `spring.liquibase.drop-first` fails startup while migratory has migrations to run.

### Requirements

- Spring Boot 4.1 or later: built against 4.1.3, and CI also builds and tests on 4.2.0.
