#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
nbt_tool.py — dependency-free Minecraft NBT / .dat reader, writer and editor.

Pure Python standard library (3.8+). Covers the full format matrix found in
Java Edition, Bedrock Edition, PMMP, CloudburstMC, NetEase and Nukkit-MOT
files:

  endian      big | little          (Java disk = big, Bedrock disk = little)
  network     false | true          (Bedrock network NBT: zigzag VarInt/VarLong
                                     scalars, element counts and array lengths;
                                     unsigned-varint string lengths. PMMP
                                     canonical files are network=true + big endian)
  compression gzip | zlib | none
  container   plain | Bedrock level.dat 8-byte header (LE version + LE length)
  root        named (any tag type, incl. the ListTag roots used by Nukkit
              palette .dat files) | nameless compound (MC 1.20.2+ network style)
  multi-root  sequential concatenated named roots (PMMP canonical_block_states)
  strings     modified UTF-8 (Java) | standard UTF-8 (Bedrock/Nukkit); decoding
              is lenient and accepts both, encoding is chosen by params

Text interchange formats:
  SNBT  (stringified NBT, typed: 1b 2s 3 4L 1.5f 2.5d [B;..] [I;..] [L;..])
  JSON  (typed: {"type": "int", "value": 3} ... under a "meta"+"roots" wrapper)

Semantics cross-checked against Nukkit-MOT NBTInputStream/NBTOutputStream,
tth05's minecraft-nbt-intellij-plugin
(https://github.com/MemoriesOfTime/minecraft-nbt-intellij-plugin) and Lenni0451's
ImNbt/MCStructs, and verified on the real corpus shipped with the DataConvert
project.

Usage: nbt_tool.py <info|decode|encode|get|set|rm|verify|selftest> ...
Run `nbt_tool.py selftest [--corpus DIR]` after modifying this file.
"""

import argparse
import gzip
import json
import math
import os
import re
import struct
import sys
import zlib

MAX_DEPTH = 1024
MAX_PAYLOAD = 1 << 30  # 1 GiB decompression guard

# ---------------------------------------------------------------------------
# Tag model
# ---------------------------------------------------------------------------

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

TYPE_NAME = {
    TAG_BYTE: "byte", TAG_SHORT: "short", TAG_INT: "int", TAG_LONG: "long",
    TAG_FLOAT: "float", TAG_DOUBLE: "double", TAG_BYTE_ARRAY: "byte_array",
    TAG_STRING: "string", TAG_LIST: "list", TAG_COMPOUND: "compound",
    TAG_INT_ARRAY: "int_array", TAG_LONG_ARRAY: "long_array",
}
NAME_TYPE = {v: k for k, v in TYPE_NAME.items()}
NAME_TYPE["end"] = TAG_END

INT_RANGE = {
    TAG_BYTE: (-128, 127),
    TAG_SHORT: (-32768, 32767),
    TAG_INT: (-2147483648, 2147483647),
    TAG_LONG: (-(1 << 63), (1 << 63) - 1),
}


class NBTErr(Exception):
    pass


class Tag(object):
    """Uniform tag wrapper: t = type name str, v = value.

    v: int (byte/short/int/long), float (float/double), str,
    list[int] (byte/int/long arrays), list[Tag] (list), dict[str, Tag] (compound).
    """

    __slots__ = ("t", "v")

    def __init__(self, t, v):
        self.t = t
        self.v = v

    def __repr__(self):
        return "Tag(%s, %r)" % (self.t, self.v)

    def type_id(self):
        return NAME_TYPE[self.t]


def check_int(tid, value, what="value"):
    lo, hi = INT_RANGE[tid]
    if not (lo <= value <= hi):
        raise NBTErr("%s %d out of range for %s" % (what, value, TYPE_NAME[tid]))


def tag_equal(a, b):
    if a.t != b.t:
        return False
    if a.t in ("float", "double"):
        fmt = "<f" if a.t == "float" else "<d"
        return struct.pack(fmt, a.v) == struct.pack(fmt, b.v)
    if a.t == "compound":
        if set(a.v.keys()) != set(b.v.keys()):
            return False
        return all(tag_equal(a.v[k], b.v[k]) for k in a.v)
    if a.t == "list":
        return len(a.v) == len(b.v) and all(tag_equal(x, y) for x, y in zip(a.v, b.v))
    return a.v == b.v


def tag_stats(tag, counts=None):
    counts = counts if counts is not None else {}
    counts[tag.t] = counts.get(tag.t, 0) + 1
    if tag.t == "compound":
        for c in tag.v.values():
            tag_stats(c, counts)
    elif tag.t == "list":
        for c in tag.v:
            tag_stats(c, counts)
    return counts


def tag_depth(tag):
    if tag.t == "compound":
        return 1 + max([tag_depth(c) for c in tag.v.values()] or [0])
    if tag.t == "list":
        return 1 + max([tag_depth(c) for c in tag.v] or [0])
    return 1


# ---------------------------------------------------------------------------
# Modified UTF-8 (Java DataInput/DataOutput string encoding)
# ---------------------------------------------------------------------------

def mutf8_decode(data):
    """Decode Java modified UTF-8 (CESU-8 + C0 80 NUL). Tolerates lone surrogates."""
    out = []
    i, n = 0, len(data)
    while i < n:
        c = data[i]
        if c < 0x80:
            out.append(chr(c))
            i += 1
        elif 0xC0 <= c <= 0xDF and i + 1 < n and 0x80 <= data[i + 1] <= 0xBF:
            out.append(chr(((c & 0x1F) << 6) | (data[i + 1] & 0x3F)))
            i += 2
        elif 0xE0 <= c <= 0xEF and i + 2 < n and 0x80 <= data[i + 1] <= 0xBF \
                and 0x80 <= data[i + 2] <= 0xBF:
            out.append(chr(((c & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6)
                           | (data[i + 2] & 0x3F)))
            i += 3
        else:
            raise NBTErr("invalid modified UTF-8 byte 0x%02X at offset %d" % (c, i))
    # combine CESU-8 surrogate pairs into real code points
    res = []
    j = 0
    while j < len(out):
        cp = ord(out[j])
        if 0xD800 <= cp <= 0xDBFF and j + 1 < len(out):
            lo = ord(out[j + 1])
            if 0xDC00 <= lo <= 0xDFFF:
                res.append(chr(0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00)))
                j += 2
                continue
        res.append(out[j])
        j += 1
    return "".join(res)


def mutf8_encode(s):
    out = bytearray()
    for ch in s:
        cp = ord(ch)
        if 1 <= cp <= 0x7F:
            out.append(cp)
        elif cp == 0:
            out += b"\xc0\x80"
        elif cp <= 0x7FF:
            out.append(0xC0 | (cp >> 6))
            out.append(0x80 | (cp & 0x3F))
        elif cp <= 0xFFFF:  # includes lone & paired surrogates (CESU-8)
            out.append(0xE0 | (cp >> 12))
            out.append(0x80 | ((cp >> 6) & 0x3F))
            out.append(0x80 | (cp & 0x3F))
        else:
            cp -= 0x10000
            for x in (0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF)):
                out.append(0xE0 | (x >> 12))
                out.append(0x80 | ((x >> 6) & 0x3F))
                out.append(0x80 | (x & 0x3F))
    return bytes(out)


def decode_string(data):
    """Lenient decode: strict UTF-8, then modified UTF-8, then surrogatepass."""
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        pass
    try:
        return mutf8_decode(data)
    except NBTErr:
        pass
    return data.decode("utf-8", "surrogatepass")


def encode_string(s, encoding):
    if encoding == "mutf8":
        return mutf8_encode(s)
    try:
        return s.encode("utf-8")
    except UnicodeEncodeError:
        return s.encode("utf-8", "surrogatepass")


# ---------------------------------------------------------------------------
# Binary reader / writer
# ---------------------------------------------------------------------------

class Reader(object):
    def __init__(self, data, endian="big", network=False, string_encoding=None):
        self.d = data
        self.p = 0
        self.e = "<" if endian == "little" else ">"
        self.endian = endian
        self.net = network
        self.se = string_encoding or ("utf8" if (network or endian == "little") else "mutf8")
        self.ev_utf8 = 0
        self.ev_mutf8 = 0

    def string_encoding_evidence(self):
        if self.ev_mutf8 and not self.ev_utf8:
            return "mutf8"
        if self.ev_utf8 and not self.ev_mutf8:
            return "utf8"
        return None

    def take(self, n):
        if n < 0 or self.p + n > len(self.d):
            raise NBTErr("unexpected end of data (want %d bytes at offset %d, have %d)"
                         % (n, self.p, len(self.d) - self.p))
        b = self.d[self.p:self.p + n]
        self.p += n
        return b

    def remaining(self):
        return len(self.d) - self.p

    def u8(self):
        return self.take(1)[0]

    def i8(self):
        v = self.u8()
        return v - 256 if v > 127 else v

    def i16(self):
        return struct.unpack(self.e + "h", self.take(2))[0]

    def u16(self):
        return struct.unpack(self.e + "H", self.take(2))[0]

    def i32(self):
        return struct.unpack(self.e + "i", self.take(4))[0]

    def i64(self):
        return struct.unpack(self.e + "q", self.take(8))[0]

    def f32(self):
        return struct.unpack(self.e + "f", self.take(4))[0]

    def f64(self):
        return struct.unpack(self.e + "d", self.take(8))[0]

    def uvarint(self, max_bytes, bits):
        result = 0
        for i in range(max_bytes):
            b = self.u8()
            result |= (b & 0x7F) << (7 * i)
            if not (b & 0x80):
                if result >> bits:
                    raise NBTErr("varint exceeds %d bits" % bits)
                return result
        raise NBTErr("varint too long (%d bytes)" % max_bytes)

    def zigzag32(self):
        n = self.uvarint(5, 32)
        return (n >> 1) ^ -(n & 1)

    def zigzag64(self):
        n = self.uvarint(10, 64)
        return (n >> 1) ^ -(n & 1)

    # unified scalar accessors ------------------------------------------------
    def read_int(self):
        return self.zigzag32() if self.net else self.i32()

    def read_long(self):
        return self.zigzag64() if self.net else self.i64()

    def read_string(self):
        if self.net:
            n = self.uvarint(5, 32)
        else:
            n = self.u16()
        raw = self.take(n)
        # Lenient decode that also records evidence of which encoding wrote the
        # file: 4-byte UTF-8 sequences ⇒ standard UTF-8, CESU-8 pairs / C0 80
        # NUL ⇒ Java modified UTF-8. Lets `detect` reproduce the bytes exactly.
        try:
            s = raw.decode("utf-8")
            if not raw.isascii() and any(b >= 0xF0 for b in raw):
                self.ev_utf8 += 1
            return s
        except UnicodeDecodeError:
            pass
        try:
            s = mutf8_decode(raw)
            self.ev_mutf8 += 1
            return s
        except NBTErr:
            pass
        return raw.decode("utf-8", "surrogatepass")

    # payload ------------------------------------------------------------------
    def read_payload(self, tid, depth=0):
        if depth > MAX_DEPTH:
            raise NBTErr("max nesting depth %d exceeded" % MAX_DEPTH)
        if tid == TAG_BYTE:
            return Tag("byte", self.i8())
        if tid == TAG_SHORT:
            return Tag("short", self.i16())
        if tid == TAG_INT:
            return Tag("int", self.read_int())
        if tid == TAG_LONG:
            return Tag("long", self.read_long())
        if tid == TAG_FLOAT:
            return Tag("float", self.f32())
        if tid == TAG_DOUBLE:
            return Tag("double", self.f64())
        if tid == TAG_BYTE_ARRAY:
            n = self.read_int()
            if n < 0 or self.p + n > len(self.d):
                raise NBTErr("byte_array length %d invalid at offset %d" % (n, self.p))
            raw = self.take(n)
            return Tag("byte_array", [b - 256 if b > 127 else b for b in raw])
        if tid == TAG_STRING:
            return Tag("string", self.read_string())
        if tid == TAG_LIST:
            etype = self.u8()
            n = self.read_int()
            if n < 0:
                raise NBTErr("negative list length %d" % n)
            if etype == TAG_END and n != 0:
                raise NBTErr("TAG_End list element type with length %d" % n)
            if self.net and etype == TAG_LONG_ARRAY:
                raise NBTErr("TAG_Long_Array not allowed in Bedrock network NBT")
            return Tag("list", [self.read_payload(etype, depth + 1) for _ in range(n)])
        if tid == TAG_COMPOUND:
            out = {}
            while True:
                t = self.u8()
                if t == TAG_END:
                    return Tag("compound", out)
                if t not in TYPE_NAME:
                    raise NBTErr("unknown tag id %d in compound at offset %d" % (t, self.p - 1))
                name = self.read_string()
                out[name] = self.read_payload(t, depth + 1)
        if tid == TAG_INT_ARRAY:
            n = self.read_int()
            # cheap sanity bound: each element needs >=1 byte (varint) / 4 bytes (fixed)
            per = 1 if self.net else 4
            if n < 0 or n * per > self.remaining():
                raise NBTErr("int_array length %d invalid at offset %d" % (n, self.p))
            return Tag("int_array", [self.read_int() for _ in range(n)])
        if tid == TAG_LONG_ARRAY:
            n = self.read_int()
            per = 1 if self.net else 8
            if n < 0 or n * per > self.remaining():
                raise NBTErr("long_array length %d invalid at offset %d" % (n, self.p))
            return Tag("long_array", [self.read_long() for _ in range(n)])
        raise NBTErr("unknown tag id %d" % tid)

    def read_named_root(self):
        tid = self.u8()
        if tid not in TYPE_NAME:
            raise NBTErr("invalid root tag id %d" % tid)
        name = self.read_string()
        return name, self.read_payload(tid)

    def read_nameless_root(self):
        return "", self.read_payload(TAG_COMPOUND)


class Writer(object):
    def __init__(self, endian="big", network=False, string_encoding=None):
        self.b = bytearray()
        self.e = "<" if endian == "little" else ">"
        self.net = network
        self.se = string_encoding or ("utf8" if (network or endian == "little") else "mutf8")

    def out_str(self, s):
        raw = encode_string(s, self.se)
        if self.net:
            self.uvarint(len(raw), 5)
        else:
            if len(raw) > 65535:
                raise NBTErr("string longer than 65535 bytes for non-network NBT: %r..." % s[:32])
            self.b += struct.pack(self.e + "H", len(raw))
        self.b += raw

    def uvarint(self, value, max_bytes):
        if value < 0:
            raise NBTErr("cannot varint-encode negative %d" % value)
        while True:
            if value < 0x80:
                self.b.append(value)
                return
            if max_bytes <= 0:
                raise NBTErr("value too large for varint")
            self.b.append((value & 0x7F) | 0x80)
            value >>= 7
            max_bytes -= 1

    def zigzag32(self, v):
        check_int(TAG_INT, v)
        self.uvarint(((v << 1) ^ (v >> 31)) & 0xFFFFFFFF, 5)

    def zigzag64(self, v):
        check_int(TAG_LONG, v)
        self.uvarint(((v << 1) ^ (v >> 63)) & 0xFFFFFFFFFFFFFFFF, 10)

    def write_int(self, v):
        check_int(TAG_INT, v)
        if self.net:
            self.zigzag32(v)
        else:
            self.b += struct.pack(self.e + "i", v)

    def write_long(self, v):
        check_int(TAG_LONG, v)
        if self.net:
            self.zigzag64(v)
        else:
            self.b += struct.pack(self.e + "q", v)

    def write_payload(self, tag):
        t, v = tag.t, tag.v
        if t == "byte":
            check_int(TAG_BYTE, v)
            self.b.append(v & 0xFF)
        elif t == "short":
            check_int(TAG_SHORT, v)
            self.b += struct.pack(self.e + "h", v)
        elif t == "int":
            self.write_int(v)
        elif t == "long":
            self.write_long(v)
        elif t == "float":
            self.b += struct.pack(self.e + "f", v)
        elif t == "double":
            self.b += struct.pack(self.e + "d", v)
        elif t == "byte_array":
            self.write_int(len(v))
            self.b += bytes(x & 0xFF for x in v)
        elif t == "string":
            self.out_str(v)
        elif t == "list":
            if not v:
                self.b.append(0)
                self.write_int(0)
            else:
                etype = v[0].type_id()
                for c in v:
                    if c.type_id() != etype:
                        raise NBTErr("heterogeneous list (%s vs %s); NBT lists must be homogeneous"
                                     % (v[0].t, c.t))
                    if self.net and c.t == "long_array":
                        raise NBTErr("TAG_Long_Array not allowed in Bedrock network NBT")
                self.b.append(etype)
                self.write_int(len(v))
                for c in v:
                    self.write_payload(c)
        elif t == "compound":
            for name, child in v.items():
                self.b.append(child.type_id())
                self.out_str(name)
                self.write_payload(child)
            self.b.append(0)
        elif t == "int_array":
            self.write_int(len(v))
            for x in v:
                self.write_int(x)
        elif t == "long_array":
            self.write_int(len(v))
            for x in v:
                self.write_long(x)
        else:
            raise NBTErr("cannot write tag type %r" % t)

    def write_named_root(self, name, tag):
        self.b.append(tag.type_id())
        self.out_str(name)
        self.write_payload(tag)

    def write_nameless_root(self, tag):
        if tag.t != "compound":
            raise NBTErr("nameless root must be a compound")
        self.write_payload(tag)


# ---------------------------------------------------------------------------
# SNBT
# ---------------------------------------------------------------------------

_UNQUOTED_RE = re.compile(r"[A-Za-z0-9._+-]+\Z")
_NUM_TOKEN_RE = re.compile(
    r"([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)([bBsSlLfFdD]?)\Z")
_SPECIAL_NUM_RE = re.compile(r"([+-]?(?:[Ii]nfinity|[Nn]a[Nn]))([bdf])?\Z")
_ESCAPES_OUT = {"\\": "\\\\", '"': '\\"', "\n": "\\n", "\r": "\\r", "\t": "\\t"}


def f32_str(v):
    """Shortest decimal that round-trips through IEEE binary32 (bit-exact)."""
    bits = struct.pack("<f", v)
    if v != v:
        return "NaN"
    if v == math.inf:
        return "Infinity"
    if v == -math.inf:
        return "-Infinity"
    for p in range(1, 18):
        s = format(v, ".%dg" % p)
        try:
            if struct.pack("<f", float(s)) == bits:
                return s
        except (OverflowError, ValueError):
            pass
    return repr(v)


def f64_str(v):
    if v != v:
        return "NaN"
    if v == math.inf:
        return "Infinity"
    if v == -math.inf:
        return "-Infinity"
    return repr(v)


def snbt_string(s, quote='"', force=False):
    """Quote/escape a string. Keys stay bare when simple; values are always
    quoted (vanilla behaviour) so `1.21.120` or `true` can't be misread."""
    if not force and _UNQUOTED_RE.match(s) and not _NUM_TOKEN_RE.match(s) \
            and not _SPECIAL_NUM_RE.match(s) and s not in ("true", "false"):
        return s
    out = [quote]
    for ch in s:
        if ch == quote or ch == "\\":
            out.append("\\" + ch)
        elif ch in _ESCAPES_OUT:
            out.append(_ESCAPES_OUT[ch])
        elif ord(ch) < 0x20 or ch == "\x7f":
            out.append("\\u%04x" % ord(ch))
        else:
            out.append(ch)
    out.append(quote)
    return "".join(out)


def to_snbt(tag, pretty=False, level=0):
    t, v = tag.t, tag.v
    pad = "  " * (level + 1) if pretty else ""
    end_pad = "  " * level if pretty else ""
    nl = "\n" if pretty else ""
    sep = (",\n" + pad) if pretty else ", "
    if t == "byte":
        return "%db" % v
    if t == "short":
        return "%ds" % v
    if t == "int":
        return str(v)
    if t == "long":
        return "%dL" % v
    if t == "float":
        return f32_str(v) + "f"
    if t == "double":
        return f64_str(v) + "d"
    if t == "string":
        return snbt_string(v, force=True)
    if t == "byte_array":
        return "[B;" + ",".join(str(x) + "b" for x in v) + "]"
    if t == "int_array":
        return "[I;" + ",".join(str(x) for x in v) + "]"
    if t == "long_array":
        return "[L;" + ",".join(str(x) + "L" for x in v) + "]"
    if t == "list":
        if not v:
            return "[]"
        items = [to_snbt(c, pretty, level + 1) for c in v]
        if pretty:
            return "[" + nl + pad + sep.join(items) + nl + end_pad + "]"
        return "[" + sep.join(items) + "]"
    if t == "compound":
        if not v:
            return "{}"
        items = [snbt_string(k) + ":" + to_snbt(c, pretty, level + 1)
                 for k, c in v.items()]
        if pretty:
            return "{" + nl + pad + sep.join(items) + nl + end_pad + "}"
        return "{" + sep.join(items) + "}"
    raise NBTErr("cannot serialize %r" % t)


class SNBTParser(object):
    """Recursive-descent SNBT parser (vanilla / IntelliJ-plugin compatible)."""

    def __init__(self, text):
        self.s = text
        self.p = 0

    def err(self, msg):
        line = self.s.count("\n", 0, self.p) + 1
        col = self.p - self.s.rfind("\n", 0, self.p)
        raise NBTErr("SNBT parse error at line %d col %d: %s" % (line, col, msg))

    def ws(self):
        while self.p < len(self.s) and self.s[self.p] in " \t\r\n":
            self.p += 1

    def peek(self):
        return self.s[self.p] if self.p < len(self.s) else ""

    def expect(self, ch):
        if self.peek() != ch:
            self.err("expected %r, found %r" % (ch, self.peek()))
        self.p += 1

    def parse_document(self):
        self.ws()
        tag = self.parse_value()
        self.ws()
        if self.p != len(self.s):
            self.err("trailing content after SNBT value")
        return tag

    def parse_value(self):
        c = self.peek()
        if c == "{":
            return self.parse_compound()
        if c == "[":
            return self.parse_list_or_array()
        if c in "\"'":
            return Tag("string", self.parse_quoted())
        if c in "+-." or c.isdigit():
            return Tag(*self.finish_number(self.scan_numberish()))
        # bare word: true/false/Infinity/NaN/plain string
        start = self.p
        while self.p < len(self.s) and self.s[self.p] not in ",]}: \t\r\n":
            self.p += 1
        tok = self.s[start:self.p]
        if not tok:
            self.err("expected value")
        return Tag(*self.finish_number(tok))

    def parse_compound(self):
        self.expect("{")
        out = {}
        self.ws()
        if self.peek() == "}":
            self.p += 1
            return Tag("compound", out)
        while True:
            self.ws()
            if self.peek() in "\"'":
                key = self.parse_quoted()
            else:
                key = self.parse_bare_key()
            self.ws()
            self.expect(":")
            self.ws()
            out[key] = self.parse_value()
            self.ws()
            if self.peek() == ",":
                self.p += 1
                self.ws()
                if self.peek() == "}":  # tolerate trailing comma
                    self.p += 1
                    return Tag("compound", out)
                continue
            if self.peek() == "}":
                self.p += 1
                return Tag("compound", out)
            self.err("expected ',' or '}' in compound")

    def parse_bare_key(self):
        start = self.p
        while self.p < len(self.s) and (self.s[self.p].isalnum() or self.s[self.p] in "._+-"):
            self.p += 1
        if self.p == start:
            self.err("expected key")
        return self.s[start:self.p]

    def parse_quoted(self):
        q = self.s[self.p]
        self.p += 1
        out = []
        while True:
            if self.p >= len(self.s):
                self.err("unterminated string")
            c = self.s[self.p]
            if c == "\\":
                if self.p + 1 >= len(self.s):
                    self.err("dangling backslash")
                e = self.s[self.p + 1]
                if e == "u" and self.p + 6 <= len(self.s) \
                        and re.match(r"[0-9a-fA-F]{4}\Z", self.s[self.p + 2:self.p + 6]):
                    out.append(chr(int(self.s[self.p + 2:self.p + 6], 16)))
                    self.p += 6
                    continue
                # vanilla only escapes the quote char and backslash; \n \r \t
                # are an extension matching what this tool prints
                out.append({"n": "\n", "r": "\r", "t": "\t"}.get(e, e))
                self.p += 2
                continue
            if c == q:
                self.p += 1
                return "".join(out)
            out.append(c)
            self.p += 1

    def parse_list_or_array(self):
        self.expect("[")
        self.ws()
        m = re.match(r"([BbIiLl]);", self.s[self.p:])
        if m:
            self.p += 2
            kind = m.group(1).upper()
            tname = {"B": "byte_array", "I": "int_array", "L": "long_array"}[kind]
            tid = {"B": TAG_BYTE, "I": TAG_INT, "L": TAG_LONG}[kind]
            self.ws()
            if self.peek() == "]":
                self.p += 1
                return Tag(tname, [])
            vals = []
            while True:
                self.ws()
                tok = self.scan_numberish()
                etype, v = self.finish_number(tok)
                if etype != "string":
                    check_int(tid, v, "element of [%s;" % kind)
                    vals.append(v)
                else:
                    # allow suffixed literals like 1b inside [B; — finish_number
                    # already applied the suffix; a bare word here is an error
                    self.err("expected number in typed array, found %r" % tok)
                self.ws()
                if self.peek() == ",":
                    self.p += 1
                    continue
                if self.peek() == "]":
                    self.p += 1
                    return Tag(tname, vals)
                self.err("expected ',' or ']' in typed array")
        if self.peek() == "]":
            self.p += 1
            return Tag("list", [])
        items = [self.parse_value()]
        self.ws()
        while self.peek() == ",":
            self.p += 1
            self.ws()
            if self.peek() == "]":  # tolerate trailing comma
                break
            items.append(self.parse_value())
            self.ws()
        self.expect("]")
        etype = items[0].t
        for it in items:
            if it.t != etype:
                raise NBTErr("heterogeneous SNBT list (%s vs %s)" % (etype, it.t))
        return Tag("list", items)

    def scan_numberish(self):
        """Scan a numeric-ish token: [+-]?digits[.digits][eE[+-]digits] + optional suffix."""
        start = self.p
        if self.peek() in "+-":
            self.p += 1
        m = re.match(r"(?:[Ii]nfinity|[Nn]a[Nn])[fFdD]?", self.s[self.p:])
        if m:
            self.p += m.end()
            return self.s[start:self.p]
        while self.p < len(self.s):
            ch = self.s[self.p]
            if ch.isdigit() or ch == ".":
                self.p += 1
            elif ch in "eE":
                self.p += 1
                if self.p < len(self.s) and self.s[self.p] in "+-":
                    self.p += 1
            else:
                break
        if self.p < len(self.s) and self.s[self.p] in "bBsSlLfFdD" \
                and self.p > start and (self.s[self.p - 1].isdigit() or self.s[self.p - 1] == "."):
            self.p += 1
        return self.s[start:self.p]

    def finish_number(self, tok):
        """Classify a scanned token → (type, value). Bare words become strings."""
        tok = tok.strip()
        if not tok:
            self.err("expected value")
        low = tok.lower()
        if low == "true":
            return ("byte", 1)
        if low == "false":
            return ("byte", 0)
        m = _SPECIAL_NUM_RE.match(tok)
        if m:
            body = m.group(1).lower().lstrip("+-")
            val = math.inf if body == "infinity" else math.nan
            if m.group(1).startswith("-"):
                val = -val
            kind = (m.group(2) or "d").lower()
            return ("float", val) if kind == "f" else ("double", val)
        m = _NUM_TOKEN_RE.match(tok)
        if not m:
            return ("string", tok)
        body, suffix = m.group(1), (m.group(2) or "").lower()
        try:
            if suffix == "b":
                v = int(body, 10)
                check_int(TAG_BYTE, v, tok)
                return ("byte", v)
            if suffix == "s":
                v = int(body, 10)
                check_int(TAG_SHORT, v, tok)
                return ("short", v)
            if suffix == "l":
                v = int(body, 10)
                check_int(TAG_LONG, v, tok)
                return ("long", v)
            if suffix == "f":
                return ("float", float(body))
            if suffix == "d":
                return ("double", float(body))
            if "." in body or "e" in body.lower():
                return ("double", float(body))
            v = int(body, 10)
            check_int(TAG_INT, v, tok)
            return ("int", v)
        except ValueError:
            return ("string", tok)


def parse_snbt(text):
    return SNBTParser(text).parse_document()


# ---------------------------------------------------------------------------
# Typed JSON
# ---------------------------------------------------------------------------

def tag_to_json(tag):
    t, v = tag.t, tag.v
    if t in ("float", "double"):
        if v != v or v in (math.inf, -math.inf):
            return {"type": t, "value": (f64_str if t == "double" else f32_str)(v)}
        return {"type": t, "value": v}
    if t in ("byte_array", "int_array", "long_array"):
        return {"type": t, "value": list(v)}
    if t in ("byte", "short", "int", "long", "string"):
        return {"type": t, "value": v}
    if t == "list":
        return {"type": t, "value": [tag_to_json(c) for c in v]}
    if t == "compound":
        return {"type": t, "value": {k: tag_to_json(c) for k, c in v.items()}}
    raise NBTErr("cannot jsonify %r" % t)


def tag_from_json(obj, where="root"):
    if not isinstance(obj, dict) or "type" not in obj or "value" not in obj:
        raise NBTErr("tag json at %s must be an object with \"type\" and \"value\"" % where)
    t, v = obj["type"], obj["value"]
    if t in ("byte", "short", "int", "long"):
        if isinstance(v, bool) or not isinstance(v, int):
            raise NBTErr("%s at %s needs an integer value" % (t, where))
        check_int(NAME_TYPE[t], v, where)
        return Tag(t, v)
    if t in ("float", "double"):
        if isinstance(v, str):
            s = v.strip().lower().lstrip("+-")
            if s == "nan":
                x = math.nan
            elif s == "infinity":
                x = -math.inf if v.strip().startswith("-") else math.inf
            else:
                x = float(v)
        else:
            x = float(v)
        return Tag(t, x)
    if t == "string":
        if not isinstance(v, str):
            raise NBTErr("string at %s needs a string value" % where)
        return Tag(t, v)
    if t in ("byte_array", "int_array", "long_array"):
        if not isinstance(v, list) or any(isinstance(x, bool) or not isinstance(x, int) for x in v):
            raise NBTErr("%s at %s needs a list of integers" % (t, where))
        etid = {"byte_array": TAG_BYTE, "int_array": TAG_INT, "long_array": TAG_LONG}[t]
        for x in v:
            check_int(etid, x, where)
        return Tag(t, list(v))
    if t == "list":
        if not isinstance(v, list):
            raise NBTErr("list at %s needs an array value" % where)
        items = [tag_from_json(c, "%s[%d]" % (where, i)) for i, c in enumerate(v)]
        if items:
            et = items[0].t
            for it in items:
                if it.t != et:
                    raise NBTErr("heterogeneous list at %s" % where)
        return Tag("list", items)
    if t == "compound":
        if not isinstance(v, dict):
            raise NBTErr("compound at %s needs an object value" % where)
        return Tag("compound", {k: tag_from_json(c, "%s.%s" % (where, k)) for k, c in v.items()})
    raise NBTErr("unknown tag type %r at %s" % (t, where))


# ---------------------------------------------------------------------------
# File-level: params, detection, IO
# ---------------------------------------------------------------------------

DEFAULT_PARAMS = {
    "endian": None,  # None ⇒ derived: little for network (Bedrock protocol), big otherwise
    "compression": None,  # None ⇒ derived: gzip for big-endian disk (Java), none otherwise
    "network": False,
    "string_encoding": None,  # None ⇒ derived: utf8 for network/little, mutf8 for big
    "nameless_root": False,
    "level_dat": None,  # None or {"version": int}
}


def norm_params(p):
    q = dict(DEFAULT_PARAMS)
    q.update({k: v for k, v in p.items() if k in q})
    if q["endian"] not in ("big", "little", None):
        raise NBTErr("endian must be big|little")
    if q["compression"] not in ("none", "gzip", "zlib", None):
        raise NBTErr("compression must be none|gzip|zlib")
    if q["string_encoding"] not in ("mutf8", "utf8", None):
        raise NBTErr("string_encoding must be mutf8|utf8")
    if q["level_dat"] is not None:
        if not isinstance(q["level_dat"], dict) or "version" not in q["level_dat"]:
            raise NBTErr("level_dat must be {\"version\": int}")
        # level.dat container implies LE disk NBT, uncompressed, named root
        q["endian"] = "little"
        q["network"] = False
        q["compression"] = "none"
        q["nameless_root"] = False
    if q["endian"] is None:
        q["endian"] = "little" if q["network"] else "big"
    if q["string_encoding"] is None:
        q["string_encoding"] = "utf8" if (q["network"] or q["endian"] == "little") else "mutf8"
    if q["compression"] is None:
        q["compression"] = "gzip" if (q["endian"] == "big" and not q["network"]) else "none"
    return q


def encode_roots(params, roots):
    """roots: list of (name, Tag). Returns full file bytes incl. compression/header."""
    params = norm_params(params)
    w = Writer(params["endian"], params["network"], params["string_encoding"])
    if params["nameless_root"]:
        if len(roots) != 1:
            raise NBTErr("nameless root files have exactly one root")
        w.write_nameless_root(roots[0][1])
    else:
        for name, tag in roots:
            w.write_named_root(name, tag)
    payload = bytes(w.b)
    comp = params["compression"]
    if comp == "gzip":
        body = gzip.compress(payload, compresslevel=9, mtime=0)
    elif comp == "zlib":
        body = zlib.compress(payload, 9)
    else:
        body = payload
    if params["level_dat"] is not None:
        body = struct.pack("<ii", int(params["level_dat"]["version"]), len(payload)) + body
    return body


def decode_bytes(raw, params):
    """Parse raw file bytes with known params → roots."""
    p = norm_params(params)
    payload = raw
    if p["level_dat"] is not None:
        if len(raw) < 8:
            raise NBTErr("file too short for level.dat header")
        payload = raw[8:]
    comp = p["compression"]
    if comp == "gzip":
        payload = gzip.decompress(payload)
    elif comp == "zlib":
        payload = zlib.decompress(payload)
    if len(payload) > MAX_PAYLOAD:
        raise NBTErr("decompressed payload exceeds 1 GiB guard")
    r = Reader(payload, p["endian"], p["network"], p["string_encoding"])
    roots = []
    if p["nameless_root"]:
        roots = [r.read_nameless_root()]
    else:
        while r.remaining():
            roots.append(r.read_named_root())
    return roots


def try_parse_payload(payload, endian, network, nameless):
    """Full parse; returns (roots, ntags, reader). Raises on any inconsistency."""
    r = Reader(payload, endian, network)
    roots = []
    ntags = 0
    if nameless:
        name, tag = r.read_nameless_root()
        roots.append((name, tag))
        ntags = sum(tag_stats(tag).values())
    else:
        while True:
            name, tag = r.read_named_root()
            roots.append((name, tag))
            ntags += sum(tag_stats(tag).values())
            if r.remaining() == 0:
                break
            if r.remaining() < 2:
                raise NBTErr("trailing garbage (%d bytes)" % r.remaining())
    if r.remaining() != 0:
        raise NBTErr("%d trailing bytes" % r.remaining())
    return roots, ntags, r


# priority: BE disk > LE disk > network (LE is the Bedrock protocol convention;
# BE network is what PMMP canonical files / Nukkit DataConvert assume) > nameless
_DETECT_ORDER = [
    ("big", False, False), ("little", False, False),
    ("little", True, False), ("big", True, False),
    ("little", False, True), ("little", True, True), ("big", False, True),
]

_PARSE_ERRORS = (NBTErr, struct.error, UnicodeDecodeError, MemoryError,
                 RecursionError, OverflowError)


def _collect_floats(tag, out):
    if tag.t in ("float", "double"):
        out.append(tag.v)
    elif tag.t == "compound":
        for c in tag.v.values():
            _collect_floats(c, out)
    elif tag.t == "list":
        for c in tag.v:
            _collect_floats(c, out)


def _float_sanity(roots):
    """(#plausible floats, #floats). In network NBT only float/double bytes
    depend on endianness, so this is the only signal to tell BE from LE."""
    vals = []
    for _, t in roots:
        _collect_floats(t, vals)
    nice = 0
    for v in vals:
        if v == 0 or (math.isfinite(v) and 1e-12 <= abs(v) <= 1e18):
            nice += 1
    return nice, len(vals)


def detect_payload(payload):
    for endian, network, nameless in _DETECT_ORDER:
        try:
            roots, ntags, r = try_parse_payload(payload, endian, network, nameless)
        except _PARSE_ERRORS:
            continue
        params = {"endian": endian, "network": network, "nameless_root": nameless}
        if network:
            # both endians parse identically except float bytes: pick the one
            # whose floats look sane; without floats the choice is immaterial
            other = "big" if endian == "little" else "little"
            try:
                roots_o, _, r_o = try_parse_payload(payload, other, network, nameless)
                if _float_sanity(roots_o)[0] > _float_sanity(roots)[0]:
                    params["endian"] = other
                    roots, r = roots_o, r_o
            except _PARSE_ERRORS:
                pass
        params["string_encoding"] = r.string_encoding_evidence() or \
            ("utf8" if (params["network"] or params["endian"] == "little") else "mutf8")
        return params, roots
    # lenient pass: best partial parse wins
    best = None
    for endian, network, nameless in _DETECT_ORDER:
        r = Reader(payload, endian, network)
        try:
            if nameless:
                roots = [r.read_nameless_root()]
            else:
                roots = [r.read_named_root()]
        except _PARSE_ERRORS:
            continue
        ntags = sum(sum(tag_stats(t).values()) for _, t in roots)
        cand = (r.p, ntags)
        if best is None or cand > best[0]:
            best = (cand, {"endian": endian, "network": network,
                           "nameless_root": nameless}, roots, len(payload) - r.p)
    if best is None:
        raise NBTErr("could not parse data as NBT in any supported variant "
                     "(big/little × network/disk × named/nameless). "
                     "If you know the format, pass --endian/--network/etc. explicitly.")
    _, params, roots, rem = best
    sys.stderr.write("warning: no exact format match; best-effort parse leaves %d "
                     "trailing bytes (endian=%s network=%s nameless=%s)\n"
                     % (rem, params["endian"], params["network"], params["nameless_root"]))
    return params, roots


def detect(raw):
    """Detect full file params from raw bytes → (params, roots)."""
    if len(raw) >= 12:
        length = struct.unpack("<i", raw[4:8])[0]
        if length == len(raw) - 8 and length > 0:
            version = struct.unpack("<i", raw[0:4])[0]
            payload = raw[8:]
            for endian in ("little", "big"):  # level.dat is LE by convention
                try:
                    roots, _, r = try_parse_payload(payload, endian, False, False)
                    return {"endian": endian, "network": False, "nameless_root": False,
                            "compression": "none",
                            "string_encoding": r.string_encoding_evidence() or "utf8",
                            "level_dat": {"version": version}}, roots
                except _PARSE_ERRORS:
                    continue
            raise NBTErr("level.dat header (length field matches) but payload "
                         "unparseable; try explicit --endian/--network flags")
    comp = "none"
    payload = raw
    if raw[:2] == b"\x1f\x8b":
        try:
            payload = gzip.decompress(raw)
            comp = "gzip"
        except OSError:
            pass
    if comp == "none" and raw[:1] == b"\x78" and len(raw) > 2:
        try:
            payload = zlib.decompress(raw)
            comp = "zlib"
        except zlib.error:
            pass
    if len(payload) > MAX_PAYLOAD:
        raise NBTErr("decompressed payload exceeds 1 GiB guard")
    params, roots = detect_payload(payload)
    params["compression"] = comp
    return params, roots


def load_file(path, params=None):
    with open(path, "rb") as f:
        raw = f.read()
    if params:
        p = norm_params(params)
        roots = decode_bytes(raw, p)
        return p, roots
    return detect(raw)


# ---------------------------------------------------------------------------
# NBT path (compound keys / list indices)
# ---------------------------------------------------------------------------

def parse_path(path):
    """'.' → root; '.Foo.Bar[0]' / '."quoted key"' / '.x[3].y' → segment list."""
    segs = []
    i = 0
    n = len(path)
    if path == ".":
        return segs
    while i < n:
        c = path[i]
        if c == ".":
            i += 1
            if i < n and path[i] in "\"'":
                q = path[i]
                i += 1
                buf = []
                while i < n and path[i] != q:
                    if path[i] == "\\" and i + 1 < n:
                        buf.append(path[i + 1])
                        i += 2
                        continue
                    buf.append(path[i])
                    i += 1
                if i >= n:
                    raise NBTErr("unterminated quoted key in path %r" % path)
                i += 1
                segs.append(("key", "".join(buf)))
            else:
                j = i
                while j < n and path[j] not in ".[":
                    j += 1
                if j == i:
                    raise NBTErr("empty path segment in %r" % path)
                segs.append(("key", path[i:j]))
                i = j
        elif c == "[":
            j = path.find("]", i)
            if j < 0:
                raise NBTErr("unterminated [index] in path %r" % path)
            segs.append(("index", int(path[i + 1:j])))
            i = j + 1
        else:
            raise NBTErr("path must start with '.' or '[': %r" % path)
    return segs


def navigate(root_tag, segs):
    cur = root_tag
    for kind, val in segs:
        if kind == "key":
            if cur.t != "compound":
                raise NBTErr("cannot descend into %s with key %r" % (cur.t, val))
            if val not in cur.v:
                raise NBTErr("key %r not found" % val)
            cur = cur.v[val]
        else:
            if cur.t != "list":
                raise NBTErr("cannot index into %s" % cur.t)
            if not (0 <= val < len(cur.v)):
                raise NBTErr("index %d out of range (list has %d elements)" % (val, len(cur.v)))
            cur = cur.v[val]
    return cur


def apply_set(root_tag, segs, value_tag):
    if not segs:
        return value_tag
    parent = navigate(root_tag, segs[:-1])
    kind, val = segs[-1]
    if kind == "key":
        if parent.t != "compound":
            raise NBTErr("parent of %r is %s, need compound" % (val, parent.t))
        parent.v[val] = value_tag
    else:
        if parent.t != "list":
            raise NBTErr("parent of [%d] is %s, need list" % (val, parent.t))
        if not (0 <= val < len(parent.v)):
            raise NBTErr("index %d out of range (list has %d elements); append is not "
                         "supported in-place — use decode/edit/encode" % (val, len(parent.v)))
        if parent.v and parent.v[0].t != value_tag.t:
            raise NBTErr("list element type is %s, refusing to insert %s"
                         % (parent.v[0].t, value_tag.t))
        parent.v[val] = value_tag
    return root_tag


def apply_rm(root_tag, segs):
    if not segs:
        raise NBTErr("refusing to remove the root; use decode/encode to replace the file")
    parent = navigate(root_tag, segs[:-1])
    kind, val = segs[-1]
    if kind == "key":
        if parent.t != "compound" or val not in parent.v:
            raise NBTErr("key %r not found" % val)
        del parent.v[val]
    else:
        if parent.t != "list" or not (0 <= val < len(parent.v)):
            raise NBTErr("index %d not removable" % val)
        parent.v.pop(val)
    return root_tag


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _stats_str(roots):
    counts = {}
    for _, t in roots:
        for k, v in tag_stats(t).items():
            counts[k] = counts.get(k, 0) + v
    parts = " ".join("%s×%d" % (k, v) for k, v in sorted(counts.items()))
    root_counts = {}
    for _, t in roots:
        root_counts[t.t] = root_counts.get(t.t, 0) + 1
    root_types = "/".join("%s×%d" % (k, v) if v > 1 else k
                          for k, v in sorted(root_counts.items()))
    depth = max(tag_depth(t) for _, t in roots)
    return "root=%s, %d root(s), depth≤%d, tags: %s" % (root_types, len(roots), depth, parts)


def _params_str(p):
    s = "endian=%s network=%s compression=%s strings=%s root=%s" % (
        p["endian"], p["network"], p["compression"], p["string_encoding"],
        "nameless" if p["nameless_root"] else "named")
    if p.get("level_dat") is not None:
        s += " level_dat_version=%s" % p["level_dat"].get("version")
    return s


def params_from_args(args, base=None):
    p = dict(base if base is not None else DEFAULT_PARAMS)
    if args.endian:
        p["endian"] = args.endian
    if args.compression:
        p["compression"] = args.compression
    if args.network is not None:
        p["network"] = args.network
    if args.nameless_root is not None:
        p["nameless_root"] = args.nameless_root
    if args.string_encoding:
        p["string_encoding"] = args.string_encoding
    if getattr(args, "level_dat_version", None) is not None:
        p["level_dat"] = {"version": args.level_dat_version}
    elif getattr(args, "no_level_dat", False):
        p["level_dat"] = None
    return p


def any_param_args(args):
    return bool(args.endian or args.compression or args.network is not None
                or args.nameless_root is not None or args.string_encoding
                or getattr(args, "level_dat_version", None) is not None
                or getattr(args, "no_level_dat", False))


def cmd_info(args):
    params, roots = load_file(args.file, params_from_args(args) if any_param_args(args) else None)
    if args.json:
        out = {"file": os.path.abspath(args.file),
               "size": os.path.getsize(args.file),
               "params": dict(params),
               "roots": [{"name": n, "type": t.t} for n, t in roots],
               "stats": {k: v for _, t in roots for k, v in tag_stats(t).items()}}
        print(json.dumps(out, ensure_ascii=False, indent=2))
    else:
        print("file:   %s (%d bytes)" % (os.path.abspath(args.file), os.path.getsize(args.file)))
        print("format: " + _params_str(params))
        print("data:   " + _stats_str(roots))
        for i, (n, t) in enumerate(roots[:3]):
            preview = to_snbt(t, pretty=False)
            if len(preview) > 120:
                preview = preview[:117] + "..."
            print("root[%d]: name=%r %s" % (i, n, preview))
        if len(roots) > 3:
            print("... and %d more roots" % (len(roots) - 3))
    return 0


def cmd_decode(args):
    params, roots = load_file(args.file, params_from_args(args) if any_param_args(args) else None)
    meta = {"params": dict(params), "source": os.path.abspath(args.file),
            "roots": [{"name": n, "type": t.t} for n, t in roots]}
    if args.format == "json":
        doc = {"meta": meta, "roots": [{"name": n, "tag": tag_to_json(t)} for n, t in roots]}
        text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    else:
        if len(roots) > 1:
            raise NBTErr("file has %d concatenated roots; SNBT supports one — "
                         "use --format json" % len(roots))
        text = to_snbt(roots[0][1], pretty=not args.compact) + "\n"
    if args.output and args.output != "-":
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(text)
        print("wrote %s (%d bytes); format: %s" %
              (args.output, len(text.encode("utf-8")), _params_str(params)), file=sys.stderr)
        if args.format == "snbt":
            with open(args.output + ".meta.json", "w", encoding="utf-8") as f:
                json.dump(meta, f, ensure_ascii=False, indent=2)
            print("sidecar: %s.meta.json (used by `encode` to preserve format)" % args.output,
                  file=sys.stderr)
    else:
        sys.stdout.write(text)
    return 0


def _coerce_value(args):
    if args.type:
        raw = args.value
        t = args.type
        if t in ("byte", "short", "int", "long"):
            return Tag(t, int(raw, 0))
        if t in ("float", "double"):
            return Tag(t, float(raw))
        if t == "string":
            return Tag(t, raw)
        if t in ("byte_array", "int_array", "long_array"):
            return Tag(t, [int(x.strip(), 0) for x in raw.split(",") if x.strip()])
        raise NBTErr("unknown --type %r" % t)
    try:
        return parse_snbt(args.value)
    except NBTErr as e:
        raise NBTErr("VALUE is not valid SNBT (%s); or pass --type explicitly" % e)


def _load_text_roots(text):
    """Returns (params_or_None, roots). Accepts typed-JSON doc or SNBT."""
    stripped = text.lstrip()
    if stripped.startswith("{") or stripped.startswith("["):
        try:
            doc = json.loads(text)
            if isinstance(doc, dict) and "meta" in doc and "roots" in doc:
                params = doc["meta"]["params"]
                roots = [(r["name"], tag_from_json(r["tag"])) for r in doc["roots"]]
                return params, roots
            if isinstance(doc, dict) and "type" in doc and "value" in doc:
                return None, [("", tag_from_json(doc))]
            if isinstance(doc, list):
                return None, [("", Tag("list", [tag_from_json(x) for x in doc]))]
            raise ValueError("json without recognized NBT structure")
        except ValueError:
            pass  # fall through to SNBT
    return None, [("", parse_snbt(text))]


def cmd_encode(args):
    with open(args.input, "r", encoding="utf-8") as f:
        text = f.read()
    json_params, roots = _load_text_roots(text)
    # params precedence: explicit flags > --meta file > JSON meta > sidecar > defaults
    base = None
    if args.meta:
        with open(args.meta, "r", encoding="utf-8") as f:
            base = json.load(f)["params"]
    elif json_params is not None:
        base = json_params
    else:
        sidecar = args.input + ".meta.json"
        if os.path.exists(sidecar):
            with open(sidecar, "r", encoding="utf-8") as f:
                base = json.load(f)["params"]
    if base is None and not any_param_args(args):
        sys.stderr.write("warning: no format metadata found and no flags given; using "
                         "Java Edition defaults (big-endian, gzip, named root). "
                         "Pass --endian/--compression/... or --meta to control.\n")
    p = norm_params(params_from_args(args, base))
    if p["level_dat"] is not None and p["level_dat"].get("version") is None:
        raise NBTErr("level_dat params need a version; pass --level-dat-version")
    if p["nameless_root"] and len(roots) != 1:
        raise NBTErr("nameless root needs exactly one root")
    body = encode_roots(p, roots)
    out = args.output
    if not out or out == "-":
        sys.stdout.buffer.write(body)
    else:
        tmp = out + ".tmp"
        with open(tmp, "wb") as f:
            f.write(body)
        os.replace(tmp, out)
        print("wrote %s (%d bytes); format: %s" % (out, len(body), _params_str(p)),
              file=sys.stderr)
    return 0


def _load_for_edit(args):
    forced = params_from_args(args) if any_param_args(args) else None
    params, roots = load_file(args.file, forced)
    if not roots:
        raise NBTErr("file has no roots")
    if len(roots) > 1:
        raise NBTErr("in-place edit not supported for multi-root files (%d roots); "
                     "use decode --format json + encode" % len(roots))
    return params, roots


def _save_after_edit(args, params, roots):
    body = encode_roots(params, roots)
    tmp = args.file + ".tmp"
    with open(tmp, "wb") as f:
        f.write(body)
    os.replace(tmp, args.file)


def cmd_get(args):
    params, roots = _load_for_edit(args)
    tag = navigate(roots[0][1], parse_path(args.path))
    if args.format == "json":
        print(json.dumps(tag_to_json(tag), ensure_ascii=False, indent=2))
    else:
        print(to_snbt(tag, pretty=not args.compact))
    return 0


def cmd_set(args):
    params, roots = _load_for_edit(args)
    value = _coerce_value(args)
    apply_set(roots[0][1], parse_path(args.path), value)
    _save_after_edit(args, params, roots)
    print("set %s = %s" % (args.path, to_snbt(value, pretty=False)[:200]))
    print("file rewritten (%s)" % _params_str(params))
    return 0


def cmd_rm(args):
    params, roots = _load_for_edit(args)
    apply_rm(roots[0][1], parse_path(args.path))
    _save_after_edit(args, params, roots)
    print("removed %s; file rewritten (%s)" % (args.path, _params_str(params)))
    return 0


def cmd_verify(args):
    forced = params_from_args(args) if any_param_args(args) else None
    params, roots = load_file(args.file, forced)
    print("ok: %s" % _params_str(params))
    print("    " + _stats_str(roots))
    if args.roundtrip:
        body = encode_roots(params, roots)
        roots2 = decode_bytes(body, params)
        same = len(roots) == len(roots2) and all(
            tag_equal(a[1], b[1]) for a, b in zip(roots, roots2))
        print("roundtrip: %s (re-encoded %d bytes vs original %d)"
              % ("data identical" if same else "MISMATCH", len(body),
                 os.path.getsize(args.file)))
        if not same:
            return 1
    return 0


def add_param_args(sp):
    sp.add_argument("--endian", choices=["big", "little"])
    sp.add_argument("--compression", choices=["none", "gzip", "zlib"])
    sp.add_argument("--network", dest="network", action="store_true", default=None)
    sp.add_argument("--no-network", dest="network", action="store_false")
    sp.add_argument("--nameless-root", dest="nameless_root", action="store_true", default=None)
    sp.add_argument("--named-root", dest="nameless_root", action="store_false")
    sp.add_argument("--string-encoding", choices=["mutf8", "utf8"])
    sp.add_argument("--level-dat-version", type=int, default=None)
    sp.add_argument("--no-level-dat", dest="no_level_dat", action="store_true")


def build_parser():
    ap = argparse.ArgumentParser(
        prog="nbt_tool.py",
        description="Minecraft NBT/.dat reader, writer, editor (zero dependencies)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sp = sub.add_parser("info", help="detect format and print stats of a binary NBT file")
    sp.add_argument("file")
    sp.add_argument("--json", action="store_true")
    add_param_args(sp)
    sp.set_defaults(func=cmd_info)

    sp = sub.add_parser("decode", help="binary → SNBT (default) or typed JSON")
    sp.add_argument("file")
    sp.add_argument("-o", "--output")
    sp.add_argument("--format", choices=["snbt", "json"], default="snbt")
    sp.add_argument("--compact", action="store_true")
    add_param_args(sp)
    sp.set_defaults(func=cmd_decode)

    sp = sub.add_parser("encode", help="SNBT/typed-JSON text → binary (format from flags/meta/sidecar)")
    sp.add_argument("input")
    sp.add_argument("-o", "--output")
    sp.add_argument("--meta", help="meta json path (as written by decode --format snbt)")
    add_param_args(sp)
    sp.set_defaults(func=cmd_encode)

    sp = sub.add_parser("get", help="print one subtree by path, e.g. '.LevelName' or '.blocks[3].block'")
    sp.add_argument("file")
    sp.add_argument("path")
    sp.add_argument("--format", choices=["snbt", "json"], default="snbt")
    sp.add_argument("--compact", action="store_true")
    add_param_args(sp)
    sp.set_defaults(func=cmd_get)

    sp = sub.add_parser("set", help="in-place edit: set FILE PATH SNBT_VALUE (e.g. '5b', '\"text\"', '{a:1b}')")
    sp.add_argument("file")
    sp.add_argument("path")
    sp.add_argument("value")
    sp.add_argument("--type", choices=sorted(k for k in NAME_TYPE if k != "end"),
                    help="coerce raw string value to this type instead of SNBT")
    add_param_args(sp)
    sp.set_defaults(func=cmd_set)

    sp = sub.add_parser("rm", help="in-place edit: remove a key or list element by path")
    sp.add_argument("file")
    sp.add_argument("path")
    add_param_args(sp)
    sp.set_defaults(func=cmd_rm)

    sp = sub.add_parser("verify", help="re-parse and report consistency; --roundtrip re-encodes")
    sp.add_argument("file")
    sp.add_argument("--roundtrip", action="store_true")
    add_param_args(sp)
    sp.set_defaults(func=cmd_verify)

    sp = sub.add_parser("selftest", help="round-trip tests (synthetic matrix + optional corpus dir)")
    sp.add_argument("--corpus", help="directory of real .dat/.nbt files to verify")
    sp.set_defaults(func=lambda a: selftest(a.corpus))
    return ap


def main(argv=None):
    ap = build_parser()
    args = ap.parse_args(argv)
    try:
        return args.func(args)
    except NBTErr as e:
        sys.stderr.write("error: %s\n" % e)
        return 2
    except (OSError, struct.error, zlib.error, ValueError, KeyError) as e:
        sys.stderr.write("error: %s: %s\n" % (type(e).__name__, e))
        return 2


# ---------------------------------------------------------------------------
# Self-test
# ---------------------------------------------------------------------------

def _sample_tag():
    return Tag("compound", {
        "name": Tag("string", "wörld \"quoted\" \U0001F600"),
        "byte": Tag("byte", -128),
        "short": Tag("short", 32767),
        "int": Tag("int", -2147483648),
        "long": Tag("long", (1 << 63) - 1),
        "float": Tag("float", 0.1),
        "fneg": Tag("float", -0.0),
        "double": Tag("double", 1.7976931348623157e308),
        "bytes": Tag("byte_array", [0, 127, -128, -1]),
        "ints": Tag("int_array", [1, -2, 2147483647]),
        "longs": Tag("long_array", [-(1 << 63), (1 << 63) - 1, 0]),
        "empty_list": Tag("list", []),
        "list": Tag("list", [Tag("compound", {"a": Tag("byte", 1)}),
                             Tag("compound", {"a": Tag("byte", 2)})]),
        "strings": Tag("list", [Tag("string", "a"), Tag("string", "")]),
        "empty_compound": Tag("compound", {}),
        "nul": Tag("string", "a\x00b"),
        "supp": Tag("string", "\U0001F600€漢"),
        "newline": Tag("string", "a\nb"),
    })


def selftest(corpus_dir=None):
    failures = []

    def check(name, cond, detail=""):
        if cond:
            print("  ok  %s" % name)
        else:
            failures.append(name)
            print("FAIL  %s %s" % (name, detail))

    print("== synthetic format matrix ==")
    sample = _sample_tag()
    list_root = Tag("list", [Tag("compound", {"bid": Tag("int", 1), "rtid": Tag("int", 0)}),
                             Tag("compound", {"bid": Tag("int", 2), "rtid": Tag("int", 1)})])
    for endian in ("big", "little"):
        for network in (False, True):
            for comp in ("none", "gzip", "zlib"):
                for nameless in (False, True):
                    label = "%s/%s/%s/%s" % (endian, "net" if network else "disk",
                                             comp, "nameless" if nameless else "named")
                    p = {"endian": endian, "network": network, "compression": comp,
                         "nameless_root": nameless,
                         "string_encoding": "utf8" if (network or endian == "little") else "mutf8"}
                    try:
                        body = encode_roots(p, [("", sample)])
                        p2, roots2 = detect(body)
                        ok = len(roots2) == 1 and tag_equal(roots2[0][1], sample)
                        pld = dict(p)
                        pld["level_dat"] = {"version": 10}
                        bodyld = encode_roots(pld, [("", sample)])
                        p3, roots3 = detect(bodyld)
                        ok = ok and p3.get("level_dat") == {"version": 10} \
                            and tag_equal(roots3[0][1], sample)
                        check("roundtrip " + label, ok,
                              "params=%r roots=%d" % (p2, len(roots2)))
                    except Exception as e:  # noqa: BLE001
                        check("roundtrip " + label, False, "%s: %s" % (type(e).__name__, e))

    print("== network varint edge values ==")
    edge = Tag("compound", {
        "i": Tag("int", -2147483648), "j": Tag("int", 2147483647),
        "k": Tag("long", -(1 << 63)), "l": Tag("long", (1 << 63) - 1),
        "arr": Tag("int_array", [-1, 0, 1, 123456789]),
    })
    for endian in ("big", "little"):
        body = encode_roots({"endian": endian, "network": True}, [("", edge)])
        _, roots = detect(body)
        check("network varint edges %s" % endian, tag_equal(roots[0][1], edge))

    print("== multi-root (PMMP canonical style) ==")
    multi = [("", Tag("compound", {"n": Tag("string", "a")})),
             ("", Tag("compound", {"n": Tag("string", "b")})),
             ("", Tag("compound", {"n": Tag("string", "c")}))]
    body = encode_roots({"endian": "big", "network": True, "compression": "none"}, multi)
    p, roots = detect(body)
    check("multi-root detect", len(roots) == 3 and p["network"]
          and p["endian"] in ("big", "little"),
          "got %d roots %r" % (len(roots), p))

    print("== list root (Nukkit palette style) ==")
    body = encode_roots({"endian": "big", "network": False, "compression": "gzip"},
                        [("", list_root)])
    p, roots = detect(body)
    check("list-tag root gzip BE", roots[0][1].t == "list" and p["compression"] == "gzip")

    print("== byte-identical re-encode (raw formats) ==")
    for label, pp in [("LE raw", {"endian": "little", "network": False, "compression": "none"}),
                      ("BE raw mutf8", {"endian": "big", "network": False, "compression": "none"}),
                      ("BE network", {"endian": "big", "network": True, "compression": "none"}),
                      ("LE network", {"endian": "little", "network": True, "compression": "none"})]:
        body = encode_roots(pp, [("", sample)])
        p, roots = detect(body)
        body2 = encode_roots(p, roots)
        check("byte identical " + label, body == body2,
              "params=%r" % (p,))

    print("== SNBT round trips ==")
    cases = [("sample", sample), ("list", list_root),
             ("floats", Tag("compound", {"a": Tag("float", -0.0),
                                         "b": Tag("double", 0.1),
                                         "c": Tag("float", 3.4028235e38)}))]
    for name, tag in cases:
        for pretty in (False, True):
            text = to_snbt(tag, pretty=pretty)
            try:
                back = parse_snbt(text)
                check("snbt %s pretty=%s" % (name, pretty), tag_equal(back, tag))
            except NBTErr as e:
                check("snbt %s pretty=%s" % (name, pretty), False, str(e))
    try:
        lit = parse_snbt("{a:1b, b:2s, c:3, d:4L, e:1.5f, f:2.5d, g:true, h:false, "
                         "i:\"x\", j:'y', k:[B; 1, -1], l:[I;2], m:[L;3L], n:[B;1b,2b], "
                         "o:-Infinityf, p:NaNd, q:1.0E3, r:[], s:{}}")
        expect = Tag("compound", {
            "a": Tag("byte", 1), "b": Tag("short", 2), "c": Tag("int", 3), "d": Tag("long", 4),
            "e": Tag("float", 1.5), "f": Tag("double", 2.5), "g": Tag("byte", 1),
            "h": Tag("byte", 0), "i": Tag("string", "x"), "j": Tag("string", "y"),
            "k": Tag("byte_array", [1, -1]), "l": Tag("int_array", [2]),
            "m": Tag("long_array", [3]), "n": Tag("byte_array", [1, 2]),
            "o": Tag("float", -math.inf), "p": Tag("double", math.nan),
            "q": Tag("double", 1000.0), "r": Tag("list", []), "s": Tag("compound", {})})
        check("snbt literals", tag_equal(lit, expect), to_snbt(lit))
    except NBTErr as e:
        check("snbt literals", False, str(e))
    try:
        parse_snbt("[1b, 2s]")
        check("snbt rejects heterogeneous list", False)
    except NBTErr:
        check("snbt rejects heterogeneous list", True)
    check("snbt key quoting", parse_snbt('{"a b":1b}').v["a b"].v == 1)
    check("snbt bare word", parse_snbt("{k:hello}").v["k"].v == "hello")

    print("== JSON round trips ==")
    back = tag_from_json(tag_to_json(sample))
    check("json roundtrip", tag_equal(back, sample))

    print("== path navigation ==")
    segs = parse_path('.foo[0]."bar baz"')
    check("path parse", segs == [("key", "foo"), ("index", 0), ("key", "bar baz")])
    t = Tag("compound", {"foo": Tag("list", [Tag("compound", {"bar baz": Tag("int", 7)})])})
    check("path get", navigate(t, segs).v == 7)

    if corpus_dir:
        print("== corpus: %s ==" % corpus_dir)
        import glob
        files = []
        for pat in ("*.dat", "*.nbt"):
            files.extend(glob.glob(os.path.join(corpus_dir, pat)))
        files.sort()
        for f in files[:40]:
            base = os.path.basename(f)
            try:
                with open(f, "rb") as fh:
                    raw = fh.read()
                p, roots = detect(raw)
                body = encode_roots(p, roots)
                roots2 = decode_bytes(body, p)
                same = len(roots) == len(roots2) and all(
                    tag_equal(a[1], b[1]) for a, b in zip(roots, roots2))
                check("corpus %s [%s]" % (base, _params_str(p)), same)
            except Exception as e:  # noqa: BLE001
                check("corpus %s" % base, False, "%s: %s" % (type(e).__name__, e))

    if failures:
        print("\n%d FAILURE(S): %s" % (len(failures), ", ".join(failures[:10])))
        return 1
    print("\nall tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
