#!/usr/bin/env python3
"""Validate registered-device Ad Hoc profile and install only after verification."""
import datetime
import pathlib
import plistlib
import re
import sys

plist_path, team, bundle = sys.argv[1:4]
profile = plistlib.loads(pathlib.Path(plist_path).read_bytes())
assert re.fullmatch(r"[A-Z0-9]{10}", team), "Invalid Apple Team ID"
assert bundle == "com.pt2vhf.speedalert.ios", "Wrong bundle"
assert team in profile.get("TeamIdentifier", []), "Wrong Apple signing team"
assert profile.get("Entitlements", {}).get("application-identifier") == team + "." + bundle, (
    "Provisioning profile does not match the explicit app identifier"
)
assert profile.get("Entitlements", {}).get("get-task-allow") is False, (
    "Expected distribution profile, not developer debugging profile"
)
assert profile.get("ProvisionedDevices"), (
    "Expected Ad Hoc provisioning profile with registered iPhone UDIDs"
)
assert not profile.get("ProvisionsAllDevices", False), "Enterprise profile is not Ad Hoc"
expiry = profile["ExpirationDate"]
if expiry.tzinfo is None:
    expiry = expiry.replace(tzinfo=datetime.timezone.utc)
assert expiry > datetime.datetime.now(datetime.timezone.utc), "Apple provisioning profile expired"
uuid = profile["UUID"]
assert re.fullmatch(r"[A-Fa-f0-9-]{36}", uuid), "Invalid Apple profile UUID"
if len(sys.argv) >= 6:
    output_path, source_profile = sys.argv[4:6]
    destination_dir = pathlib.Path.home() / "Library/MobileDevice/Provisioning Profiles"
    destination_dir.mkdir(parents=True, exist_ok=True)
    (destination_dir / (uuid + ".mobileprovision")).write_bytes(
        pathlib.Path(source_profile).read_bytes()
    )
    with open(output_path, "a", encoding="utf-8") as out:
        out.write("IOS_PROFILE_UUID=" + uuid + "\n")
print("Verified Ad Hoc profile for", len(profile["ProvisionedDevices"]), "registered iPhones.")
