import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const source=ts.transpileModule(readFileSync(new URL('../app/src/keyBindings.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext}}).outputText;
const {keyName,keyLabel}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
test('launcher stores stable Minecraft key names across GLFW and SDL versions',()=>{
 for(const [code,name]of [['KeyC','key.keyboard.c'],['KeyM','key.keyboard.m'],['ControlLeft','key.keyboard.left.control'],['F5','key.keyboard.f5'],['Digit7','key.keyboard.7']])assert.equal(keyName(code),name);
 assert.equal(keyName('Unidentified'),undefined);
 assert.equal(keyLabel('key.keyboard.c'),'C');assert.equal(keyLabel('key.keyboard.left.control'),'Left Control');assert.equal(keyLabel('key.keyboard.f5'),'F5');assert.equal(keyLabel('key.keyboard.unknown'),'Unbound');
 assert.equal(keyLabel(67),'C');assert.equal(keyLabel('341'),'Left Control');assert.equal(keyLabel(-1),'Unbound');
});
