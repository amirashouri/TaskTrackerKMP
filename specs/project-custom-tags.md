# Project custom tags — admin-defined chips on cards and backlog rows

- **status:** done
- **priority:** P2
- **plan:** no `docs/PLAN.md` entry — standalone cross-repo feature. The server
  half is [`../../TaskTrackerServer/specs/project-custom-tags.md`](../../TaskTrackerServer/specs/project-custom-tags.md).

## Scope

- `:packages:core` — `TagID`, `randomTagID`, `parseTagID`.
- `:packages:domain` — `TagColor`, `TagIcon`, `ProjectTag`, `TagRules`,
  `Project.tags`, `WorkItem.tagIDs`, and the cap/dedupe normalisation in `WorkItem.init`.
- `:packages:database` — `ProjectRecord.tags`, `WorkItemRecord.tagIDs`, version 6.
- `:packages:feature-projects-data` — record mapping, the admin project DTO, and the
  work-item create/update parameters.
- `:packages:feature-projects-domain` — `SaveWorkItem` tag parameter, cap/dedupe, and the
  recurrence copy.
- `:packages:design-system` — `TagColors.kt`, `TagIcons.kt`, string resources.
- `:packages:feature-projects-presentation` — the editor picker, the icon strips, and the
  admin catalog section.
- `:shared-ui` — thread the catalog through `NavigationHost` and the admin destination.

No new sync entity, outbox entity, sync scope, or Room table: tags ride the project
entity and are written only through the existing admin project API.

## Contract

- [x] An admin can add and remove tags (name, colour, icon) in the project editor; a
      blank or duplicate name and a catalog past `TagRules.MAX_PER_PROJECT` are refused
      inline, not silently dropped.
- [x] A member's work-item editor offers the project's catalog and caps selection at
      `TagRules.MAX_PER_ITEM`; a sixth tap is refused and a tap on a selected tag clears it.
- [x] A validation-less payload is still safe: `WorkItem.init` and `SaveWorkItem` dedupe
      and cap, so a server that sends six ids or the same id twice produces a valid item.
- [x] Board cards and backlog rows draw up to five icon-only chips bottom-left, resolving
      each id against the project catalog and dropping ids the catalog no longer defines.
- [x] A deleted tag leaves stale ids on items, and no reader shows a blank chip or
      rewrites the row. The editor filters the ids when the catalog is known and leaves
      them alone when the catalog read failed (`null`).
- [x] Filtering a stale id at load is **not** a user edit: the editor's `original` draft
      is filtered the same way, so `hasChanges` is false on an untouched form.
- [x] A corrupt tag JSON blob costs the project's catalog, not the project; an
      unparsable tag id costs one chip, not the card.
- [x] `Project.memberIDs` decodes the admin API's `memberIds` spelling via `@JsonNames`,
      so the admin project payload still decodes into the shared model.
- [x] Room bumps to 6 and regenerates `6.json`; the historical schemas stay untouched.

## Validation

- Run: focused JVM tests for the touched modules (under a minute); the three-host run is the release gate: `scripts/check.sh full` — the change spans rendering, storage and the graph.
- Counts: new `commonTest` cases move **all three** host totals; `test-baseline.json` was
  regenerated with `scripts/test-baseline.py` and `check-baseline.sh` passes.
- The tag strips and picker are asserted structurally (test tags and the resolved-chip
  list), not by rendered pixels; the tint tokens are new values rather than changes to a
  value an existing test pins, so no `test-reviewer` pixel pass was required.

## Handoff

- **Done:** client domain/core/data/sync/presentation and server schema, entity and admin
  payload are implemented. `scripts/check.sh full` is green on JVM, iOS simulator and the
  Android host; the server's `scripts/check.sh` is green.
- **Pending:** none in this repo. The server branch and PR are tracked in its own spec.
- **Next step:** merge both PRs; the client is the fixed point and can land first.
- **Gotchas:**
  - `availableTags == null` means "the catalog read failed", not "no tags". Do not
    collapse it to `emptyList()`, or an item loses its tags whenever the project read is
    slow.
  - The admin API spells the member field `memberIds`; the domain property is `memberIDs`
    and relies on `@JsonNames`. A new response key still needs the same treatment.
  - Tag colours and icons are closed vocabularies on the client and open strings on the
    server. Adding a value is a client-only change; an unknown server value is not drawn.
  - Admin tag management is a `tagsSection` slot on the shared `ProjectEditorScreen`, not
    a flag — the member editor must keep rendering without it.

## Risks / notes

- The delete story is "stale ids are dropped on read". Anything that starts *writing*
  resolved tags back onto items would resurrect the old catalog.
- Tag names are normalised and deduped case-insensitively in the admin editor because two
  identical 12dp chips are indistinguishable.
