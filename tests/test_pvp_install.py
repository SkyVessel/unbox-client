import hashlib,importlib.util,json,pathlib,tempfile,unittest,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('pvp',ROOT/'scripts/prepare-pvp-mods.py');pvp=importlib.util.module_from_spec(spec);spec.loader.exec_module(pvp)
class InstallTests(unittest.TestCase):
 def test_disabled_and_custom_settings_survive_and_tampering_is_rejected(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=pathlib.Path(tmp);pvp.ROOT=root;(root/'client-mod').mkdir();cache=root/'.cache/pvp-1.21.11/jars';cache.mkdir(parents=True)
   entries=[]
   for id,on in [('example',True),('blur',False)]:
    jar=cache/(id+'.jar')
    with zipfile.ZipFile(jar,'w') as z:z.writestr('fabric.mod.json',json.dumps({'id':id}))
    entries.append({'id':id,'filename':jar.name,'sha512':hashlib.sha512(jar.read_bytes()).hexdigest(),'primary':True,'defaultEnabled':on})
   (root/'client-mod/pvp-lock-1.21.11-fabric.json').write_text(json.dumps({'mods':entries}));game=root/'game'
   pvp.prepare(game);self.assertTrue((game/'mods/example.jar').exists());self.assertTrue((game/'mods/blur.jar.disabled').exists())
   self.assertEqual(json.loads((game/'config/bettertotemcounter.json').read_text())['popAnimationScale'],0.5)
   (game/'config/bettertotemcounter.json').write_text('{"popAnimationScale":0.8}')
   (game/'mods/example.jar').rename(game/'mods/example.jar.disabled');(game/'config/hitreg.properties').write_text('renderHitbox=true\n')
   pvp.prepare(game);self.assertFalse((game/'mods/example.jar').exists());self.assertEqual((game/'config/hitreg.properties').read_text(),'renderHitbox=true\n')
   self.assertEqual(json.loads((game/'config/bettertotemcounter.json').read_text())['popAnimationScale'],0.8)
   bad=game/'mods/example.jar.disabled';bad.write_bytes(b'custom user jar')
   with self.assertRaises(ValueError):pvp.prepare(game)
   self.assertEqual(bad.read_bytes(),b'custom user jar')
if __name__=='__main__':unittest.main()
