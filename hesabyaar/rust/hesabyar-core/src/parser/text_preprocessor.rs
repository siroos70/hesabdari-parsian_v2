/// Normalize Persian and Arabic characters to standard digits and clean text.
#[uniffi::export]
pub fn preprocess_persian_text(text: &str) -> String {
    to_ascii_digits(text)
        .replace("\u{060C}", ",")
        .replace("\u{066B}", ".")
        .replace("\u{061B}", ";")
        .replace("\u{061F}", "?")
        .replace('\u{200C}', " ")
        .split_whitespace()
        .collect::<Vec<&str>>()
        .join(" ")
}

/// Normalize money text by removing thousands separators and fixing spacing.
#[uniffi::export]
pub fn normalize_money_text(text: &str) -> String {
    let result = text.replace("\u{066C}", "").replace(',', "");
    // Replace " و " with single space
    let parts: Vec<&str> = result.split(" \u{0648} ").collect();
    parts.join(" ").trim().to_string()
}

/// Convert Persian/Arabic digits to ASCII digits.
pub fn to_ascii_digits(text: &str) -> String {
    text.replace('\u{06F0}', "0")
        .replace('\u{06F1}', "1")
        .replace('\u{06F2}', "2")
        .replace('\u{06F3}', "3")
        .replace('\u{06F4}', "4")
        .replace('\u{06F5}', "5")
        .replace('\u{06F6}', "6")
        .replace('\u{06F7}', "7")
        .replace('\u{06F8}', "8")
        .replace('\u{06F9}', "9")
        .replace('\u{0660}', "0")
        .replace('\u{0661}', "1")
        .replace('\u{0662}', "2")
        .replace('\u{0663}', "3")
        .replace('\u{0664}', "4")
        .replace('\u{0665}', "5")
        .replace('\u{0666}', "6")
        .replace('\u{0667}', "7")
        .replace('\u{0668}', "8")
        .replace('\u{0669}', "9")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_preprocess_persian_text() {
        assert_eq!(preprocess_persian_text("۵۰۰ هزار"), "500 هزار");
        // Persian comma (U+060C) is normalized to ASCII comma
        assert_eq!(preprocess_persian_text("سلام  ،  خوبی"), "سلام , خوبی");
    }

    #[test]
    fn test_to_ascii_digits() {
        assert_eq!(to_ascii_digits("۵۰۰"), "500");
        assert_eq!(to_ascii_digits("١٢٣"), "123");
        assert_eq!(to_ascii_digits("123"), "123");
    }

    #[test]
    fn test_normalize_money_text() {
        // normalize_money_text only removes thousands separators; it does NOT convert digits.
        // Digit conversion is handled separately by to_ascii_digits().
        assert_eq!(normalize_money_text("۱٬۰۰۰"), "۱۰۰۰");
        assert_eq!(normalize_money_text("۱,۰۰۰"), "۱۰۰۰");
        assert_eq!(normalize_money_text("1,000"), "1000");
        assert_eq!(normalize_money_text("۱۲۳٬۴۵۶"), "۱۲۳۴۵۶");
    }
}
