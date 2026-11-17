#!/usr/bin/env python3
"""Build strict signed device distribution options for Xcode 16+."""
import pathlib
import plistlib
import sys
team, profile_uuid, output_file = sys.argv[1:4]
options = {
    "method": "release-testing",
    "destination": "export",
    "signingStyle": "manual",
    "teamID": team,
    "provisioningProfiles": {"com.pt2vhf.speedalert.ios": profile_uuid},
    "stripSwiftSymbols": True,
    "thinning": "<none>",
}
pathlib.Path(output_file).write_bytes(plistlib.dumps(options,fmt=plistlib.FMT_XML))
print("Ad Hoc / release-testing export options generated.")
