# VibeTradingBotV5

Algorithmic trading bot for crypto exchanges. Java 21, Spring Boot 3, PostgreSQL, Flyway, MapStruct, Spring Cloud Vault.

Currently supports OKX. Architecture designed for adding more exchanges.

## How to work on this project

This project uses a structured collaboration model with Claude. Before making any non-trivial change, read:

- `.claude/working-with-claude.md` — core working model: source hierarchy, agents, skills, ADRs, knowledge capture rules.
- `.claude/flow/playbook.md` — operational scenarios: how to make decisions, create or update agents and skills, run retrospectives.

Pipeline history lives in `.claude/pipeline-evolution-log.md`. Read it to understand how the current setup evolved.

## Documentation organization

The repository separates product documentation from Claude collaboration artifacts:

- **`docs/`** — product documentation: models, API, conventions, specs, glossary. Describes what the bot is.
- **`.claude/`** — everything related to working on this project through Claude: working artifacts (ADR, backlog), process documents (`working-with-claude.md`, `flow/playbook.md`, `pipeline-evolution-log.md`), Claude configuration (`project-instructions.md`, `agents/`, `skills/`), historical snapshots (`strategy-summary/`).

ADR-0003 records the rationale for this split.

## Source of truth hierarchy

When sources disagree, follow this priority (top wins):

1. **`docs/spec/`** — target specification (models, processes, interfaces). Being built incrementally. **Primary source of truth for topics already migrated here.**
2. **`docs/domain/`**, **`docs/api/`** — current live specifications. Used as source of truth for topics **not yet migrated to `docs/spec/`**. Files migrated to spec are marked `[MIGRATED → docs/spec/...]` on the first line and stop being authoritative.
3. **Source code in `src/main/java/`**, **`.claude/adr/`** (Architecture Decision Records), **`docs/conventions/`** (code style, tech radar).
4. **Other docs in `docs/`** (planning, ops).
5. **Archives**: `docs/context/`, `docs/deprecated/`, `docs/planning/` legacy entries, any path containing `old/`, `archive/`, `deprecated/`. **Reference only. Never authoritative.** Use only if a topic has no coverage in higher priority sources, AND the content does not contradict them.

Conflicts between levels 1 and 2 (or 1 and 3) are resolved by recording an ADR.
Conflicts between higher and lower priorities are resolved silently in favor of the higher.

## Repository layout
src/main/java/                 Java source (~357 files)
src/main/resources/db/migration/  Flyway migrations (V1..V7)
src/test/java/                 Tests (currently empty — see ADRs for test strategy)
docs/
spec/                        ★ TARGET specification (growing, primary source of truth)
conventions/                 Code style, tech radar
domain/                      Current live specification (legacy, being migrated)
api/                         API documentation (OKX, internal)
planning/                    Roadmap, milestones, execution log (long-term plans)
ops/                         Operational notes
README.md                    Documentation map
GLOSSARY.md                  (TBD) Domain terms
.claude/
working-with-claude.md       Collaboration model (read this)
project-instructions.md      Backup of claude.ai Custom Instructions
pipeline-evolution-log.md    History of changes to the working setup
agents/                      Subagent definitions (architect, domain-expert, etc.)
skills/                      Reusable knowledge and procedures
adr/                         Architecture Decision Records (append-only) + README
flow/                        Operational procedures (playbook)
planning/                    Working backlog
strategy-summary/            Versioned strategy snapshots (v1, v2, …)
notes/                       TBD — created when first observation log appears

## Key project conventions

- **HTTP client**: target is Spring `RestClient`. Existing code uses `RestTemplate` — migration planned, see ADRs.
- **Migrations**: managed by Flyway. Migration discipline — see skill `flyway-migrations` (when created).
- **Exchange integration**: each exchange has dedicated client, mappers, and skill. See `okx-api` skill for OKX specifics.
- **Domain layer must not depend on infrastructure.** Enforced manually; will be subject of an ADR.
- **No silent state changes.** Order/Deal/Position state transitions must go through their respective state machines.

## Conflicts and ambiguities

If something is unclear, ambiguous, or appears to contradict:

1. Check the source hierarchy above. Higher priority wins.
2. If conflict is between equal-priority sources — surface it explicitly. Do not silently choose.
3. Conflicts of substance should be resolved by creating an ADR, not by quiet edits.

## Working with Claude Code in this project

- Default model for non-trivial work: **Opus**. Switch to Sonnet for routine code work to save budget.
- Do **not** commit by default — leave changes in working tree for human review.
- **Auto-staging.** After creating or modifying files in this project, run `git add` on those files so they appear as staged changes in IDEA. This applies to created files, modified files, and deletions. Do NOT commit — staging only.
- When making changes that touch documented models or processes, **proactively** check and update related documents. This is a baseline behavior, not an optional step.
- At the end of significant work, run `knowledge-curator` to verify nothing was left undocumented.

For operational scenarios (creating agents, updating skills, retrospectives), see `.claude/flow/playbook.md`.
