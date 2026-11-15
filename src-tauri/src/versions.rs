pub const SUPPORTED: &[&str] = &["26.1", "26.1.2", "26.3"];
pub fn supported(version: &str) -> bool { SUPPORTED.contains(&version) }
pub fn channel(profile:&serde_json::Value)->&str{profile["channel"].as_str().unwrap_or("stable")}
pub fn profile_supported(version:&str,loader:&str,channel:&str)->bool{
    match channel {
        "stable"=>supported(version)&&["fabric","neoforge","vanilla"].contains(&loader),
        "beta"=>matches!((version,loader),("26.3","fabric"|"neoforge")|("1.21.11","fabric")|("1.8.9","forge")),
        _=>false,
    }
}
pub fn profile_resource(profile:&serde_json::Value)->Result<&'static str,String>{
    let version=profile["version"].as_str().unwrap_or("");let loader=profile["loader"].as_str().unwrap_or("");
    if !profile_supported(version,loader,channel(profile)){return Err("Unsupported profile environment".into())}
    if channel(profile)=="beta"{return match(version,loader){
        ("26.3","fabric")=>Ok("resources/unbox-client-26.3-beta.jar"),
        ("26.3","neoforge")=>Ok("resources/unbox-client-neoforge-26.3-beta.jar"),
        ("1.21.11","fabric")=>Ok("resources/unbox-client-1.21.11-beta.jar"),
        ("1.8.9","forge")=>Ok("resources/unpvp-forge-1.8.9.jar"),
        _=>Err("Unsupported beta profile".into()),
    }}
    client_resource(loader,version)
}
pub fn channel_namespace(loader:&str,version:&str,channel:&str)->String{
    let base=shared_namespace(loader,version);if channel=="beta"{format!("{base} beta")}else{base}
}
/// Native shared-file preparation also supports the development Fabric backport.
/// This does not expose unverified profile creation or other backport loaders.
pub fn shared_supported(loader: &str, version: &str) -> bool {
    (supported(version) && matches!(loader, "fabric" | "neoforge")) || (loader == "fabric" && version == "1.21.11")
}
pub fn java_major(version: &str) -> Option<u32> {
    match version {
        "26.1" | "26.1.2" | "26.3" => Some(25),
        "1.21.11" => Some(21),
        "1.20.1" => Some(17),
        _ => None,
    }
}
pub fn neoforge(version: &str) -> Option<(&'static str, &'static str)> {
    match version {
        "26.1" => Some(("26.1.0.19-beta", "7d9f928b8d34fcbb1ea7bad30f6c4849bab4aef7")),
        "26.1.2" => Some(("26.1.2.116", "3ba299fd382b6257a6a84f291077518844046bde")),
        "26.3" => Some(("26.3.0.86", "059fef6105e0cd57dd0fcd2bac07a38c2267b18d")),
        _ => None,
    }
}
pub fn client_resource(loader: &str, version: &str) -> Result<&'static str, String> {
    match (loader, version) {
        ("fabric", "26.1") => Ok("resources/unbox-client.jar"),
        ("fabric", "26.1.2") => Ok("resources/unbox-client-26.1.2.jar"),
        ("fabric", "26.3") => Ok("resources/unbox-client-26.3.jar"),
        ("neoforge", "26.1") => Ok("resources/unbox-client-neoforge.jar"),
        ("neoforge", "26.1.2") => Ok("resources/unbox-client-neoforge-26.1.2.jar"),
        ("neoforge", "26.3") => Ok("resources/unbox-client-neoforge-26.3.jar"),
        _ => Err("Unsupported native client environment".into()),
    }
}
pub fn shared_namespace(loader: &str, version: &str) -> String {
    format!("Unbox shared {} {}", match loader {"neoforge"=>"NeoForge","forge"=>"Forge",_=>"Fabric"}, version)
}
#[cfg(test)]mod tests {
    use super::*;
    #[test]fn shared_fabric_backport_is_loader_scoped() {
        assert!(shared_supported("fabric","1.21.11"));
        assert!(!shared_supported("neoforge","1.21.11"));
        assert!(!shared_supported("fabric","1.20.1"));
        assert!(!shared_supported("forge","26.3"));
    }
    #[test]fn backport_java_requirements_do_not_enable_unverified_profiles() {
        for version in SUPPORTED { assert_eq!(java_major(version),Some(25)); }
        assert_eq!(java_major("1.21.11"),Some(21));
        assert_eq!(java_major("1.20.1"),Some(17));
        assert_eq!(java_major("1.21"),None);
        for version in ["1.21.11","1.20.1"] {
            assert!(!supported(version));
            assert!(client_resource("fabric",version).is_err());
        }
    }
    #[test]fn native_versions_have_distinct_pinned_resources_and_shared_profiles() {
        let mut resources=std::collections::HashSet::new();
        let mut namespaces=std::collections::HashSet::new();
        for version in SUPPORTED {
            assert!(supported(version));
            let (neo,sha)=neoforge(version).unwrap();assert!(neo.starts_with(version));assert_eq!(sha.len(),40);assert!(sha.bytes().all(|b|b.is_ascii_hexdigit()));
            for loader in ["fabric","neoforge"] {
                assert!(resources.insert(client_resource(loader,version).unwrap()));
                assert!(namespaces.insert(shared_namespace(loader,version)));
            }
        }
        assert_eq!(neoforge("26.3").unwrap(),("26.3.0.86","059fef6105e0cd57dd0fcd2bac07a38c2267b18d"));
        for version in ["26.2","26.3.1",""] {assert!(!supported(version));assert!(neoforge(version).is_none());assert!(client_resource("fabric",version).is_err());}
        assert!(client_resource("forge","26.3").is_err());
    }
}

#[cfg(test)]mod beta_tests{
 use super::*;use serde_json::json;
 #[test]fn beta_is_opt_in_and_old_profiles_select_published_modules(){
  for loader in ["fabric","neoforge"]{
   let mut p=json!({"version":"26.3","loader":loader});let stable=profile_resource(&p).unwrap();assert_eq!(stable,client_resource(loader,"26.3").unwrap());
   p["channel"]=json!("beta");assert_ne!(profile_resource(&p).unwrap(),stable);assert!(profile_resource(&p).unwrap().contains("beta"));
   assert_ne!(channel_namespace(loader,"26.3","beta"),channel_namespace(loader,"26.3","stable"));
  }
  assert!(!profile_supported("1.21.11","fabric","stable"));assert!(profile_supported("1.21.11","fabric","beta"));
  assert!(!profile_supported("1.21.11","neoforge","beta"));assert!(profile_supported("1.8.9","forge","beta"));assert!(!profile_supported("1.8.9","fabric","beta"));assert!(!profile_supported("1.20.1","fabric","beta"));
 }
}
