# AGENTS.md — `:packages:database`

Scoping: everything in the root `AGENTS.md` still applies. This file holds only rules
that are true here and false elsewhere.

- **No foreign keys, no cascades.** Deleting a project must write an outbox row for the
  project *and* everything under it, and a DB-level cascade cannot write into `outbox`.
  Cross-table references are plain indexed strings; the repository notices a sprint going
  away, not the schema. See `SprintRecord`'s KDoc.
- **One DAO owns one table.** Every statement against `work_items` lives on `WorkItemDao`,
  even sprint-scoped ones — a sprint-owned `UPDATE work_items` is how a second writer
  silently touches the rank. See `SprintDao`'s KDoc.
- **Inserts are ABORT, not REPLACE.** A conflict is a bug, not a repair; silent replace
  hides duplicate-key races the outbox protocol must surface.
- **A schema change ships three things:** the entity edit, the generated schema JSON, and
  a migration covered by both the drift test and the migration test. One without the
  others is an incomplete change.
