# Agents

Subagent definitions for VibeTradingBotV5. Each agent embodies a specific role/perspective and is invoked via the Agent tool with `subagent_type=<name>`. When each agent is appropriate is described in the `description` field of the agent's frontmatter.

Each agent is identified by the question it answers — use this as the index:

- **architect** — *will this hold up long-term?* → [`architect.md`](architect.md)
- **domain-expert** — *does this correctly reflect the trading domain model?* → [`domain-expert.md`](domain-expert.md)
- **risk-engineer** — *what will break and how will it manifest?* → [`risk-engineer.md`](risk-engineer.md)
- **trading-risk-officer** — *does this make financial sense?* → [`trading-risk-officer.md`](trading-risk-officer.md)
- **knowledge-curator** — *where to put it and what to update?* → [`knowledge-curator.md`](knowledge-curator.md)

For team-level context (KPIs, division of responsibility, when to use which agent) see `.claude/working-with-claude.md`, section "Команда агентов". The roster evolves through scenarios 2-3 in `.claude/flow/playbook.md`; all changes are recorded in `.claude/pipeline-evolution-log.md`.
