# Owned scalar damage modifiers

Baseline: upstream `b216d2b21fe9f25d242ec3832234a6a3092ab029`.
The production change is only the eight-line constructor hunk in
`EntityDamageEvent.java`; no private distribution changes are applied.

The scalar overload creates a private `DamageModifierFloatEnumMap` which has no
caller alias. A private constructor can keep this owned map. The public Map
overload still unconditionally creates its defensive `EnumMap` copy. Both paths
keep the immutable original snapshot before BASE validation and resistance
processing. The resistance expression, including its double arithmetic and
integer amplifier overflow, and the virtual getter/setter order are unchanged.

## Standalone actual-source evidence

Temurin 25.0.4+7, macOS aarch64, default escape analysis, three independent JVM
pairs. Each variant compiles the actual baseline/current target and these
unchanged stock sources: `EntityEvent`, `Event`, `Cancellable`, `HandlerList`,
`EventException`, `Effect`. Other native APIs come from the same core dependency
in both variants, not from source replacements or stubs.

| Escaped actual event path | Before B/event | After B/event | Saved B/event |
| --- | ---: | ---: | ---: |
| Scalar constructor | 504 | 416 | 88 |
| Public Map constructor, control | 416 | 416 | 0 |

Every one of the 15 measured passes per variant has these same values. Each
pass constructs 20,000 events after 60,000 warmup events per path. Both paths
perform one fresh Float boxing per event, and every actual event escapes
through a volatile reference. The reusable input map on the public path is
outside the measured interval. That path controls for common allocation and
escape analysis; it is not an event reuse or no-boxing comparison.

Each JVM verifies 11,613 all-cause/value/resistance pairs and 3,842,371 explicit
behavior checks. It covers all 21 causes; 79 raw float patterns, including NaN
payloads, infinities, signed zero, subnormals and random bits; seven resistance
states; each modifier; mutation and cancellation flags. Additional controls
verify EnumMap/HashMap/immutable-map inputs, later caller mutation, mutation
during `hasEffect`, immutable original snapshots, virtual getter/setter order,
throwing hooks at each stage, and malformed-input/null exception ordering.
The scalar/public-Map results match within every JVM, and the full before/after
digest matches across all six JVMs:

`a450651cfc66c7929cc27609dd3deb0ad81fc3aee6c91beb7d5f9c085649961e`

The main probe adapts and extends the historical 243-line focused regression
as explicit standalone checks. It imports no test framework and runs no server,
event bus, armor/projectile suite, build tool or test runner. `Unsafe` only
allocates a fixture Entity without constructing a world; its two relevant
effect methods have explicit fixture behavior. This proves constructor
semantics and allocation for these profiles, not server TPS, live combat,
plugin reflection into private fields, or every JVM configuration.

## Reproduction

Use an existing compatible native core JAR and its real annotations dependency;
the runner never builds/downloads them. The measured dependency was selected
core279, SHA-256
`dca4d9c92bfed4b1fbdbb8c58808f948d15cdf3fb4f75ed1bfe8ea5da52a2636`.
The seven stock source files are freshly compiled into variant-first output
directories, so the target/event/Effect bytecode in that JAR is not used.

```sh
python3 tools/performance/owned-scalar-damage-probe.py \
  --core-jar /path/to/compatible-core.jar \
  --dependency /path/to/annotations-23.0.0.jar \
  --java-home /path/to/jdk-25 \
  --out /path/outside/source-clone
```

Each subprocess has a deadline: compilation 60 seconds, main 45 seconds,
metadata commands 10 seconds. The runner saves compile/main logs, dependency
hashes, all raw allocation passes, source hashes and `summary.json` outside the
clone. No JUnit/Gradle/Maven invocation is used.

Target source SHA-256 before:
`2060b420e92f342706e536ab95392d65afeeb83249bce8a23420784ba4b0db3d`.
Target source SHA-256 after:
`fa53336cde8ac5df8e92f0033c2d907100bf76942f3f28a45eccaebb4fa853dc`.
