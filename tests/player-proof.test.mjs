import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {verifyPlayerProof,proofMessage} from '../cloudflare/player-proof.mjs';

const root=generateKeyPairSync('rsa',{modulusLength:2048});
const player=generateKeyPairSync('rsa',{modulusLength:2048});
const rootDer=root.publicKey.export({type:'spki',format:'der'}).toString('base64');
const publicDer=player.publicKey.export({type:'spki',format:'der'});
function fixture(){
 const context={id:crypto.randomUUID(),tokenHash:'a'.repeat(64),nonce:'b'.repeat(32),uuid:crypto.randomUUID().replaceAll('-',''),now:Date.now()};
 const expires=context.now+3600000,certificate=Buffer.alloc(24+publicDer.length);
 Buffer.from(context.uuid,'hex').copy(certificate);certificate.writeBigInt64BE(BigInt(expires),16);publicDer.copy(certificate,24);
 const proof={version:1,expiresAt:new Date(expires).toISOString(),publicKey:publicDer.toString('base64'),certificateSignature:sign('RSA-SHA1',certificate,root.privateKey).toString('base64'),signature:sign('RSA-SHA256',proofMessage(context),player.privateKey).toString('base64')};
 return {proof,context};
}
test('player proof requires both the trusted certificate and possession of its private key',async()=>{
 const {proof,context}=fixture();
 assert.equal(await verifyPlayerProof(proof,context,[rootDer]),true);
 assert.equal(await verifyPlayerProof(proof,context),false,'test issuer must never pass production Mojang roots');
 assert.equal(await verifyPlayerProof({...proof,signature:sign('RSA-SHA256',proofMessage(context),root.privateKey).toString('base64')},context,[rootDer]),false);
});
test('certificate proof cannot be replayed for another challenge, account, session token or UUID',async()=>{
 const {proof,context}=fixture();
 for(const key of ['id','tokenHash','nonce','uuid'])assert.equal(await verifyPlayerProof(proof,{...context,[key]:context[key].replace(/^./,'f')},[rootDer]),false,key);
 assert.equal(await verifyPlayerProof(proof,{...context,now:context.now+3600000},[rootDer]),false,'expired');
});
test('modified certificates, malformed keys, oversized input and unsupported proof versions fail closed',async()=>{
 const {proof,context}=fixture();
 for(const patch of [{version:2},{expiresAt:new Date(context.now+7200000).toISOString()},{certificateSignature:proof.signature},{publicKey:'?'},{publicKey:'A'.repeat(2049)},{signature:''},{expiresAt:'invalid'}])assert.equal(await verifyPlayerProof({...proof,...patch},context,[rootDer]),false);
});
