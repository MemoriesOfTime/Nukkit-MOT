# NBT format reference (as implemented by `scripts/nbt_tool.py`)

Read this when auto-detection fails, when you must hand-craft a specific dialect,
or when a result looks wrong and you need to reason at the byte level.

## 1. Tag ids and payloads

| id | name | payload (disk) | payload (network) |
|---|---|---|---|
| 0 | End | – | – |
| 1 | Byte | i8 | i8 |
| 2 | Short | i16 (endian) | i16 (endian) |
| 3 | Int | i32 (endian) | **ZigZag VarInt** (≤5 bytes) |
| 4 | Long | i64 (endian) | **ZigZag VarLong** (≤10 bytes) |
| 5 | Float | f32 (endian) | f32 (endian) |
| 6 | Double | f64 (endian) | f64 (endian) |
| 7 | Byte_Array | len:Int, bytes | len:**ZigZag VarInt**, bytes |
| 8 | String | len:u16 (endian), bytes | len:**unsigned VarInt**, bytes |
| 9 | List | elemType:u8, len:Int, payloads | elemType:u8, len:ZigZag VarInt, payloads |
| 10 | Compound | (type:u8, name:String, payload)* , 0x00 | same |
| 11 | Int_Array | len:Int, Int* | len:ZigZag VarInt, ZigZag VarInt* |
| 12 | Long_Array | len:Int, Long* | **not allowed** (Bedrock has no long arrays) |

"Int" in the length columns means *whatever Int means in that dialect*: the
network reader/writer substitutes ZigZag VarInt everywhere an Int is read, including
array lengths and array elements. This matches Nukkit-MOT `NBTInputStream.readInt()`
(`cn/nukkit/nbt/stream/NBTInputStream.java` lines 133–137, 225–235) and
`NetworkDataInputStream` in tth05's minecraft-nbt-intellij-plugin
([repo](https://github.com/MemoriesOfTime/minecraft-nbt-intellij-plugin); overrides
`readInt/readLong/readUTF` only).

**Known discrepancy:** MCStructs-bedrock documents *unsigned* VarInt for byte-array
lengths; Nukkit and the IntelliJ plugin use ZigZag (`readInt`), as does CloudburstMC
NBT (`VarInts.readInt`). The tool follows that convention.
Real files rarely contain byte arrays in network NBT (palette files have none); if
one parses with trailing bytes, try the other convention by hand.

ZigZag: `enc = (v << 1) ^ (v >> 31)` (arithmetic shift), `dec = (n >>> 1) ^ -(n & 1)`.
VarInt: little-endian base-128, MSB continuation bit, max 5 bytes for 32-bit, 10 for 64.

## 2. Root forms

* **Named root** (default): `type:u8, name:String, payload`. Root may be *any* type —
  Nukkit `runtime_block_states_*.dat` and PMMP `required_block_states_*.nbt` use a
  **List** root; the name is almost always `""`.
* **Nameless root**: compound body only — no type byte, no name (MC 1.20.2+ network
  style, the IntelliJ plugin's "Nameless Root" checkbox). Only compounds.
* **Multi-root**: several named roots back-to-back until EOF. PMMP
  `canonical_block_states_*.nbt` has 7 000–15 000 roots. Only representable in the
  typed-JSON output (`roots: [...]`), not SNBT.

## 3. Containers / compression

* **gzip** — magic `1F 8B`. Java Edition default for `.dat`; CloudburstMC/NetEase
  palettes; Nukkit dats (written with a parallel gzip, read with plain `GZIPInputStream`).
  The tool writes with `mtime=0`, so output differs from the original gzip header but
  the decompressed payload is byte-identical.
* **zlib** — `78 xx`. Chunk payloads inside `.mca` region files; some third-party
  tools. Validated by actually decompressing.
* **none** — Bedrock disk files, network NBT, PMMP data.
* **Bedrock level.dat header** — 8 bytes before an uncompressed LE payload:
  `version:i32 LE, length:i32 LE` where `length == filesize - 8`. Version is 8/9/10
  depending on game version; the tool preserves it and recomputes `length` on write
  (the IntelliJ plugin's `NBTFileUtil.java` lines 93–97 does the same; ImNbt's `BedrockLevelDatFormat`).

## 4. Endianness

* **big** — Java Edition, CloudburstMC/Nukkit palette files (Nukkit is Bedrock
  software but writes BE NBT for its data files), PMMP network files *as read by
  DataConvert* (`NBTIO1.read(bis, ByteOrder.BIG_ENDIAN, true)`).
* **little** — Bedrock disk NBT (`level.dat`, `.mcstructure`, LevelDB values) and the
  Bedrock network protocol.

In **network** mode only Float/Double bytes depend on endianness. Detection parses
both and keeps the one whose floats look sane (finite, |v| in [1e-12, 1e18] or 0);
with no floats it reports `little`. Both encodings produce identical bytes for such
files, so "DataConvert says BIG_ENDIAN, tool says little" is not a conflict.

## 5. Strings

| mode | length prefix | bytes |
|---|---|---|
| big, disk (Java) | u16 BE | **modified UTF-8**: `C0 80` for U+0000, CESU-8 surrogate pairs for > U+FFFF, no 4-byte sequences (`DataInput.readUTF`) |
| little, disk (Bedrock) | u16 LE | standard UTF-8 |
| network | unsigned VarInt | standard UTF-8 |
| Nukkit (any mode) | as above | standard UTF-8 even for BE (`new String(bytes, UTF_8)`) |

The reader decodes leniently (strict UTF-8 → modified UTF-8 → UTF-8 with lone
surrogates) and records *evidence*: a 4-byte sequence proves `utf8`, a CESU-8 pair or
`C0 80` proves `mutf8`. `detect` uses evidence when present, else the dialect default
(`mutf8` for big/disk, `utf8` otherwise). ASCII-only content is identical under both,
which is why Nukkit BE files report `mutf8` yet round-trip byte-exactly.

## 6. Detection order (`detect()` in the tool)

1. If `len ≥ 12` and `LE i32 @4 == len - 8` → **level.dat**; payload parsed LE named
   (BE fallback). Mirrors the IntelliJ plugin's `NBTFormatDetector.java` lines 38–45.
2. gzip magic → decompress; else zlib magic + successful inflate → zlib; else raw.
3. On the payload, first variant that parses **and consumes every byte** wins:
   `big/disk/named` → `little/disk/named` → `little/network/named` →
   `big/network/named` (float-sanity tie-break between the two network endians) →
   `little/disk/nameless` → `little/network/nameless` → `big/disk/nameless`.
   Named variants loop over concatenated roots to EOF.
4. If nothing consumes exactly, a lenient pass takes the parse that got furthest and
   prints a warning with the trailing-byte count. Treat that output as suspect.

Why exact consumption is reliable: a wrong endian or dialect almost immediately
produces an impossible length (e.g. LE name length `05 00` read as BE is 1280) and the
parse dies on EOF; two dialects fully parsing the same multi-KB file is practically
impossible. The IntelliJ plugin relies on the same property (`NBTFormatDetector.java` 54–88).

## 7. Text formats

**SNBT** (vanilla-compatible, superset):
`{key:value, "quoted key":value}`, `[a, b]`, `[B;1b,2b]`, `[I;1,2]`, `[L;1L]`,
suffixes `b s L f d` (case-insensitive; no suffix + no dot = int, dot/exponent =
double), `true/false` → byte, `Infinity`/`NaN` (+ suffix), strings in `"` or `'` with
`\"` `\\` plus the extension `\n \r \t \uXXXX`. Printer: keys bare when
`[A-Za-z0-9._+-]+` and not number-like, string **values always quoted**, floats as
the shortest decimal that round-trips the IEEE value bit-exactly (`-0.0f` preserved).

**Typed JSON**:
```json
{"meta": {"params": {"endian":"little","network":false,"compression":"none",
                     "string_encoding":"utf8","nameless_root":false,
                     "level_dat":{"version":10}},
          "source": "/abs/path", "roots": [{"name":"","type":"compound"}]},
 "roots": [{"name": "", "tag": {"type":"compound","value":{
      "SpawnX": {"type":"int","value":128},
      "Pos":    {"type":"list","value":[{"type":"double","value":1.5}]},
      "Blob":   {"type":"byte_array","value":[1,-1]}}}}]}
```
Every tag is `{"type","value"}`; non-finite floats are the strings `"NaN"`,
`"Infinity"`, `"-Infinity"`. `encode` accepts this document, a bare tag object, or
SNBT; flags > `--meta` > JSON meta > `<input>.meta.json` sidecar > defaults
(big/gzip/mutf8; `--network` alone implies little/none/utf8).

## 8. Reference implementations

* Nukkit-MOT NBT (what DataConvert uses): this repo, `cn/nukkit/nbt/stream/NBTInputStream.java`, `NBTOutputStream.java`, `cn/nukkit/nbt/NBTIO.java`. DataConvert ([repo](https://github.com/MemoriesOfTime/DataConvert)) wrapper for List roots: `src/main/java/cn/lanink/dataconvert/utils/NBTIO1.java`; 4-way gzip×endian probe in `utils/Utils.java` lines 126–141; multi-root loop lines 186–215.
* CloudburstMC NBT (Apache-2.0 library the protocol code in this repo uses — `BinaryStream`, `StartGamePacket`): [CloudburstMC/NBT](https://github.com/CloudburstMC/NBT) — `org/cloudburstmc/nbt/{NbtUtils,NBTInputStream,NBTOutputStream}.java`, `util/VarInts.java`; network dialect in `util/stream/{NetworkDataInputStream,NetworkDataOutputStream}.java` (overrides `readInt`/`readLong`/`readUTF` only), little-endian disk in `util/stream/LittleEndianData*.java`.
* tth05's minecraft-nbt-intellij-plugin (pure-JDK reader/writer, SNBT parser, format detector): [repo](https://github.com/MemoriesOfTime/minecraft-nbt-intellij-plugin) — `src/main/java/com/github/tth05/minecraftnbtintellijplugin/util/{NBTFileUtil,NBTFormatDetector,VarInts,LittleEndianDataInputStream,NetworkDataInputStream}.java`, `snbt/{SNbtParser,SNbtSerializer}.java`.
* ImNbt / MCStructs (format axes model, brute-force detector, SNBT versions): [Lenni0451/ImNbt](https://github.com/Lenni0451/ImNbt) — `src/main/java/net/lenni0451/imnbt/types/{EndianType,CompressionType,FormatType,CustomFormatType}.java`, `types/formats/BedrockLevelDatFormat.java`, `utils/nbt/{NbtReader,detector/BasicDetector,detector/BruteForceDetector}.java`. Library + GUI app, no CLI.

## 9. Limits

* No `.mca/.mcr` region container parsing, no LevelDB access, no `.schematic`/
  `.litematic` container logic — extract the NBT blob first.
* Whole file in memory; 1 GiB decompressed guard; nesting depth guard 1024.
* `set` replaces existing paths only (no list append); use decode/edit/encode.
* Network byte-array length convention: see §1 discrepancy.
