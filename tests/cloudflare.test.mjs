import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from '../cloudflare/worker.mjs';
test('unconfigured cloud service fails closed',async()=>{const r=await worker.fetch(new Request('https://unbox.test/v1/manifests',{method:'POST',body:'{}'}),{});assert.equal(r.status,503)});
test('publisher authentication precedes database mutation',async()=>{let touched=false;const r=await worker.fetch(new Request('https://unbox.test/v1/manifests',{method:'POST',body:'{}'}),{PUBLISH_TOKEN:'test-only',DB:{prepare(){touched=true;throw Error('unexpected')}}});assert.equal(r.status,401);assert.equal(touched,false)});
test('unsupported game versions are rejected before storing manifests',async()=>{const r=await worker.fetch(new Request('https://unbox.test/v1/manifests',{method:'POST',headers:{Authorization:'Bearer test-only'},body:JSON.stringify({gameVersion:'1.21.1',loader:'fabric',mods:[]})}),{PUBLISH_TOKEN:'test-only',DB:{prepare(){throw Error('unexpected')}}});assert.equal(r.status,400)});
