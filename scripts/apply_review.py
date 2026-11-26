#!/usr/bin/env python3
"""Apply a filled scholarly review file to core/WorshipCatalog.kt.

    python -I scripts/apply_review.py najwa-review-1.7.0.json core/src/main/kotlin/org/najwa/core/WorshipCatalog.kt

The review file comes from the app (Settings > Worship > Scholarly review file). For each rule the reviewer sets
"verdict" to APPROVED or REJECTED and may add a note, and fills "reviewer" and "date". Only APPROVED rules change:
their VirtueRule gets status = EvidenceStatus.APPROVED and a comment line naming the reviewer, date and note.
Nothing is approved without a reviewer name and date; the script never invents either.
"""
import json
import re
import sys


def main(review_path, catalog_path):
    review = json.load(open(review_path, encoding="utf-8"))
    if review.get("najwaReview") != 1:
        raise SystemExit("not a Najwa review file")
    reviewer = (review.get("reviewer") or "").strip()
    date = (review.get("date") or "").strip()
    if not reviewer or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
        raise SystemExit("reviewer name and date (YYYY-MM-DD) are required")
    approved = {r["id"]: (r.get("note") or "").strip() for r in review["rules"] if (r.get("verdict") or "").upper() == "APPROVED"}
    src = open(catalog_path, encoding="utf-8").read()
    changed = 0
    for rule_id, note in approved.items():
        start = src.find(f'VirtueRule("{rule_id}"')
        if start < 0:
            print("not in catalog:", rule_id)
            continue
        depth, i = 0, src.index("(", start)
        while True:
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        call = src[start:i]
        if "status = EvidenceStatus.APPROVED" in call:
            continue
        new_call = re.sub(r",\s*status\s*=\s*EvidenceStatus\.\w+", "", call) + ", status = EvidenceStatus.APPROVED"
        line_start = src.rfind("\n", 0, start) + 1
        indent = src[line_start:start]
        comment = f"{indent}// Approved by {reviewer} on {date}{': ' + note if note else ''}.\n"
        src = src[:line_start] + comment + indent + new_call + src[i:]
        changed += 1
    open(catalog_path, "w", encoding="utf-8", newline="\n").write(src)
    print(f"{changed} rule(s) approved by {reviewer} on {date}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
