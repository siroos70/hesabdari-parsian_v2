use sha2::{Digest, Sha256};

/// Compute SHA-256 checksum of data.
///
/// Returns a 64-character hexadecimal string.
pub fn compute_checksum(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    let result = hasher.finalize();
    hex_encode(&result)
}

/// Verify SHA-256 checksum of data.
///
/// Returns `true` if the computed checksum matches the expected value.
/// Uses constant-time comparison to prevent timing attacks.
pub fn verify_checksum(data: &[u8], expected: &str) -> bool {
    let computed = compute_checksum(data);
    constant_time_eq(computed.as_bytes(), expected.as_bytes())
}

/// Encode bytes as hexadecimal string.
fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

/// Constant-time string comparison to prevent timing attacks.
fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut result = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        result |= x ^ y;
    }
    result == 0
}

#[cfg(test)]
mod tests {
    use super::*;

    // =====================================================================
    // Checksum tests
    // =====================================================================

    #[test]
    fn test_compute_checksum_deterministic() {
        let data = b"hello world";
        let c1 = compute_checksum(data);
        let c2 = compute_checksum(data);
        assert_eq!(c1, c2);
    }

    #[test]
    fn test_compute_checksum_length() {
        let checksum = compute_checksum(b"test");
        assert_eq!(checksum.len(), 64); // SHA-256 = 32 bytes = 64 hex chars
    }

    #[test]
    fn test_verify_checksum_correct() {
        let data = b"important data";
        let checksum = compute_checksum(data);
        assert!(verify_checksum(data, &checksum));
    }

    #[test]
    fn test_verify_checksum_incorrect() {
        let data = b"important data";
        let wrong_checksum = "0000000000000000000000000000000000000000000000000000000000000000";
        assert!(!verify_checksum(data, wrong_checksum));
    }

    #[test]
    fn test_verify_checksum_wrong_length() {
        let data = b"test";
        assert!(!verify_checksum(data, "tooshort"));
    }

    // =====================================================================
    // Edge cases
    // =====================================================================

    #[test]
    fn test_constant_time_eq_equal() {
        assert!(constant_time_eq(b"hello", b"hello"));
    }

    #[test]
    fn test_constant_time_eq_different() {
        assert!(!constant_time_eq(b"hello", b"world"));
    }

    #[test]
    fn test_constant_time_eq_different_lengths() {
        assert!(!constant_time_eq(b"hello", b"hello world"));
    }
}
