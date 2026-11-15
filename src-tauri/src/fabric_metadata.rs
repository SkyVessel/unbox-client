use serde_json::Value;
use std::io::Read;

/// Fabric accepts literal newlines in quoted descriptions (e.g. BetterEnd).
/// Normalize only those string characters; keep structural JSON validation strict.
pub(crate) fn read(reader: impl Read) -> Result<Value, String> {
    let mut bytes = Vec::new();
    reader.take(262145).read_to_end(&mut bytes).map_err(|_| "Cannot read Fabric metadata")?;
    if bytes.len() > 262144 { return Err("Mod metadata is too large".into()); }
    let text = std::str::from_utf8(&bytes).map_err(|_| "Invalid Fabric metadata encoding")?;
    let mut normalized = String::with_capacity(text.len());
    let (mut quoted, mut escaped) = (false, false);
    for c in text.chars() {
        if escaped { normalized.push(c); escaped = false; continue; }
        if quoted && c == '\\' { normalized.push(c); escaped = true; continue; }
        if c == '"' { quoted = !quoted; }
        if quoted && c < ' ' {
            use std::fmt::Write;
            write!(normalized, "\\u{:04x}", c as u32).unwrap();
        } else { normalized.push(c); }
    }
    serde_json::from_str(&normalized).map_err(|_| "Invalid Fabric mod metadata".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn loader_compatible_descriptions_keep_fields_and_escapes() {
        let raw = b"{\"id\":\"betterend\",\"description\":\"First line\nSecond\tline\",\"path\":\"a\\\\b\",\"quote\":\"a\\\"b\"}";
        assert!(serde_json::from_slice::<Value>(raw).is_err());
        let value = read(raw.as_slice()).unwrap();
        assert_eq!(value["id"], "betterend");
        assert_eq!(value["description"], "First line\nSecond\tline");
        assert_eq!(value["path"], "a\\b");
        assert_eq!(value["quote"], "a\"b");
        for invalid in [b"{\"id\":\"broken\"".as_slice(), b"{\"id\":\"a\",oops}", b"{\"id\":\"a\"}\x01", b"{\"id\":\"\\q\"}"] {
            assert!(read(invalid).is_err());
        }
        assert!(read(vec![b' '; 262145].as_slice()).is_err());
    }
}
