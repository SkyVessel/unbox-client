//! Windows current-user DPAPI storage. No credentials are written in plaintext.
use std::{fs,path::PathBuf,ptr};
use sha2::{Digest,Sha256};
use windows_sys::Win32::{Foundation::LocalFree,Security::Cryptography::{CryptProtectData,CryptUnprotectData,CRYPT_INTEGER_BLOB,CRYPTPROTECT_UI_FORBIDDEN}};

fn path(service:&str,name:&str)->Result<PathBuf,String>{
    let root=std::env::var_os("LOCALAPPDATA").ok_or("Windows local app data is unavailable")?;
    let key=format!("{:x}",Sha256::digest(format!("{service}\0{name}").as_bytes()));
    Ok(PathBuf::from(root).join("Unbox/credentials").join(format!("{key}.dpapi")))
}
fn crypt(bytes:&[u8],context:&str,encrypt:bool)->Result<Vec<u8>,String>{
    let input=CRYPT_INTEGER_BLOB{cbData:bytes.len().try_into().map_err(|_|"Account data is too large")?,pbData:bytes.as_ptr() as *mut u8};
    let entropy=CRYPT_INTEGER_BLOB{cbData:context.len() as u32,pbData:context.as_ptr() as *mut u8};
    let mut output=CRYPT_INTEGER_BLOB::default();
    // DPAPI owns the output allocation; copy it before releasing with LocalFree.
    // No LOCAL_MACHINE flag: only the current Windows user can decrypt this vault.
    let ok=unsafe{if encrypt{CryptProtectData(&input,ptr::null(),&entropy,ptr::null(),ptr::null(),CRYPTPROTECT_UI_FORBIDDEN,&mut output)}else{CryptUnprotectData(&input,ptr::null_mut(),&entropy,ptr::null(),ptr::null(),CRYPTPROTECT_UI_FORBIDDEN,&mut output)}};
    if ok==0{return Err("Windows could not access encrypted account storage. Use the original Windows account and retry.".into())}
    let result=if output.cbData==0{Vec::new()}else{unsafe{std::slice::from_raw_parts(output.pbData,output.cbData as usize).to_vec()}};
    unsafe{LocalFree(output.pbData as _);}
    Ok(result)
}
pub fn read(service:&str,name:&str)->Result<Option<Vec<u8>>,String>{
    match fs::read(path(service,name)?){Ok(bytes)=>crypt(&bytes,&format!("{service}\0{name}"),false).map(Some),Err(e) if e.kind()==std::io::ErrorKind::NotFound=>Ok(None),Err(_)=>Err("Could not read Windows account storage".into())}
}
pub fn write(service:&str,name:&str,bytes:&[u8])->Result<(),String>{
    let encrypted=crypt(bytes,&format!("{service}\0{name}"),true)?;
    crate::atomic(&path(service,name)?,&encrypted)
}
#[cfg(test)]mod tests{
    use super::*;
    #[test]fn dpapi_roundtrip_rejects_tampering_and_other_context(){
        let plain=b"test account secret";let mut encoded=crypt(plain,"unbox-test",true).unwrap();
        assert!(!encoded.windows(plain.len()).any(|s|s==plain));
        assert_eq!(crypt(&encoded,"unbox-test",false).unwrap(),plain);
        assert!(crypt(&encoded,"other-account",false).is_err());
        let last=encoded.len()-1;encoded[last]^=1;assert!(crypt(&encoded,"unbox-test",false).is_err());
    }
    #[test]fn encrypted_store_creates_reloads_and_replaces(){
        let name=uuid::Uuid::new_v4().to_string();let service="dev.unbox.tests";
        assert!(read(service,&name).unwrap().is_none());
        write(service,&name,b"first").unwrap();assert_eq!(read(service,&name).unwrap().unwrap(),b"first");
        write(service,&name,b"replacement").unwrap();assert_eq!(read(service,&name).unwrap().unwrap(),b"replacement");
        fs::remove_file(path(service,&name).unwrap()).unwrap();
    }
}
