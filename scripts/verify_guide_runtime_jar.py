#!/usr/bin/env python3
"""Verify the actual distributable, including every compiled JEG nested class."""
from __future__ import annotations
import argparse
import re
import zipfile
from pathlib import Path

PREFIX = 'com/balugaq/jeg/'
SYNCHRONOUS_CHAT_APIS = (
    b'org/bukkit/event/player/PlayerChatEvent',
    b'io/papermc/paper/event/player/ChatEvent',
)
REQUIRED = {PREFIX + name + '.class' for name in (
    'implementation/JustEnoughGuide',
    'api/interfaces/JEGSlimefunGuideImplementation',
    'utils/clickhandler/OnDisplay', 'utils/clickhandler/OnDisplay$ItemGroup',
    'utils/clickhandler/OnDisplay$ItemGroup$DisplayType',
    'utils/clickhandler/OnDisplay$ItemGroup$Normal',
    'utils/clickhandler/OnDisplay$ItemGroup$Bookmark',
    'utils/clickhandler/OnDisplay$ItemGroup$Locked',
    'utils/clickhandler/OnDisplay$ItemGroup$NoPermission',
    'utils/clickhandler/OnDisplay$Item', 'utils/clickhandler/OnDisplay$RecipeType',
    'utils/clickhandler/OnClick', 'utils/clickhandler/OnClick$ItemGroup',
    'utils/clickhandler/OnClick$ItemGroup$Normal',
    'libraries/libby/LibraryManager',
)}

def verify(path: Path, classes: Path | None = None, version: str | None = None) -> int:
    required = set(REQUIRED)
    if classes is not None:
        compiled = {p.relative_to(classes).as_posix() for p in (classes / PREFIX).rglob('*.class')}
        if not compiled:
            raise ValueError('No compiled JEG classes found; refusing an empty comparison')
        required.update(compiled)
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive entries')
        if jar.testzip() is not None:
            raise ValueError('Archive CRC failure')
        missing = sorted(required - set(names))
        if missing:
            raise ValueError('Missing runtime classes: ' + ', '.join(missing))
        descriptor = jar.read('plugin.yml').decode('utf-8')
        if version is not None:
            found = re.search(r'''(?m)^version:\s*['"]?([^'"\s]+)''', descriptor)
            if not found or found.group(1) != version:
                raise ValueError('Plugin descriptor version does not match the release')
        count = 0
        for name in names:
            if name.startswith(('net/byteflux/libby/', 'org/mockbukkit/', 'org/junit/', 'audit/')):
                raise ValueError('Unexpected unrelocated or test classes: ' + name)
            if name.endswith('.class') and not name.startswith('META-INF/versions/'):
                data = jar.read(name)
                if len(data) < 8 or data[:4] != b'\xca\xfe\xba\xbe':
                    raise ValueError('Invalid class header: ' + name)
                minor, major = int.from_bytes(data[4:6], 'big'), int.from_bytes(data[6:8], 'big')
                if major > 65 or minor == 65535:
                    raise ValueError('Unsupported Java bytecode: ' + name)
                if name.startswith(PREFIX) and any(api in data for api in SYNCHRONOUS_CHAT_APIS):
                    raise ValueError('Synchronous chat API forces chat onto the main thread: ' + name)
                count += 1
    return count

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--classes', type=Path)
    parser.add_argument('--version')
    args = parser.parse_args()
    try:
        count = verify(args.jar, args.classes, args.version)
    except (OSError, ValueError, KeyError, UnicodeError, zipfile.BadZipFile) as error:
        raise SystemExit(f'Guide runtime JAR validation failed: {error}') from error
    print(f'Guide runtime JAR validation passed: {count} classes; nested renderer classes retained')

if __name__ == '__main__':
    main()
