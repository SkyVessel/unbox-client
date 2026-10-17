pub const SUPPORTED: &[&str] = &["26.1", "26.1.2", "26.3"];
pub fn supported(version: &str) -> bool { SUPPORTED.contains(&version) }
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
    format!("Unbox shared {} {}", if loader == "neoforge" { "NeoForge" } else { "Fabric" }, version)
}
#[cfg(test)]mod tests {
    use super::*;
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
