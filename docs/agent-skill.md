# Agent Skill

migratory ships an [Agent Skill](https://docs.claude.com/en/docs/agents-and-tools/agent-skills/overview)
for coding agents such as Claude Code. With it, an agent asked to give a library its own tables, or
to move a library's Liquibase changelog onto migratory, reads these guides before writing code.

## Install

In Claude Code, add migratory's repository as a plugin marketplace and install the plugin:

```text
/plugin marketplace add jwcarman/migratory
/plugin install migratory@migratory
```

For other agents that read [Agent Skills](https://agentskills.io), install it with Vercel's
[`skills`](https://github.com/vercel-labs/skills) command:

```bash
npx skills add https://github.com/jwcarman/migratory/tree/main/plugins/migratory/skills/migratory
```

An agent without the skill can still read these docs as Markdown:
[`llms.txt`](https://jwcarman.github.io/migratory/llms.txt) indexes the guides and
[`llms-full.txt`](https://jwcarman.github.io/migratory/llms-full.txt) has every guide in one file.

## What it knows

- How to declare a library's migration, and the rules that fail startup.
- How to order it against other libraries with `after` and `before`.
- The changelog rules that keep upgrades safe: never edit a released changeset, never move a file.
- Every guide on this site and the changelog, copied into the skill so it matches the version it
  describes.
