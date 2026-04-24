# japicmp API-Compatibility Gate — Policy (v1.0)

**Status**: Wired in `kairo-api/pom.xml` as of v1.0.0-RC1 (2026-04-24). First enforcement
run becomes useful in v1.0.0-RC2, after RC1 is published as the comparison baseline.

**Related**: ADR-023 (SPI Stability Policy) • `spi-census-v1.0.md` • `spi-annotation-application.md`

---

## Purpose

Prevent silent binary/source-incompatible changes to any type, method, or field in
`io.kairo.api.*` that carries the `@io.kairo.api.Stable` annotation. The gate is a
mechanical backstop behind the human review process: once ADR-023 is in effect, changing
a `@Stable` signature requires an explicit decision. japicmp ensures the decision was
actually made rather than slipping through a review.

## Scope

- **Module**: `kairo-api` only. `kairo-core` and other runtime modules are not public
  contract — they may evolve freely.
- **Surface**: `@Stable`-annotated types, methods, and fields. `@Experimental` / `@Internal`
  / unannotated elements pass through silently — this is intentional per ADR-023.
- **Break kinds enforced**: binary-incompatible AND source-incompatible modifications.
  (Both flags set — a source-only change that is still binary-compatible, e.g., renaming
  a parameter, still breaks the build.)

## Mechanics

The profile is defined in `kairo-api/pom.xml` under `<profile id="api-compat">`.

| Element | Value | Rationale |
|---------|-------|-----------|
| Plugin | `com.github.siom79.japicmp:japicmp-maven-plugin:0.23.1` | Latest stable at time of wiring |
| Goal | `cmp` | Compare two artifacts |
| Phase | `verify` | Runs after `package`, so the new jar exists on disk |
| Old version | `${project.groupId}:${project.artifactId}:${api.baseline.version}` | Property-driven for promotion |
| New version | `${project.build.directory}/${project.artifactId}-${project.version}.jar` | The jar being built |
| `includeAnnotations` | `@io.kairo.api.Stable` | Only Stable surface is policed |
| `breakBuildOnBinaryIncompatibleModifications` | `true` | Binary breaks fail the build |
| `breakBuildOnSourceIncompatibleModifications` | `true` | Source breaks fail the build too |
| `onlyModified` | `true` | Don't re-report unchanged APIs |
| `onlyBinaryIncompatibleModifications` | `true` | Skip purely-additive diffs in the report |

## Activation

```bash
# Default build — gate NOT active
mvn -pl kairo-api -am verify

# Gate active — compares against baseline
mvn -pl kairo-api -am verify -Papi-compat -Dapi.baseline.version=1.0.0-RC1

# Gate active but suppressed (RC1 bootstrap, incidental debugging)
mvn -pl kairo-api -am verify -Papi-compat -Djapicmp.skip=true
```

## Baseline Lifecycle

| Release | `api.baseline.version` default | Enforcement state |
|---------|--------------------------------|--------------------|
| v1.0.0-RC1 | empty | No-op (nothing to compare) |
| v1.0.0-RC2 | `1.0.0-RC1` | First enforcement — freezes RC1 surface |
| v1.0.0 GA | `1.0.0-RC2` (or latest RC) | Enforcement active |
| v1.0.x patches | latest v1.0.x | Enforcement active |
| v1.1.0 | `1.0.0` | Enforcement active |
| v2.0.0-RC1 | empty | Reset — major version allowed to break |

**Promotion rule**: when cutting a release `vX.Y.Z`, the *next* release bumps
`api.baseline.version` default in `kairo-api/pom.xml` to `vX.Y.Z`. This is a one-line PR
that the release engineer owns; the SOT release checklist calls it out explicitly.

## What Constitutes a Break

Per ADR-023 §"Semantics of additive change":

**Binary-incompatible (always fails)**:
- Remove or rename a `@Stable` type / method / field
- Change method signature (return type, parameter types, throws clause)
- Change field type
- Narrow visibility (public → package-private)
- Add a new abstract method to a `@Stable` interface (use `default` instead)
- Add a new enum value NOT at the tail
- Add a new record component (breaks canonical constructor)

**Source-incompatible (always fails)**:
- Rename a public method parameter (Javadoc reference break)
- Change a method's generic bounds in a way that forces call-site updates

**Additive (always allowed)**:
- Add a new `default` method on an interface
- Add a new public method / field on a class
- Add a new enum value at the tail
- Add a new static factory on a record

## Intentional-Break Workflow

When a deliberate break must ship (e.g., critical security fix, RC → GA tightening):

1. File ADR documenting the break, rationale, and migration path.
2. In the commit, add an explicit suppression entry to `kairo-api/pom.xml` under the
   japicmp `<parameter><ignoreMissingClasses>` or `<excludes>` section, referencing the
   ADR number as an XML comment.
3. Bump the SOT row with a BREAKING-CHANGE note.
4. Update `CHANGELOG.md` under a `### Breaking changes` heading.

Do not silently disable the profile or set `breakBuildOn...=false`. The gate must always
be capable of flagging breaks — suppressions must be targeted and documented.

## CI Integration

- **Default CI**: runs `mvn clean verify` without `-Papi-compat`. Gate does not run.
- **Release CI**: release.yml workflow adds `-Papi-compat
  -Dapi.baseline.version=<previous-release>` as a required step before publishing.
- **PR CI (planned, post-RC2)**: add a dedicated "API Compat" job that activates the
  profile against the last released baseline. Failing job blocks merge. Will be wired
  in a follow-up PR once RC1 is published.

## Failure Triage

When the gate fails:

1. Inspect the HTML / XML report at `kairo-api/target/japicmp/`.
2. For each reported break, ask: was this intentional?
   - **Intentional** → follow the intentional-break workflow above.
   - **Accidental** → revert the offending change; ADR-023's rules apply.
3. If the break is intentional but contained to `@Experimental` / `@Internal`: the gate
   should not have fired. Verify the `includeAnnotations` scope is not being misread.

## Known Limitations

- japicmp runs only against the kairo-api module. Transitive contract leaks
  (e.g., a `@Stable` method returning a `kairo-core` type that then changes shape) are
  NOT caught. Policy-level mitigation: `kairo-api` must not expose non-JDK / non-Reactor
  types from other kairo modules. A separate static analysis check (TBD) can enforce
  this physically.
- Generic-type bound changes occasionally produce false positives when combined with
  inheritance — handle case-by-case.

---

## Open items

- [ ] First enforcement run at v1.0.0-RC2 cut.
- [ ] CI workflow integration (planned post-RC1 publish).
- [ ] Baseline promotion automation (release script should bump the property).

*This doc will evolve as the gate accrues real-world findings. Keep it current.*
