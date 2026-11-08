---
name: bedrock-protocol
description: Develop, review, debug, or update Minecraft Bedrock protocol support in Nukkit-MOT — add or change DataPacket fields, packet IDs, version gates, BinaryStream serialization, clientbound/serverbound handling, NetEase variants, and CloudburstMC cross-codec regression tests using EndstoneMC protocol-docs. Use whenever a task involves protocol version upgrades, packet encoding or decoding, wire-format mismatches, trailing bytes, malformed-packet disconnects, VarInt/optional/repeated fields, protocol-docs lookups, or synchronization of the protocol-docs reference cache.
---

# Nukkit-MOT Bedrock Protocol Development

Use [EndstoneMC protocol-docs](https://github.com/EndstoneMC/protocol-docs) as the field-level reference for standard Bedrock, and treat the actual wire format of the target protocol version as the completion criterion. Never apply the latest documentation, the current CloudburstMC implementation, or legacy MOT code indiscriminately to every version.

## Hard Rules

- Before editing code, identify **standard versus NetEase, packet direction, target protocol number, and the protocol boundary where each field was introduced or changed**.
- Never guess field order, numeric width, endianness, VarInt signedness, enum values, or optional-field presence markers.
- Implement `encode()` and `decode()` as mirror images of the same wire format. For a deliberately one-directional packet, use the existing `encodeUnsupported()` or `decodeUnsupported()` methods.
- Guard new-version fields with protocol checks so they cannot leak to older clients. Standard-client logic must not overwrite NetEase behavior when both share a protocol number.
- Never rely solely on encoding with MOT and decoding with MOT; shared mistakes cancel out in that setup.
- Do not refactor unrelated networking, item, or entity code while fixing a protocol issue.

## Required References

Before adding or changing fields, comparing protocol versions, or diagnosing wire misalignment, read [references/protocol-docs.md](references/protocol-docs.md). Follow its procedure for selecting a matching ref, recursively resolving types, and checking JSON metadata.

When the cache is missing or the documentation version must be changed or updated, run the sync script from the repository root:

```bash
# Upstream default branch
python3 .agents/skills/bedrock-protocol/scripts/sync_protocol_docs.py

# Specific branch, tag, or commit
python3 .agents/skills/bedrock-protocol/scripts/sync_protocol_docs.py --ref <ref>
```

The script stores a shallow fetch in the skill's `.cache/protocol-docs/` directory. This is a reproducible, Git-ignored cache; do not edit or commit its contents. When the target version is known, use the matching `--ref` instead of unconditionally replacing a version-matched cache with the default branch.

Additional references, using public upstream sources or existing clones without assuming fixed directories:

| Purpose | Reference repository |
|---|---|
| Version-aware schemas and field changes between adjacent versions | [EndstoneMC/bedrock-protocol](https://github.com/EndstoneMC/bedrock-protocol) |
| CloudburstMC Java codecs | [CloudburstMC/Protocol](https://github.com/CloudburstMC/Protocol) |
| Independent Go implementation in gophertunnel | [Sandertv/gophertunnel](https://github.com/Sandertv/gophertunnel) |
| NetEase and multi-protocol cross-reference | [EaseCation/SynapseAPI](https://github.com/EaseCation/SynapseAPI) |

Any reference may be outdated or incorrect. When sources conflict, prefer **target-version protocol-docs plus independent implementation or real-byte evidence**. Record the conflict and the basis for the final decision; never silently choose the most convenient source.

## Workflow

### 1. Locate the Current Behavior and Blast Radius

1. Search `src/main/java/cn/nukkit/network/protocol/` by packet name, ID, and related functionality. Determine whether the task is a new packet, a field change, missing registration, or missing business handling.
2. Read implementations for the same packet, the same protocol era, and adjacent protocol boundaries. Reuse existing types and `BinaryStream` helpers.
3. Inspect these dispatch points instead of changing only the packet class:
   - `src/main/java/cn/nukkit/network/protocol/ProtocolInfo.java`
   - `src/main/java/cn/nukkit/network/Network.java`
   - `src/main/java/cn/nukkit/network/process/DataPacketManager.java`
   - `handleDataPacket` in `src/main/java/cn/nukkit/Player.java`, only when the legacy path is involved
4. For version changes, compare the documentation or schemas immediately before and after the target version. List added, removed, moved, and retyped fields. If no exact-version evidence exists, mark the boundary as an assumption and continue looking for independent evidence.

### 2. Write Down the Wire Format First

Before editing, create a short field table containing at least:

```text
Index | Documented field | Wire type | optional/repeat/enum/constraints | MOT reader/writer | Effective protocol range
```

Apply these rules to every field:

- The order of the `fields` array is the packet-body order. The top-level `id` belongs to the packet header and is not a body field.
- Recursively resolve every non-primitive `type` through `types/*.json`. For every `enum`, check the explicit values in `enums/*.json`; never infer wire values from Java enum declaration order.
- `optional: true` usually means a presence marker followed by the value, but context-dependent fields, Cereal types, and tagged unions require confirmation against a target-version reference implementation.
- `repeat` usually specifies the wire type of the element-count prefix. Do not mechanically turn bit sets, tagged lists, or other special encodings into an ordinary Java `List`.
- Treat `constraints` as wire-format and validity evidence. For client input, reuse bounded reads such as `getUnsignedVarInt(max, field)` and bounded `getArray`; do not merely assert constraints in tests.
- Separate field semantics from wire types. For example, a documentation rename to `Actor*` does not require renaming an existing MOT `Entity*` API.

### 3. Implement the Packet, Registration, and Handling

#### Packet Class and ID

- Extend `DataPacket` and follow the style of neighboring classes. Do not force one template for public fields, `NETWORK_ID`, `@ToString`, or other conventions across all protocol eras.
- Use the existing `pid()` convention for legacy `byte` IDs. For modern IDs, especially IDs `> 255`, use an `int NETWORK_ID`, override `packetId()`, and follow the modern-packet convention of throwing `UnsupportedOperationException` from `pid()`. **Never cast a modern ID to `byte`.**
- Call `reset()` at the start of `encode()`. `DataPacket.reset()` writes the packet header; `decode()` consumes only the body and must not read the header again.
- When constructing packets directly in tests, set both `protocol` and `gameVersion`. Do not rely on the `DataPacket` default to the latest version, or version-branch tests will produce false results.
- Use `ProtocolInfo` constants for version checks. Use `gameVersion.isNetEase()` or `GameVersion` when NetEase must be distinguished; never compare only the raw protocol number in that case.

#### Registration and Network Flow

- Register in the correct pool and version section of `Network.registerPackets()`. Check inheritance and deregistration across `packetPool113`, `packetPool137`, `packetPoolCurrent`, and the NetEase pools so every target version resolves to the correct class.
- When a modern serverbound packet needs business handling, implement `DataPacketProcessor<T>` with `handle`, `getPacketId`, `getPacketClass`, and, when needed, `isSupported(GameVersion)`. Register it at the appropriate protocol threshold in `DataPacketManager.registerDefaultProcessors()`.
- Processors must be thread-safe. Prefer the existing stateless `INSTANCE` pattern, and follow the main-thread requirement for game-state changes.
- Clientbound-only packets do not need an inbound processor. Do not add modern packets to the legacy switch in `Player`; modify that switch only when the task actually concerns the legacy dispatch path.
- Annotate NetEase-only public APIs with `@OnlyNetEase` according to repository policy. The annotation does not replace runtime `GameVersion` checks.

### 4. Verify the Wire Format and Real Network Path

The repository already has protocol tests. Add or update tests that cover the change:

- Prefer cross-validation in `src/test/java/cn/nukkit/network/protocol/regression/`:
  - `crossDecode`: MOT encodes, CloudburstMC decodes, and the buffer must be fully consumed.
  - `crossEncode`: CloudburstMC encodes, MOT decodes, and every field is asserted.
- If the reference codec is missing or demonstrably outdated, write independent byte-level or golden-vector tests in `src/test/java/cn/nukkit/network/protocol/`. Assert the packet header, field order, presence markers, counts, enum values, and packet end. When useful, append another packet in the same batch to prove that decoding does not consume the next frame.
- Cover the version before the change, the first affected version, the next boundary, and the latest affected version. Cover optional absent/present cases, empty lists, boundary counts, negative and extreme VarInt values, and applicable NetEase branches.
- Verify idempotence across repeated `encode()` calls when packets may be reused. For processor changes, test dispatch and business effects as well as serialization.

Run focused tests first, then the relevant regression suite:

```bash
mvn test -Dtest='<RelevantTestClass>'
mvn test
```

Alternatively, use the corresponding Gradle tasks: `./gradlew test --tests '<RelevantTestClass>'`, followed by `./gradlew test`. Never use a test-skipping profile as a substitute for verification.

Finally, exercise the real send/receive path for the task scenario using a client, proxy, or existing integration test. Check the login phase, processor dispatch, and subsequent packets. If a real client cannot be run, state exactly what remains unverified; do not describe unit tests as real-client verification.

## Debugging Misalignment and Disconnects

1. Record the protocol number, standard/NetEase branch, direction, packet ID, packet length, and offset of the failing field.
2. Decode in field-table order and find the first offset divergence. Do not guess the root cause from the exception name alone.
3. Compare target-version documentation and an independent implementation. Inspect the nearest preceding optional field, repeated field, enum discriminator, or version gate.
4. Reproduce with a real frame or an independent reference frame, then fix the issue and retain a regression test.

Common symptoms:

| Symptom | Check first |
|---|---|
| Trailing bytes or corruption of the next packet | Missing or extra fields, optional markers, repeat counts |
| Garbage values after a particular field | Endianness, VarInt signedness, 32-bit versus 64-bit width |
| Failure for only one client version | Version gates, pool inheritance, incorrect preview-version mapping |
| Standard clients work but NetEase fails | Protocol-only comparisons, shared pool/processor contamination, NetEase differences |
| MOT-to-MOT round-trip passes but the client disconnects | Both sides share the same mistake; add cross-codec or real-byte tests |
| A modern packet is ignored or has an invalid ID | Byte truncation, incorrect `pid()`, missing registration, or accidental legacy-switch dispatch |

Do not hide misalignment by swallowing exceptions, skipping remaining bytes, or unconditionally relaxing length limits.

## Completion Criteria and Reporting

Before declaring completion, confirm that:

- Evidence supports the target versions, field order, types, optional/repeat behavior, and enum values.
- The packet ID, pool, inbound processor when required, and version gates are complete.
- Focused tests and relevant regressions pass, with no unexplained unread bytes after decoding.
- New and updated comments and documentation no longer describe the old wire format. If the change alters the protocol architecture documented by the repository, update `AGENTS.md` accordingly.
- `git status` has been checked, and temporary test artifacts not required for delivery have been removed.

In the final report, briefly state the protocol-docs ref and commit used, target protocols and gates, changed packets and processors, test commands and results, and any real-client or version-specific verification that remains outstanding.
