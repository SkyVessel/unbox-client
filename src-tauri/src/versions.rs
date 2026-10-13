pub const SUPPORTED: &[&str] = &["26.1", "26.1.2"];
pub fn supported(version: &str) -> bool { SUPPORTED.contains(&version) }
pub fn neoforge(version: &str) -> (&'static str, &'static str) {
    if version == "26.1.2" { ("26.1.2.116", "3ba299fd382b6257a6a84f291077518844046bde") }
    else { ("26.1.0.19-beta", "7d9f928b8d34fcbb1ea7bad30f6c4849bab4aef7") }
}
pub fn shared_namespace(loader: &str, version: &str) -> String {
    format!("Unbox shared {} {}", if loader == "neoforge" { "NeoForge" } else { "Fabric" }, version)
}
