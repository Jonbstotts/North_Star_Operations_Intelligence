from pathlib import Path

PATCHER = Path("ci/one_shot_foundational_cleanup.py")
source = PATCHER.read_text()
old = """# The legacy helper name may still be referenced by Information text methods;\n# keep a thin policy delegation, but reject any duplicated weather semantics.\nif 'automaticSevereWeatherActive()' in text:\n    raise SystemExit('automaticSevereWeatherActive reference survived refactor')\nif 'setAutomaticSevereWeatherActive' in text:\n    raise SystemExit('old showcase severe API survived refactor')\nwrite(path, text)\n"""
new = """# Manual and automatic severe sources share one source-agnostic presentation\n# contract. Replace remaining callers only after the owning methods have been\n# refactored above.\ntext=text.replace('automaticSevereWeatherActive()', 'severeWeatherActive()')\ntext=text.replace('setAutomaticSevereWeatherActive', 'setSevereWeatherActive')\nwrite(path, text)\n"""
if source.count(old) != 1:
    raise SystemExit("foundational patcher final severe-state block changed unexpectedly")
source = source.replace(old, new, 1)
exec(compile(source, str(PATCHER), "exec"), {"__name__": "__main__"})

# The transport patcher replaces startRefreshers with the complete scheduler
# cluster before replacing the pre-existing stopRefreshers method. Collapse the
# two staged copies into the one canonical lifecycle owner before compilation.
p = Path("src/com/wtm/ui/OperationsWorkspaceFrame.java")
text = p.read_text()
signature = "    private synchronized void stopRefreshers(){"
starts = []
pos = 0
while True:
    pos = text.find(signature, pos)
    if pos < 0:
        break
    starts.append(pos)
    pos += len(signature)
if len(starts) != 2:
    raise SystemExit(f"expected two staged stopRefreshers copies, found {len(starts)}")

def method_end(source_text: str, start: int) -> int:
    brace = source_text.find("{", start)
    depth = 0
    in_string = False
    in_char = False
    escaped = False
    for i in range(brace, len(source_text)):
        ch = source_text[i]
        if escaped:
            escaped = False
        elif ch == "\\" and (in_string or in_char):
            escaped = True
        elif ch == '"' and not in_char:
            in_string = not in_string
        elif ch == "'" and not in_string:
            in_char = not in_char
        elif not in_string and not in_char:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return i + 1
    raise SystemExit("unterminated staged stopRefreshers")

end = method_end(text, starts[0])
text = text[:starts[0]] + text[end:]
p.write_text(text)
