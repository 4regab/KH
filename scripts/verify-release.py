#!/usr/bin/env python3
"""Check release identity; signed APKs must retain the v3.2 certificate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
# Verified with apksigner against the published v3.2/KH-3.2.apk.
ESTABLISHED_CERT = '9f111ae130b036c5a6b628274f0c0f9c4e7de5370c77ae28d4a02f4019a6aa5c'


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def verify(apk, unsigned=False):
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError('APK is missing or empty')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    tools = sdk / 'build-tools' / '35.0.0'
    config = (ROOT / 'app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', config)[1]
    code = re.search(r'versionCode\s*=\s*(\d+)', config)[1]
    badging = run(str(tools / 'aapt'), 'dump', 'badging', str(apk))
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.groups() != ('com.kisskh.tv', code, version):
        raise ValueError('Package/version does not match the release configuration')
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)[1]
    if minimum != '23':
        raise ValueError('Unexpected minimum SDK: ' + minimum)
    if "application-label:'KH'" not in badging:
        raise ValueError('Unexpected application label')
    if re.search(r'^application-debuggable', badging, re.M):
        raise ValueError('Release is debuggable')
    run(str(tools / 'zipalign'), '-c', '-p', '4', str(apk))
    fingerprint = None
    commit = run('git', '-C', str(ROOT), 'rev-parse', 'HEAD').strip()
    dirty = bool(run('git', '-C', str(ROOT), 'status', '--porcelain', '--untracked-files=no').strip())
    with zipfile.ZipFile(apk) as archive:
        vcs = archive.read('META-INF/version-control-info.textproto').decode()
        revision = re.search(r'revision:\s*"([a-f0-9]+)"', vcs)
        if not revision or revision[1] != commit:
            raise ValueError('Embedded APK commit does not match the checkout')
    if not unsigned:
        if dirty:
            raise ValueError('Refusing to publish from a modified checkout')
        certs = run(str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk))
        fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)', certs)
        if fingerprints != [ESTABLISHED_CERT]:
            raise ValueError('Signing certificate differs from v3.2; refusing incompatible update')
        fingerprint = fingerprints[0]
    if os.environ.get('GITHUB_REF_TYPE') == 'tag' and os.environ['GITHUB_REF_NAME'] != 'v' + version:
        raise ValueError('Tag does not match versionName')
    for name in ('ShelfTopBar.kt', 'ShelfHome.kt'):
        content = (ROOT / 'app/src/main/java/com/kisskh/tv/ui/shelf' / name).read_text()
        if any(symbol in content for symbol in ('CategoryChips', 'showCategories', 'categoryTabs')):
            raise ValueError('Removed Home chips have returned')
    result = {
        'file': apk.name, 'package': package[1], 'versionName': version,
        'versionCode': int(code), 'minSdk': int(minimum), 'size': apk.stat().st_size,
        'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'certificateSha256': fingerprint,
        'signingStatus': 'unsigned; NOT INSTALLABLE' if unsigned else 'established Android Debug certificate; not production-signed',
        'commit': commit, 'trackedCheckoutModified': dirty,
        'runtimeTests': 'Not run by this workflow; device QA required',
    }
    apk.with_suffix('.json').write_text(json.dumps(result, indent=2) + '\n')
    apk.with_suffix('.apk.sha256').write_text(result['sha256'] + '  ' + apk.name + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--unsigned', action='store_true', help='Validate a non-installable build, never for publication')
    args = parser.parse_args()
    verify(args.apk, args.unsigned)
