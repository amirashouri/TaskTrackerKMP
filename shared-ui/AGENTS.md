# AGENTS.md — `:shared-ui`

Scoping: everything in the root `AGENTS.md` still applies. This file holds only rules
that are true here and false elsewhere.

- **`NavigationHost.kt` and most of this module are untracked.** Git cannot recover an
  accidental edit. Read the file and edit deliberately; never run broad scripted
  search-and-replace in this tree.
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
