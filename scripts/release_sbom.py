"""Generate a CycloneDX inventory of resolved Maven artifacts and optionally check OSV."""

import argparse
import csv
import json
from pathlib import Path
from urllib.parse import quote
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("inventory", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--scan", action="store_true")
    args = parser.parse_args()

    components = {}
    with args.inventory.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source, delimiter="\t"):
            group, artifact, version = row["coordinate"].split(":")
            if group == "Najwa":
                continue  # Local project module, without an external Maven coordinate.
            purl = f"pkg:maven/{quote(group, safe='')}/{quote(artifact, safe='')}@{quote(version, safe='')}"
            components[purl] = {
                "type": "library", "group": group, "name": artifact,
                "version": version, "purl": purl,
            }
    if not components:
        raise SystemExit("No Maven components found")
    bom = {
        "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
        "components": list(components.values()),
    }
    args.output.write_text(json.dumps(bom, indent=2) + "\n", encoding="utf-8")
    if not args.scan:
        return

    payload = {"queries": [{"package": {"purl": purl}} for purl in components]}
    request = Request("https://api.osv.dev/v1/querybatch", data=json.dumps(payload).encode(),
                      headers={"Content-Type": "application/json"})
    with urlopen(request, timeout=45) as response:
        results = json.load(response)["results"]
    if len(results) != len(components) or any(result.get("next_page_token") for result in results):
        raise SystemExit("OSV returned an incomplete response")
    matches = {
        purl: [item["id"] for item in result.get("vulns", [])]
        for purl, result in zip(components, results) if result.get("vulns")
    }
    print(json.dumps({"scanned": len(components), "advisories": matches}, indent=2))
    if matches:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
