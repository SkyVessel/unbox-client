//! Dependencies for explicitly selected beta profiles. Stable profiles never enter this path.
use crate::{Runtime,atomic,launcher::{download,phase,safe_path}};
use serde_json::Value;
#[cfg(test)]use serde_json::json;
use sha2::{Digest,Sha512};
use std::{fs,path::Path};
fn valid(path:&Path,e:&Value)->bool{
    fs::symlink_metadata(path).is_ok_and(|m|m.is_file()&&!m.file_type().is_symlink())&&fs::read(path).is_ok_and(|b|format!("{:x}",Sha512::digest(b))==e["sha512"].as_str().unwrap_or(""))
}
fn destination(mods:&Path,e:&Value)->Result<std::path::PathBuf,String>{
    let name=e["filename"].as_str().ok_or("Missing beta dependency filename")?;
    let active=safe_path(mods,name)?;let disabled=safe_path(mods,&format!("{name}.disabled"))?;
    if active.exists()&&disabled.exists(){return Err(format!("Both enabled and disabled copies of {name} exist. Preserve one before retrying."))}
    let path=if active.exists(){active}else if disabled.exists()||e["defaultEnabled"]==false{disabled}else{active};
    if path.exists()&&!valid(&path,e){return Err(format!("{name} differs from the pinned beta version. Your file was preserved."))}Ok(path)
}
pub async fn prepare(rt:&Runtime,client:&reqwest::Client,game:&Path)->Result<(),String>{
    let entries:Vec<Value>=serde_json::from_str(include_str!("../resources/beta-mods-1.21.11.json")).map_err(|e|e.to_string())?;
    let mods=game.join("mods");fs::create_dir_all(&mods).map_err(|e|e.to_string())?;
    for e in &entries{destination(&mods,e)?;}
    // A synchronized hash-named copy may supply an identical library. Reject conflicting IDs.
    let mut existing=std::collections::HashMap::new();
    for f in fs::read_dir(&mods).map_err(|e|e.to_string())?.flatten(){let p=f.path();if p.extension().is_some_and(|e|e=="jar"){
        for id in crate::launcher::jar_mod_ids(&p)?{existing.insert(id,p.clone());}
    }}
    let mut prepared=Vec::new();
    for e in &entries{
        let dest=destination(&mods,e)?;
        if valid(&dest,e){prepared.push((e.clone(),dest));continue}
        let hash=e["sha512"].as_str().ok_or("Missing beta checksum")?;
        if hash.len()!=128||!hash.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid beta checksum".into())}
        let cache=rt.root.join("runtime/beta-mods").join(format!("{hash}.jar"));
        phase(rt,&format!("Preparing Beta · {}",e["slug"].as_str().unwrap_or("mod")),88);
        if !valid(&cache,e){
            if let Some(name)=e["resource"].as_str(){
                let source=safe_path(&rt.resources.join("resources"),name)?;
                if !valid(&source,e){return Err(format!("Bundled beta checksum failed: {name}"))}
                atomic(&cache,&fs::read(source).map_err(|e|e.to_string())?)?;
            }else{download(client,e["url"].as_str().ok_or("Missing beta download")?,&cache,e["sha1"].as_str()).await?;}
            if !valid(&cache,e){return Err("Beta dependency checksum mismatch".into())}
        }
        let mut reused=None;
        for id in crate::launcher::jar_mod_ids(&cache)?{if let Some(p)=existing.get(&id){
            if !valid(p,e){return Err(format!("{} already provides a different {id}; your file was preserved",p.display()))}
            reused=Some(p.clone());
        }}
        if let Some(p)=reused{prepared.push((e.clone(),p));continue}
        prepared.push((e.clone(),dest));
    }
    for (e,dest) in &prepared{if !valid(dest,e){let cache=rt.root.join("runtime/beta-mods").join(format!("{}.jar",e["sha512"].as_str().unwrap()));atomic(dest,&fs::read(cache).map_err(|e|e.to_string())?)?;}}
    atomic(&game.join("unbox-beta-mods.json"),&serde_json::to_vec(&entries).unwrap())?;
    for (name,data) in [("hitreg.properties","renderHitbox=false\nrenderCross=false\nRenderServerHitbox=false\nRenderApproachHitbox=false\nRenderYourReach=false\nRenderTheirReach=false\nRenderYourJump=false\nRenderTheirJump=false\nPerfectHitColor=false\nJumpResetColor=false\n"),("bettertotemcounter.json","{\"popAnimationScale\":0.5}\n")]{let path=game.join("config").join(name);if !path.exists(){atomic(&path,data.as_bytes())?;}}
    Ok(())
}
#[cfg(test)]mod tests{
    use super::*;
    #[test]fn disabled_pvp_files_stay_disabled_and_modified_files_are_preserved(){
        let dir=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());fs::create_dir_all(&dir).unwrap();
        let e=json!({"filename":"test.jar","sha512":format!("{:x}",Sha512::digest(b"mod")),"defaultEnabled":true});
        let disabled=dir.join("test.jar.disabled");fs::write(&disabled,b"mod").unwrap();assert_eq!(destination(&dir,&e).unwrap(),disabled);
        fs::write(&disabled,b"custom").unwrap();assert!(destination(&dir,&e).is_err());assert_eq!(fs::read(&disabled).unwrap(),b"custom");fs::remove_dir_all(dir).unwrap();
    }
}
