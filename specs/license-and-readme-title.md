# Licensing — add the project license, and drop the SwiftUI-original framing from the README

- **status:** done
- **priority:** P2
- **plan:** `none` for standalone work — this is not a roadmap entry. The
  license is a repository-level policy decision, and the README title change
  is a framing fix; the *what* and the rationale live in this file.

## Scope

Repository-level policy and documentation. Two repositories, no code, no
build files, no wire format. The client is the fixed point for the *license
text*: both repos carry the same `LICENSE`, so the terms a reader accepts on
one side are the terms they get on the other.

- `LICENSE` (new) — the license itself.
- `README.md` — the `## License` section, and the intro paragraph, which
  described the app as "a Kotlin Multiplatform port of an original SwiftUI
  application" and made the README's opening line about the project's
  history rather than what it is.
- `.github/workflows/release.yml` — a `### License` section in the release
  body, so the terms travel with the binaries a user downloads.

## Files

- `LICENSE` (new) — MIT + Commons Clause, plus one extra term: no rebranding.
- `README.md` — `## License` section rewritten; the intro paragraph's
  SwiftUI-original clause removed.
- `.github/workflows/release.yml` — the release body gains a `### License`
  section.
- `specs/license-and-readme-title.md` (new) — this file.

The server half of the same change is in the server repo, under its own
spec. Nothing here depends on the server, and nothing in the server depends
on this file, so the two halves could land in either order.

## Contract

- [ ] `LICENSE` exists at the repository root and is byte-identical to the
      server repository's `LICENSE`.
- [ ] The license grants use, hosting, deployment, and modification for
      personal, commercial, and production team use.
- [ ] The license withholds the right to *Sell* the software (Commons
      Clause) and the right to redistribute it under another name, brand, or
      product identity (the no-rebranding term).
- [ ] The license states that the author retains copyright and that the
      TaskTracker name and logos are the author's trademarks.
- [ ] The README's `## License` section summarises the same three points and
      links to `LICENSE`.
- [ ] The README's intro paragraph no longer describes the project as a port
      of an original SwiftUI application.
- [ ] The release body has a `### License` section naming the same terms and
      linking to the repository's `LICENSE`.
- [ ] Every relative link the two new README sections add resolves on disk
      and over HTTP.

## Validation

- `scripts/check.sh docs` — the applicable scope: no code, no build file,
  no test.
- The release workflow's YAML parses, and the new body section renders the
  intended markdown.
- Both new README links are checked with an HTTP status, not by reading the
  markdown.

## Handoff

- **Done:**
  - `LICENSE` added — MIT + Commons Clause, plus a no-rebranding term and a
    trademark paragraph. Identical in both repositories.
  - `README.md` — `## License` rewritten to state the three terms and link
    to `LICENSE`; the SwiftUI-original clause removed from the intro
    paragraph; the `iosApp/` layout line now says "SwiftUI host shell" so the
    one remaining SwiftUI mention is about this project's iOS host, not about
    a predecessor.
  - `.github/workflows/release.yml` — the release body gains a `### License`
    section.
  - The server half: `LICENSE`, the same `## License` section, a `### License`
    section in its release body, and `COPY LICENSE ./LICENSE` in the
    Dockerfile's runtime stage.
- **Pending:** Nothing. The desktop installers do **not** embed the license:
    the user chose the Docker image and the release notes instead. The
    Compose plugin supports `nativeDistributions.licenseFile`, so adding it
    later is a one-line change — but `jpackage` cannot be run on this host
    (no full JDK), so it would ship unverified until the next CI release.
- **Next step:** None — merge the two PRs.
- **Gotchas:**
  - GitHub's license *detection* will not recognise this file (the Commons
    Clause and the extra term make it a modified MIT), so the repository page
    may show "Other" rather than "MIT". That is expected, not a mistake.
  - The server repo is `amirashouri/TaskTracker`, not `.../TaskTrackerServer`.
    The server's release body linked the latter and 404'd; it now links the
    real repository, and its client link points at `TaskTrackerKMP`.

## Risks / notes

- The Commons Clause is not OSI-approved, so this is a source-available
  license and the README says so in the license file rather than claiming
  "open source".
- A modified MIT weakens the automatic-grant expectation: the two repos
  must keep the `LICENSE` files identical, or the terms drift between the
  client and the server a team deploys.
