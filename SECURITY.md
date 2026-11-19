# Security policy

migratory runs database migrations at application startup, with the application's database
credentials. Thank you for taking the time to report a flaw in it privately.

## Reporting a vulnerability

Report it through GitHub's private vulnerability reporting:
[Report a vulnerability](https://github.com/jwcarman/migratory/security/advisories/new).

Please do not open a public issue, discussion or pull request for a vulnerability.

A useful report says which version is affected, what an attacker can do and what they need first,
and how to reproduce it, ideally as a failing test.

## What happens next

- You get an acknowledgement within 3 working days.
- Within 10 working days you hear whether it is confirmed, and what fix and release are planned.
- The fix ships in a new release, with a GitHub security advisory that credits you unless you
  prefer otherwise. A CVE is requested for confirmed vulnerabilities.
- Please keep the details private until the advisory is published. If a fix takes longer than
  90 days, we agree on a disclosure date with you.

migratory is maintained by one person, so these are commitments made in good faith, not a
contract.

## Supported versions

Until 1.0, only the latest minor release gets security fixes, as a patch release.

| Version        | Security fixes |
|----------------|----------------|
| latest 0.x     | Yes            |
| older 0.x      | No             |

## Scope

In scope: the migratory modules published to Maven Central (`org.jwcarman.migratory`), their
default configuration and their documented use.

Out of scope: the applications in `migratory-examples` and `migratory-native-tests`; the content of
a library's own changelog, which migratory runs as written; and problems in Liquibase, Spring Boot
or other dependencies, which should go to those projects.

## Verifying a release

Every artifact on Maven Central is signed with GPG and ships a CycloneDX SBOM
(`-cyclonedx.json`). Releases are signed with this key:

```
6268 5DD0 87F8 4A30 B6E7  7BF2 0DE1 9C9D 88B8 9E43
```

It is published on [keyserver.ubuntu.com](https://keyserver.ubuntu.com/pks/lookup?op=index&search=0x62685DD087F84A30B6E77BF20DE19C9D88B89E43).
Release builds also carry a GitHub build attestation:

```bash
gh attestation verify migratory-core-0.1.0.jar --repo jwcarman/migratory
```
