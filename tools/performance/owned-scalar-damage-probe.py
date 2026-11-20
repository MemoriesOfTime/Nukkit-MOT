#!/usr/bin/env python3
"""Bounded javac/main pair; compiles real source closure and never invokes a build/test runner."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess


SOURCES = [
    "src/main/java/cn/nukkit/event/entity/EntityDamageEvent.java",
    "src/main/java/cn/nukkit/event/entity/EntityEvent.java",
    "src/main/java/cn/nukkit/event/Event.java",
    "src/main/java/cn/nukkit/event/Cancellable.java",
    "src/main/java/cn/nukkit/event/HandlerList.java",
    "src/main/java/cn/nukkit/utils/EventException.java",
    "src/main/java/cn/nukkit/potion/Effect.java",
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(command, log, timeout, cwd):
    result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout, text=True)
    log.write_text(result.stdout)
    if result.returncode:
        raise RuntimeError(f"exit {result.returncode}: {log}\n{result.stdout[-5000:]}")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core-jar", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True, help="outside the source clone")
    parser.add_argument("--base", default="b216d2b21fe9f25d242ec3832234a6a3092ab029")
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--dependency", type=Path, action="append", default=[])
    parser.add_argument("--forks", type=int, default=3, choices=range(1, 6))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    jar, out = args.core_jar.resolve(), args.out.resolve()
    dependencies = [path.resolve() for path in args.dependency]
    dependency_cp = os.pathsep.join(str(path) for path in [jar] + dependencies)
    if out == root or root in out.parents:
        parser.error("--out must be outside the clone")
    java_home = args.java_home or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    java = str(java_home / "bin/java") if java_home else shutil.which("java")
    javac = str(java_home / "bin/javac") if java_home else shutil.which("javac")
    if not java or not javac or not all(path.is_file() for path in [jar] + dependencies):
        parser.error("JDK and --core-jar must exist")
    out.mkdir(parents=True, exist_ok=True)
    base = subprocess.check_output(["git", "rev-parse", args.base], cwd=root, text=True, timeout=10).strip()
    summary = {"base": base, "core_jar": str(jar), "core_jar_sha256": sha(jar.read_bytes()),
               "dependencies": {str(path): sha(path.read_bytes()) for path in dependencies},
               "source_closure": {}, "forks": [], "allocation_scope":
               "real escaped events; equal one Float boxing per event; public Map path EA/control"}
    summary["java"] = run([java, "-version"], out / "java-version.log", 10, root).strip()
    summary["probe_sha256"] = sha((root / "tools/performance/OwnedScalarDamageProbe.java").read_bytes())
    summary["runner_sha256"] = sha(Path(__file__).read_bytes())
    empty = out / "empty-sourcepath"
    empty.mkdir(exist_ok=True)
    for variant in ("before", "after"):
        folder = out / variant
        folder.mkdir(exist_ok=True)
        classes = folder / "classes"
        classes.mkdir(exist_ok=True)
        source_files = []
        for relative in SOURCES:
            data = (subprocess.check_output(["git", "show", f"{base}:{relative}"], cwd=root, timeout=10)
                    if variant == "before" else (root / relative).read_bytes())
            target = folder / "source" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            source_files.append(str(target))
            summary["source_closure"].setdefault(relative, {})[variant] = sha(data)
        command = [javac, "-proc:none", "-implicit:none", "-sourcepath", str(empty),
                   "-classpath", dependency_cp, "-d", str(classes)] + source_files
        command.append(str(root / "tools/performance/OwnedScalarDamageProbe.java"))
        run(command, folder / "compile.log", 60, root)
    for fork in range(args.forks):
        pair = {"index": fork + 1}
        order = ("before", "after") if fork % 2 == 0 else ("after", "before")
        for variant in order:
            cp = str(out / variant / "classes") + os.pathsep + dependency_cp
            result = run([java, "-Xms128m", "-Xmx256m", "-cp", cp, "OwnedScalarDamageProbe"],
                         out / variant / f"fork-{fork + 1}.log", 45, root)
            parity = re.search(r"PARITY pairs=(\d+) checks=(\d+) sha256=([0-9a-f]{64})", result)
            allocation = re.search(r"ALLOCATION operations=(\d+) scalar=(\[[^\n]+?\]) publicMap=(\[[^\n]+?\])", result)
            if not parity or not allocation:
                raise RuntimeError(f"missing result in {variant} fork {fork + 1}")
            count = int(allocation.group(1))
            scalar, mapped = json.loads(allocation.group(2)), json.loads(allocation.group(3))
            pair[variant] = {"pairs": int(parity.group(1)), "checks": int(parity.group(2)),
                             "parity_sha256": parity.group(3), "operations": count,
                             "scalar_bytes": scalar, "public_map_bytes": mapped,
                             "scalar_B_per_event": statistics.median(scalar) / count,
                             "public_map_B_per_event": statistics.median(mapped) / count}
        if pair["before"]["parity_sha256"] != pair["after"]["parity_sha256"]:
            raise AssertionError("before/after behavior digest differs")
        summary["forks"].append(pair)
        print(json.dumps(pair, ensure_ascii=False), flush=True)
    parity_values = {p[v]["parity_sha256"] for p in summary["forks"] for v in ("before", "after")}
    if len(parity_values) != 1:
        raise AssertionError("parity digest differs between forks")
    summary["medians_B_per_event"] = {
        v: {path: statistics.median(p[v][path] for p in summary["forks"])
            for path in ("scalar_B_per_event", "public_map_B_per_event")}
        for v in ("before", "after")}
    medians = summary["medians_B_per_event"]
    summary["scalar_saved_B_per_event"] = medians["before"]["scalar_B_per_event"] - medians["after"]["scalar_B_per_event"]
    summary["public_map_delta_B_per_event"] = medians["after"]["public_map_B_per_event"] - medians["before"]["public_map_B_per_event"]
    (out / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(summary["medians_B_per_event"], indent=2), flush=True)


if __name__ == "__main__":
    main()
