//! Process-local copies of successfully unlocked credentials; disk storage stays encrypted.
use std::{collections::HashMap,sync::{Mutex,OnceLock}};

#[derive(Default)]
struct Cache(HashMap<(String,String),Option<Vec<u8>>>);
impl Cache{
 fn read(&mut self,service:&str,name:&str,load:impl FnOnce()->Result<Option<Vec<u8>>,String>)->Result<Option<Vec<u8>>,String>{
  let key=(service.into(),name.into());
  if let Some(value)=self.0.get(&key){return Ok(value.clone())}
  let value=load()?;self.0.insert(key,value.clone());Ok(value)
 }
 fn write(&mut self,service:&str,name:&str,value:&[u8],save:impl FnOnce()->Result<(),String>)->Result<(),String>{
  let key=(service.into(),name.into());
  if self.0.get(&key).and_then(Option::as_deref)==Some(value){return Ok(())}
  save()?;self.0.insert(key,Some(value.to_vec()));Ok(())
 }
}
static CACHE:OnceLock<Mutex<Cache>>=OnceLock::new();
pub fn read(service:&str,name:&str,load:impl FnOnce()->Result<Option<Vec<u8>>,String>)->Result<Option<Vec<u8>>,String>{
 CACHE.get_or_init(Default::default).lock().map_err(|_|"Credential cache unavailable")?.read(service,name,load)
}
pub fn write(service:&str,name:&str,value:&[u8],save:impl FnOnce()->Result<(),String>)->Result<(),String>{
 CACHE.get_or_init(Default::default).lock().map_err(|_|"Credential cache unavailable")?.write(service,name,value,save)
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn successful_unlock_is_reused_and_unchanged_credentials_are_not_written(){
  let mut c=Cache::default();assert_eq!(c.read("accounts","vault",||Ok(Some(vec![1]))).unwrap(),Some(vec![1]));
  assert_eq!(c.read("accounts","vault",||panic!("must not prompt again")).unwrap(),Some(vec![1]));
  c.write("accounts","vault",&[1],||panic!("unchanged write")).unwrap();
  c.write("accounts","vault",&[2],||Ok(())).unwrap();
  assert_eq!(c.read("accounts","vault",||panic!()).unwrap(),Some(vec![2]));
  assert_eq!(c.read("friends","vault",||Ok(Some(vec![3]))).unwrap(),Some(vec![3]));
 }
 #[test]fn failed_unlock_or_write_does_not_replace_saved_credentials(){
  let mut c=Cache::default();assert!(c.read("a","b",||Err("denied".into())).is_err());
  c.read("a","b",||Ok(Some(vec![1]))).unwrap();
  assert!(c.write("a","b",&[2],||Err("denied".into())).is_err());
  assert_eq!(c.read("a","b",||panic!()).unwrap(),Some(vec![1]));
 }
}
