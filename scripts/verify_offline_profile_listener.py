#!/usr/bin/env python3
"""Narrow source-contract guard; not a substitute for real Paper runtime tests."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PATH = Path('src/main/java/com/balugaq/jeg/core/listeners/GuideHistoryPatchListener.java')


def inspect(source: str) -> list[str]:
    # This deliberately protects the short reviewed handler, not arbitrary Java parsing.
    match = re.search(r'public void onProfileLoad\(AsyncProfileLoadEvent event\)\s*\{([^{}]*)\}', source)
    if match is None:
        return ['Profile-load handler shape changed; review this contract explicitly']
    body = re.sub(r'//[^\n]*', '', match.group(1))
    errors = []
    if re.search(r'\bgetPlayer\s*\(', body):
        errors.append('Async profile loads must not dereference a live Player')
    if not re.search(r'\b(?:var|PlayerProfile) profile\s*=\s*event\.getProfile\(\)\s*;', body):
        errors.append('Capture the supplied profile exactly once')
    if body.count('event.getProfile()') != 1:
        errors.append('Profile lookup count changed')
    patch = 'GuideUtil.getProfile(profile);'
    log = 'Debug.info("Patched Slimefun PlayerProfile " + profile.getUUID());'
    if body.count(patch) != 1 or body.count(log) != 1:
        errors.append('Retain one patch callback and UUID-only success diagnostic')
    elif body.index(patch) > body.index(log):
        errors.append('Do not report success before the patch callback returns')
    if re.search(r'\b(if|return|try|catch)\b', body):
        errors.append('Do not skip offline profiles or swallow patch failures')
    if 'GuideUtil.getProfile(event.getPlayer()); // trigger patch' not in source:
        errors.append('Keep the existing player-join patch path')
    return errors


GOOD = '''public void onProfileLoad(AsyncProfileLoadEvent event) {
    var profile = event.getProfile();
    GuideUtil.getProfile(profile);
    Debug.info("Patched Slimefun PlayerProfile " + profile.getUUID());
}
public void onPlayerJoin(PlayerJoinEvent event) {
    GuideUtil.getProfile(event.getPlayer()); // trigger patch
}
'''


class ContractTests(unittest.TestCase):
    def test_reviewed_handler_passes(self):
        self.assertEqual([], inspect(GOOD))

    def test_old_null_dereference_is_rejected(self):
        old = GOOD.replace('var profile = event.getProfile();',
                           'var profile = event.getProfile();\n    Debug.info(profile.getPlayer().getName());')
        self.assertTrue(inspect(old))

    def test_missing_patch_is_rejected(self):
        self.assertTrue(inspect(GOOD.replace('GuideUtil.getProfile(profile);', '')))

    def test_success_before_patch_is_rejected(self):
        log = 'Debug.info("Patched Slimefun PlayerProfile " + profile.getUUID());'
        mutated = GOOD.replace('GuideUtil.getProfile(profile);', 'PLACEHOLDER').replace(log, 'GuideUtil.getProfile(profile);').replace('PLACEHOLDER', log)
        self.assertTrue(inspect(mutated))

    def test_early_return_is_rejected(self):
        self.assertTrue(inspect(GOOD.replace('GuideUtil.getProfile(profile);', 'return;\n    GuideUtil.getProfile(profile);')))

    def test_repeated_lookup_is_rejected(self):
        self.assertTrue(inspect(GOOD.replace('var profile = event.getProfile();', 'var profile = event.getProfile(); event.getProfile();')))

    def test_removed_join_path_is_rejected(self):
        self.assertTrue(inspect(GOOD.replace('GuideUtil.getProfile(event.getPlayer()); // trigger patch', '')))

    def test_missing_handler_is_rejected(self):
        self.assertTrue(inspect(''))


if __name__ == '__main__':
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ContractTests))
    if not result.wasSuccessful():
        raise SystemExit(1)
    failures = inspect((ROOT / PATH).read_text(encoding='utf-8'))
    if failures:
        raise SystemExit('\n'.join(failures))
    print('OFFLINE_PROFILE_LISTENER_SOURCE_CONTRACT_PASS; runtime validation is separate')
