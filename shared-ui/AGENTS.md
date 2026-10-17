# AGENTS.md — `:shared-ui`

Scoping: everything in the root `AGENTS.md` still applies. This file holds only rules
that are true here and false elsewhere.

- **`NavigationHost.kt` and this module are large, and edits to them are read deliberately.**
  *Corrected 2026-10-17:* this file used to say the two were **untracked**, so git could not
  recover an accidental edit. That was never true of this repository's git — `git ls-files`
  lists the whole module and `git status --untracked-files=all shared-ui` is clean. What the
  rule is actually for is that a broad scripted search-and-replace across a tree this size
  produces diffs no one reads; read the file and edit it deliberately, and never run a
  scripted replace here.
- **The router is the only navigation state.** Nothing here keeps a "which screen" flag;
  hosts read `Router.state` and render it. A local flag is a second answer to the same
  question and disagrees on deep links and restored sessions.
- **Host-split tests:** scenario bodies live in `commonTest`, with thin entry points per
  host — JVM, iOS, and a Robolectric `@RunWith` class for the Android host, since a
  runner annotation is not expressible from `commonTest`. New scenarios need all three
  entry points or they silently do not run somewhere.
- **This module binds, it does not decide.** Screens come from presentation modules,
  state from their models; hosts wire the two. Business rules belong in the feature
  domain, not here.
