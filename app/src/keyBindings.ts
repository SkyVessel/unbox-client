function legacyLabel(n:number){if(n===-1)return 'Unbound';if(n>=65&&n<=90||n>=48&&n<=57)return String.fromCharCode(n);if(n>=290&&n<=314)return 'F'+(n-289);return ({32:'Space',257:'Enter',258:'Tab',259:'Backspace',262:'Right',263:'Left',264:'Down',265:'Up',340:'Left Shift',341:'Left Control',342:'Left Alt',344:'Right Shift',345:'Right Control',346:'Right Alt'} as Record<number,string>)[n]||`Key ${n}`}
export function keyLabel(value:string|number){
 const name=String(value);if(/^-?\d+$/.test(name))return legacyLabel(Number(value));
 if(name==='key.keyboard.unknown')return 'Unbound';
 return name.replace(/^key\.(keyboard|mouse)\./,'').split('.').map(part=>part.length===1||/^f\d+$/.test(part)?part.toUpperCase():part[0]?.toUpperCase()+part.slice(1)).join(' ');
}
export function keyName(code:string){
 if(/^Key[A-Z]$/.test(code))return 'key.keyboard.'+code.slice(3).toLowerCase();
 if(/^Digit[0-9]$/.test(code))return 'key.keyboard.'+code.slice(5);
 if(/^F([1-9]|1[0-9]|2[0-5])$/.test(code))return 'key.keyboard.'+code.toLowerCase();
 const name=({Space:'space',Tab:'tab',Enter:'enter',Backspace:'backspace',ArrowRight:'right',ArrowLeft:'left',ArrowDown:'down',ArrowUp:'up',ShiftLeft:'left.shift',ControlLeft:'left.control',AltLeft:'left.alt',ShiftRight:'right.shift',ControlRight:'right.control',AltRight:'right.alt',MetaLeft:'left.win',MetaRight:'right.win',Backquote:'grave.accent',Minus:'minus',Equal:'equal',BracketLeft:'left.bracket',BracketRight:'right.bracket',Backslash:'backslash',Semicolon:'semicolon',Quote:'apostrophe',Comma:'comma',Period:'period',Slash:'slash',Home:'home',End:'end',PageUp:'page.up',PageDown:'page.down',Insert:'insert',CapsLock:'caps.lock',NumLock:'num.lock',ScrollLock:'scroll.lock',Pause:'pause',PrintScreen:'print.screen',ContextMenu:'menu'} as Record<string,string>)[code];
 return name?'key.keyboard.'+name:undefined;
}
