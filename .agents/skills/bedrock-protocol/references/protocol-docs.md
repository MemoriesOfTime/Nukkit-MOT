# protocol-docs Reference

Upstream: <https://github.com/EndstoneMC/protocol-docs>. The documentation is extracted from BDS and contains self-contained JSON files under `packets/`, `types/`, and `enums/`. It is useful for confirming packet IDs, field order, and field-level types for a specific version, but it is not a complete multi-version Java codec.

## 1. Sync Automatically and Match the Version

Run the sync script supplied with this skill from the repository root:

```bash
# Upstream default branch
python3 .agents/skills/bedrock-protocol/scripts/sync_protocol_docs.py

# Specific branch, tag, or commit
python3 .agents/skills/bedrock-protocol/scripts/sync_protocol_docs.py --ref <ref>
```

The script requires Python 3.7+ and Git. It writes a shallow fetch to `.agents/skills/bedrock-protocol/.cache/protocol-docs/`. The cache is Git-ignored; do not edit it manually. If the script detects local changes, it stops instead of overwriting them.

1. If the target ref is unknown, inspect upstream branches with `git ls-remote --heads https://github.com/EndstoneMC/protocol-docs.git`, then select `--ref`. Never use the latest version by default to describe an older protocol.
2. After syncing, Read the cached `README.md` and inspect the script output. Confirm `Current Release`, `Minecraft Version`, and `Network Version`, and record the actual commit.
3. Use read-only Git commands to inspect target files. Do not switch or clean another worktree merely to read reference material:

```bash
DOCS_DIR=.agents/skills/bedrock-protocol/.cache/protocol-docs
git -C "$DOCS_DIR" show HEAD:README.md
git -C "$DOCS_DIR" show HEAD:packets/AddActorPacket.json
git -C "$DOCS_DIR" log -1 --format='%H %cs %s' HEAD
```

4. If no branch exactly matches the target protocol, explicitly state the discrepancy when using the closest evidence; do not describe the target protocol as confirmed.
5. If the Git repository cannot be synced, read the upstream raw files for the appropriate ref directly:

```text
https://raw.githubusercontent.com/EndstoneMC/protocol-docs/<ref>/packets/<PacketName>.json
https://raw.githubusercontent.com/EndstoneMC/protocol-docs/<ref>/types/<TypeName>.json
https://raw.githubusercontent.com/EndstoneMC/protocol-docs/<ref>/enums/<EnumName>.json
```

The upstream default branch changes as stable and preview versions are released, and an existing cache can become stale. Check the target Network Version and cache commit for every protocol task.

## 2. JSON Structure and Expansion Rules

A typical packet definition looks like this:

```json
{
  "id": 312,
  "name": "ServerboundLoadingScreenPacket",
  "notes": "...",
  "fields": [
    {
      "name": "Loading Screen Packet Type",
      "type": "varint32",
      "enum": "ServerboundLoadingScreenPacketType",
      "constraints": {"enum_values": [2, 1]}
    },
    {
      "name": "Loading Screen Id",
      "type": "uint32",
      "optional": true
    }
  ]
}
```

- `id`: The network packet ID. Check it against MOT constants, but never infer a new version's ID from an older decimal ID.
- `name` and `notes`: Naming and semantic hints; they do not change the byte layout.
- `fields`: Read and write the packet body strictly in array order.
- `type`: A primitive or another type name. For the latter, recursively read `types/<type>.json` until reaching primitives. Do not treat a structure name as an existing Java class name and copy it directly.
- `enum`: Read `values[].name` and `values[].value` from the corresponding `enums/*.json`. Namespace separators `::` usually become `__` in filenames; for example, `PlayerAuthInputPacketPayload::InputData` maps to `PlayerAuthInputPacketPayload__InputData.json`. If the file is not found, use Glob with the simple name instead of guessing a path.
- `repeat`: Usually the wire type of the count prefix, such as `"repeat": "uvarint32"`. Write the count first, followed by each element in order. It can also appear in bit sets or specially encoded fields, so confirm the behavior against an independent implementation.
- `optional: true`: Usually controlled by a boolean presence prefix. Some fields are present according to a preceding enum, protocol version, or Cereal encoding; do not mechanically add a boolean merely because a field is optional.
- `constraints`: Examples include `max_items` and `enum_values`. Preserve their original meaning. Inbound handling should use the repository's existing bounded-read and validation patterns; do not leave constraints only in comments.

Do not read entire `packets/`, `types/`, or `enums/` directories at once. Read only the target JSON files and their recursive dependencies.

## 3. Common Wire-Type to MOT Mappings

This table is a starting point, not a shortcut around type definitions. Expand the protocol-docs type first, then confirm that the MOT helper implementation matches the target version.

| protocol-docs type | Common MOT `BinaryStream` / `DataPacket` methods | Notes |
|---|---|---|
| `bool` | `getBoolean` / `putBoolean` | Also commonly used for one-byte presence markers |
| `int8`, `uint8` | `getByte` / `putByte` | Java bytes are signed; convert explicitly when an unsigned numeric range is required |
| `int16`, `uint16` | `getLShort` / `putLShort` | Little-endian; do not mistakenly use big-endian `getShort` |
| `int32`, `uint32` | `getLInt` / `putLInt` | Little-endian; Java code often preserves the uint32 bit pattern |
| `int64`, `uint64` | `getLLong` / `putLLong` | Little-endian |
| `float` | `getLFloat` / `putLFloat` | Little-endian IEEE representation |
| `varint32` | `getVarInt` / `putVarInt` | Signed ZigZag VarInt |
| `varint64` | `getVarLong` / `putVarLong` | 64-bit ZigZag; do not call the 32-bit methods |
| `uvarint32` | `getUnsignedVarInt` / `putUnsignedVarInt` | Not ZigZag; use a bound when reading counts |
| `uvarint64` | `getUnsignedVarLong` / `putUnsignedVarLong` | Not ZigZag |
| `string` | `getString` / `putString` | The helper handles the byte-length prefix and UTF-8 |
| `Vec2` | `getVector2f` / `putVector2f` | Two little-endian floats |
| `Vec3` | `getVector3f` / `putVector3f` | Three little-endian floats |
| `BlockPos` | `getBlockVector3` / `putBlockVector3` | In `DataPacket`, use the overrides that provide `gameVersion` |
| `ActorUniqueID` | `getEntityUniqueId` / `putEntityUniqueId` | The helper handles MOT's legacy-version differences |
| `ActorRuntimeID` | `getEntityRuntimeId` / `putEntityRuntimeId` | Do not assume it is always uvarint64 |
| UUID types | `getUUID` / `putUUID` | Confirm the protocol type and byte order first |
| `optional` values | `getOptional` / `putOptionalNull` | Use only when the reference format confirms boolean presence |
| Lists with `repeat: uvarint32` | `getArray` / `putArray` | Check both the count and the element reader/writer |

Complex types such as NBT, items, actor data, Cereal, tagged unions, and compressed data require dedicated existing helpers and reference implementations. Do not apply the primitive table to them.

## 4. Compare Version Differences

Fetch both refs into temporary cache refs, then run a narrowly scoped diff for the packet and its recursive types:

```bash
DOCS_DIR=.agents/skills/bedrock-protocol/.cache/protocol-docs
git -C "$DOCS_DIR" fetch --depth 1 origin \
  +<old-ref>:refs/protocol-docs/old \
  +<new-ref>:refs/protocol-docs/new
git -C "$DOCS_DIR" diff refs/protocol-docs/old..refs/protocol-docs/new -- \
  packets/AddActorPacket.json \
  types/PassengerOfBlockArguments.json \
  types/ActorUniqueID.json
```

Classify each field change:

- Added or removed: Determine the first affected protocol and check whether the field is at the packet end or inside an existing optional structure.
- Moved: Adjust read and write order for every affected version, not only the latest one.
- Retyped: Change both reader and writer, and check signedness and width.
- Enum changes: Check numeric values; never assume that a Java ordinal equals the wire value.
- New constraints: Add input bounds and invalid-data tests.

Each protocol-docs branch primarily describes one version. When a continuous version range or exact `since/until` boundaries are needed, first cross-check the version-aware schemas in `protocol/*.py` from [EndstoneMC/bedrock-protocol](https://github.com/EndstoneMC/bedrock-protocol), then confirm with CloudburstMC, gophertunnel, or a real packet capture.

## 5. Conflicting Evidence and Test Selection

1. **Exact target-version protocol-docs conflicts with CloudburstMC:** Confirm the branch/commit and the codec's actual protocol number first. If the codec is outdated, write byte-level tests based on protocol-docs and explain why cross-validation is not used in concise bilingual Chinese and English Javadoc, following repository policy.
2. **Adjacent documented versions are identical but a preview changed:** Do not infer behavior from stable version numbers alone. Look for a preview schema, packet capture, or corresponding codec.
3. **No CloudburstMC codec exists:** Use an independent manual frame or golden bytes. Do not add a dependency merely to make testing easier unless the user requests a dependency upgrade.
4. **NetEase differences:** protocol-docs provides only the standard Bedrock baseline. Add evidence from MOT's NetEase branches, netease-protocol-extension, SynapseAPI, or real NetEase frames, and preserve `GameVersion` isolation.

Every test must include an expected source independent of MOT's own `encode()` and `decode()`. A self-consistent round-trip alone cannot detect a field-order or type error shared by both methods.
