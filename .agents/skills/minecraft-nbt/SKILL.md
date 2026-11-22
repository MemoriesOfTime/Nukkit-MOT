---
name: minecraft-nbt
description: Read, inspect, edit, convert and write Minecraft NBT binary files — .dat, .nbt, level.dat, player/entity .dat, .mcstructure, Nukkit-MOT runtime_block_states_*.dat, PMMP canonical_block_states_*.nbt, CloudburstMC/NetEase block_palette_*.nbt — using the bundled zero-dependency Python tool. Auto-detects Java big-endian vs Bedrock little-endian vs Bedrock network VarInt NBT, gzip/zlib/raw compression, Bedrock level.dat headers, ListTag roots and multi-root files; converts to/from SNBT and typed JSON; edits values in place by path. Use whenever the user mentions NBT, SNBT, a .dat or .nbt file, level.dat, block palettes / block states files, or wants to view, diff, fix or modify any Minecraft binary save data — even if they only say "open this dat file", "what's in level.dat", or "change the spawn point". 中文触发：NBT、dat 文件、level.dat、方块调色板、runtime_block_states、读取/解析/修改/转换 NBT。
---

# Minecraft NBT files (.dat / .nbt / level.dat)

NBT is a **binary** format: the Read tool shows garbage and Edit/Write would corrupt
it. Everything goes through the bundled tool, which is pure Python 3.8+ standard
library (no pip install):

```
python3 .agents/skills/minecraft-nbt/scripts/nbt_tool.py <command> ...
```

Run from the repository root (the path above is repo-relative). If the current
directory is elsewhere, resolve the script from this skill's own directory.

## Rules of engagement

1. **Run `info` first, always.** It tells you the detected format (endian, network,
   compression, root kind, level.dat header) and the size/shape of the data. Decide
   the workflow from that output, not from the file extension — `.dat` and `.nbt`
   are used for every variant.
2. **Prefer `get` / `set` / `rm` over full decode for large files.** A 150 KB gzip
   palette expands to multi-megabyte SNBT; reading it into context is a waste. Use
   paths to read or change exactly what is needed. In-place edits re-detect the
   format and write it back unchanged (compression, endian, header, string encoding).
3. **Decode→edit→encode only when many values change.** `decode -o file.snbt`
   writes a `file.snbt.meta.json` sidecar; `encode file.snbt -o out.dat` reads it
   automatically so the output format matches the input. Never drop the sidecar.
4. **Tag types are data.** `1b` (byte), `1s`, `1`, `1L`, `1.0f`, `1.0d` are six
   different values. Nukkit-MOT hashes block states by tag *type*, so turning a byte
   into an int silently breaks client palette lookups. Copy the type you see in `get`
   output; never "normalise" numbers.
5. **Verify after writing**: `verify FILE --roundtrip`, or `get` the value back. For
   raw (uncompressed) files the tool reproduces input bytes exactly; for gzip files
   the decompressed payload is identical (gzip headers may differ).
6. Make a copy before touching a world's `level.dat` or player data; the game must
   not be running while you write.

## Command cheat sheet

```bash
T=.agents/skills/minecraft-nbt/scripts/nbt_tool.py

python3 $T info FILE [--json]                       # detect format + stats + root preview
python3 $T get  FILE PATH [--compact|--format json] # print one subtree
python3 $T set  FILE PATH 'SNBT_VALUE' [--type T]   # in-place edit, format preserved
python3 $T rm   FILE PATH                           # remove key / list element in place
python3 $T decode FILE -o out.snbt [--compact]      # binary → SNBT (+ out.snbt.meta.json)
python3 $T decode FILE -o out.json --format json    # binary → typed JSON (meta inline; multi-root OK)
python3 $T encode out.snbt -o FILE                  # text → binary, format from sidecar/JSON meta
python3 $T encode new.snbt -o FILE --endian little --compression none --level-dat-version 10
python3 $T verify FILE [--roundtrip]                # re-parse; optionally re-encode and compare
python3 $T selftest [--corpus DIR]                  # built-in tests; run after editing the tool
```

Format flags (accepted by every command; they override detection or sidecar):
`--endian big|little`, `--compression none|gzip|zlib`, `--network/--no-network`,
`--nameless-root/--named-root`, `--string-encoding mutf8|utf8`,
`--level-dat-version N` (adds the 8-byte Bedrock header), `--no-level-dat`.

Path syntax: `.` is the root; `.Key.Sub`, `[3]` for list index, `."key with spaces"`.
Examples: `.LevelName`, `.Data.Player.Pos[1]`, `[0].states` (Nukkit dat, list root),
`[0].block.states` (PMMP/NetEase wrapped entries), `.blocks[12].name`.

Values for `set` are SNBT literals: `5b`, `-3s`, `42`, `9L`, `1.5f`, `2.5d`,
`"text"`, `true`/`false` (→ byte), `[B;1b,2b]`, `[I;1,2]`, `[L;1L]`, `[1,2]`,
`{a:1b,b:"x"}`. Use `--type int 5` etc. to coerce a raw string instead.

## Workflows

**Inspect an unknown file**
```bash
python3 $T info world/level.dat
python3 $T get world/level.dat . --compact | head -c 2000     # skim, don't dump
```

**Fix one value (e.g. spawn point, game mode, a block state)**
```bash
python3 $T get  level.dat .SpawnX
python3 $T set  level.dat .SpawnX 256            # int, like the original
python3 $T set  level.dat .LevelName '"My World"'
python3 $T set  palette.dat '[17].states.lit' 1b      # Nukkit runtime_block_states entry
python3 $T rm   level.dat .experiments
python3 $T verify level.dat --roundtrip
```

**Bulk edit (rename many entries, restructure)**
```bash
python3 $T decode in.dat -o work.snbt       # or --format json for machine edits
# edit work.snbt with Edit/sed/python — keep suffixes (b/s/L/f/d) intact
python3 $T encode work.snbt -o out.dat      # picks up work.snbt.meta.json
python3 $T verify out.dat --roundtrip && python3 $T info out.dat
```
Use `--format json` when the edit is programmatic (jq/python) or the file has
several concatenated roots (PMMP `canonical_block_states_*.nbt`): SNBT cannot
represent multi-root files, typed JSON can and re-encodes byte-identically.

**Create a file from scratch**
Write SNBT text, then encode with explicit flags — there is no sidecar yet:
```bash
python3 $T encode new.snbt -o level.dat --level-dat-version 10     # Bedrock level.dat
python3 $T encode new.snbt -o data.dat                             # Java: big-endian gzip (default)
python3 $T encode new.snbt -o out.nbt --network --endian big       # PMMP-style network NBT
```

**Convert between dialects** — decode with detection, encode with new flags:
```bash
python3 $T decode bedrock.nbt -o x.snbt
python3 $T encode x.snbt -o java.dat --endian big --compression gzip --no-network
```
Flags override the sidecar. Bedrock network NBT cannot hold `TAG_Long_Array`.

## Format matrix (what `info` can report)

| params | who writes it |
|---|---|
| big, disk, gzip, named compound | Java Edition level.dat / playerdata / most `.dat`; CloudburstMC & NetEase `block_palette_*.nbt`; Nukkit `vanilla_palette_*.nbt` |
| big, disk, gzip, **named list root** | Nukkit-MOT `runtime_block_states_*.dat` (DataConvert output) |
| big, disk, none | uncompressed Java NBT, `.schematic` payloads |
| little, disk, none (+ **level.dat header**) | Bedrock `level.dat` (8-byte header: LE int version, LE int payload length) |
| little, disk, none | Bedrock `.mcstructure`, LevelDB chunk/actor payloads once extracted |
| **network** (VarInt/ZigZag), none, multi-root | PMMP `canonical_block_states_*.nbt` (thousands of roots), `required_block_states_*.nbt` (list root); Bedrock protocol dumps |
| nameless compound root | MC 1.20.2+ network payloads; rare on disk |

Network NBT only differs by endian in float/double bytes; the tool reports
`little` when there are no floats (Bedrock convention) — DataConvert reading the same
files with `ByteOrder.BIG_ENDIAN` is equivalent, bytes are identical.

Strings: `mutf8` = Java modified UTF-8 (DataInput), `utf8` = Bedrock/Nukkit. Decoding
accepts both; the writer uses the detected/declared one so emoji and NUL round-trip
byte-exactly. ASCII content is identical either way.

## Pitfalls

- `decode` of a palette file is MBs of text. Use `info` + `get` first; decode only to
  a file (`-o`), never to stdout into your context.
- Empty lists are written with element type `TAG_End`; the tool handles it. Lists
  must be homogeneous — `[1b, 2s]` is rejected on purpose.
- `set` cannot append to a list (index must exist). For appends use decode/encode.
- `level.dat`: the header length is recomputed on every write and the version is
  preserved from the original; `verify` confirms `length == size - 8`.
- A gzip Java file that *was* uncompressed stays uncompressed on write (format is
  preserved, unlike the IntelliJ plugin which always gzips big-endian output).
- Not NBT containers: `.mca/.mcr` region files (chunk table + per-chunk zlib NBT) and
  Bedrock `db/` LevelDB. Extract the chunk payload first, then use this tool on it.

## References

- `references/format-reference.md` — byte-level layout of every variant, detection
  order, VarInt/ZigZag details, string encodings, cross-references into the four
  reference implementations (Nukkit-MOT, CloudburstMC/NBT, tth05's IntelliJ plugin,
  ImNbt/MCStructs).
  Read it when detection fails or you must hand-craft a format.
- `references/dataconvert-palette.md` — rules specific to Nukkit-MOT block-palette
  files (runtimeId-as-index invariant, typed state keys, the mandatory
  `CheckBlockStatesCount` gate, version-conditional formats). Read it before editing
  anything under DataConvert / `runtime_block_states_*.dat` / `vanilla_palette_*.nbt`.
  Downloads triggered by its recipes are cached in this skill's Git-ignored
  `.cache/dataconvert/` (pass `-Ddataconvert.cache.dir`), never in DataConvert's
  own `cache/`.
