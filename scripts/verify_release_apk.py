"""Verify the exact APK selected for distribution. No signing key is needed."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

from audit_privacy import APP_ID, NS, manifest_issues, source_issues

SIGNER_SHA256 = "46789626735989bcb24d3ad9c57f930b80ef2e6fc60f81d9244a84a447c21cdd"


def run(*args):
    result = subprocess.run(args, capture_output=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise ValueError(f"{Path(args[0]).name} exited with {result.returncode}: {result.stderr.strip()}")
    return result.stdout


def android_manifest(dump):
    """Turn the AAPT2 XML tree dump into elements for the shared manifest audit."""
    stack = []
    manifest = None
    for raw_line in dump.splitlines():
        line = raw_line.lstrip()
        indentation = len(raw_line) - len(line)
        if line.startswith("E: "):
            tag = line[3:].split()[0]
            while stack and indentation <= stack[-1][0]:
                stack.pop()
            element = ET.Element(tag)
            if stack:
                stack[-1][1].append(element)
            elif tag == "manifest":
                manifest = element
            stack.append((indentation, element))
        elif line.startswith("A: ") and stack:
            key, _, value = line[3:].partition("=")
            key = re.sub(r"\(0x[0-9a-fA-F]+\)$", "", key)
            if key.startswith("http://schemas.android.com/apk/res/android:"):
                key = NS + key.rsplit(":", 1)[1]
            quoted = re.match(r'^"((?:[^"\\]|\\.)*)"', value)
            if quoted:
                value = quoted.group(1)
            stack[-1][1].set(key, value)
    if manifest is None:
        raise ValueError("APK manifest could not be read")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--target-sdk", default=36, type=int)
    parser.add_argument("--expected-sha256", help="Published APK digest, for download verification")
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")
    apk = args.apk.resolve()
    extension = ".exe" if os.name == "nt" else ""
    build_tools = args.build_tools.resolve()
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / f"java{extension}") if java_home else "java"

    try:
        signature = run(java, "-jar", str(build_tools / "lib/apksigner.jar"),
                        "verify", "--print-certs", str(apk))
        signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$",
                             signature.lower(), re.MULTILINE | re.IGNORECASE)
        if signers != [SIGNER_SHA256]:
            raise ValueError("APK is not signed solely by the pinned Najwa release certificate")
        run(str(build_tools / f"zipalign{extension}"), "-c", "-P", "16", "4", str(apk))
        tree = android_manifest(run(str(build_tools / f"aapt2{extension}"),
                                    "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"))
        issues, permissions = manifest_issues(tree, release=True)
        issues.extend(source_issues())
        sdk = tree.find("uses-sdk")
        if tree.get("package") != APP_ID:
            issues.append("Wrong package ID")
        if tree.get(NS + "versionCode") != str(args.version_code) or tree.get(NS + "versionName") != args.version_name:
            issues.append("Wrong release version")
        if sdk is None or sdk.get(NS + "targetSdkVersion") != str(args.target_sdk):
            issues.append("Wrong target SDK")
        with apk.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if args.expected_sha256 and digest != args.expected_sha256.lower():
            issues.append("APK SHA-256 does not match the supplied digest")
        if issues:
            raise ValueError("\n".join(issues))
    except (OSError, ValueError) as error:
        raise SystemExit(f"RELEASE VERIFICATION FAILED: {error}") from error

    print(json.dumps({
        "status": "PASS",
        "package": APP_ID,
        "version_name": args.version_name,
        "version_code": args.version_code,
        "target_sdk": args.target_sdk,
        "apk_sha256": digest,
        "certificate_sha256": SIGNER_SHA256,
        "permissions": sorted(permissions),
    }, indent=2))


if __name__ == "__main__":
    main()
