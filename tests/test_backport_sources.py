"""Protect unchanged native targets and the user-approved 1.20.1 exception."""
import importlib.util
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('port_sources', ROOT / 'scripts/prepare-version-sources.py')
ports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ports)


class BackportSources(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for folder in ('main', 'fabric', 'neoforge', '1.21.11', '1.20.1', '26.3'):
            shutil.copytree(ROOT / 'client-mod/src' / folder, self.root / 'client-mod/src' / folder)
        ports.ROOT = self.root

    def tearDown(self):
        ports.ROOT = ROOT
        self.temp.cleanup()

    @staticmethod
    def sources(folder):
        return {p.name: p.read_text() for p in folder.glob('*.java')}

    def test_existing_native_sources_match_branch_baseline(self):
        original = subprocess.check_output(['git', 'show', '9e8c74c911cacec7317efecadcab59eebdf1ddf5:scripts/prepare-version-sources.py'], cwd=ROOT, text=True)
        baseline = {'__file__': str(ROOT / 'scripts/prepare-version-sources.py'), '__name__': 'baseline'}
        exec(compile(original, 'baseline-prepare-version-sources.py', 'exec'), baseline)
        baseline['ROOT'] = self.root
        for version in ('26.1', '26.1.2'):
            for loader in ('fabric', 'neoforge'):
                with self.subTest(version=version, loader=loader):
                    expected = self.sources(baseline['prepare'](version, loader))
                    self.assertEqual(expected, self.sources(ports.prepare(version, loader)))

    def test_263_ui_is_version_scoped(self):
        for loader in ('fabric','neoforge'):
            current=self.sources(ports.prepare('26.3',loader))
            for version in ('26.1','26.1.2','1.20.1'):
                older=self.sources(ports.prepare(version,loader))
                self.assertNotIn('SolidScreen.java',older)
                self.assertNotIn('SolidUiChecks.tick',older['UnboxClient.java'])
                self.assertNotIn('extends SolidScreen',older['SkinLibraryScreen.java'])
            self.assertIn('extends SolidScreen',current['UnboxHomeScreen.java'])
            self.assertIn('SolidUiChecks.tick',current['UnboxClient.java'])
            self.assertIn('SolidTheme.opacity()>=1',current['SolidScreen.java'])
            port=self.sources(ports.prepare('1.21.11',loader))
            self.assertIn('extends SolidScreen',port['UnboxHomeScreen.java'])
            self.assertIn('return false;',current['PvpModules.java'])
            self.assertEqual('ViewmodelMixin.java' in port,loader=='fabric')
            self.assertIn('"Background",false',current['ModOptions.java'])
            for name in ('viewmodel','daytime'):
                self.assertNotIn('new Mod("'+name+'"',current['ModOptions.java'])
                self.assertEqual('new Mod("'+name+'"' in port['ModOptions.java'],loader=='fabric')
            self.assertEqual('PvpSettings.write(key' in port['UnboxClient.java'],loader=='fabric')
            self.assertNotIn('PvpSettings.write(key',current['UnboxClient.java'])

    def test_1201_removes_only_approved_module(self):
        baseline = self.sources(ports.prepare('26.1', 'fabric'))
        old = self.sources(ports.prepare('1.20.1', 'fabric'))
        self.assertEqual(set(baseline) - set(old), {'HappyGhastRecall.java', 'HappyGhastRecallMixin.java'})
        self.assertFalse(any('ghast' in text.lower() for text in old.values()))
        modules = lambda text: set(re.findall(r'new Mod\("([^"]+)"', text))
        self.assertEqual(modules(baseline['ModOptions.java']) - {'ghast'}, modules(old['ModOptions.java']))
        for check in ('build(mc,true);shulker(mc,true);', 'ModToggleChecks.run(mc)', 'Native Resourcify browse button opens its full interface', 'World feature changes persist in the world directory'):
            self.assertIn(check, old['FeatureSmokeTest.java'])
        self.assertIn('MapSharing.died(owner)', old['SurvivalLanSmokeTest.java'])
        self.assertIn('serverEnabled("shulker")', old['SurvivalHostMods.java'])

    def test_12111_retains_ghast_and_every_module(self):
        baseline = self.sources(ports.prepare('26.1', 'fabric'))
        current = self.sources(ports.prepare('1.21.11', 'fabric'))
        self.assertTrue(set(baseline).issubset(current))
        modules=lambda text:set(re.findall(r'new Mod\("([^"]+)"',text))
        self.assertEqual(modules(current['ModOptions.java'])-modules(baseline['ModOptions.java']),{'viewmodel','daytime'})
        self.assertIn('HappyGhastRecall.init()', current['UnboxClient.java'])
        self.assertIn('HappyGhastRecall.Request.TYPE', current['MapTransport.java'])

    def test_1201_uses_java17_background_workers(self):
        old = self.sources(ports.prepare('1.20.1', 'fabric'))
        self.assertFalse(any('Thread.ofVirtual()' in text for text in old.values()))
        self.assertIn('LegacyThreads.factory("unbox-mod-sync-")', old['EnvironmentSync.java'])
        self.assertIn('LegacyThreads.start("unbox-route-stream",', old['VoxLinkTransport.java'])
        self.assertIn('com.google.common.collect.Lists.reverse(ClientHud.IDS)', old['UnboxScreen.java'])


if __name__ == '__main__':
    unittest.main()
