const UNIT_WORDS: &[&str] = &[
    "\u{0647}\u{0632}\u{0627}\u{0631}",                         // هزار
    "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0648}\u{0646}",         // میلیون
    "\u{0645}\u{0644}\u{06CC}\u{0648}\u{0646}",                 // ملیون
    "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0627}\u{0631}\u{062F}", // میلیارد
    "\u{062A}\u{0648}\u{0645}\u{0627}\u{0646}",                 // تومان
    "\u{062A}\u{0648}\u{0645}\u{0646}",                         // تومن
];

const CONTEXT_KEYWORDS: &[&str] = &[
    "\u{062E}\u{0631}\u{06CC}\u{062F}\u{0645}", // خریدم
    "\u{062E}\u{0631}\u{06CC}\u{062F}",         // خرید
    "\u{067E}\u{0631}\u{062F}\u{0627}\u{062E}\u{062A}", // پرداخت
    "\u{0647}\u{0632}\u{06CC}\u{0646}\u{0647}", // هزینه
    "\u{062E}\u{0631}\u{062C}",                 // خرج
    "\u{062F}\u{0627}\u{062F}\u{0645}",         // دادم
    "\u{06AF}\u{0631}\u{0641}\u{062A}\u{0645}", // گرفتم
    "\u{062D}\u{0642}\u{0648}\u{0642}",         // حقوق
    "\u{062F}\u{0631}\u{0622}\u{0645}\u{062F}", // درآمد
    "\u{0642}\u{0631}\u{0636}",                 // قرض
    "\u{0648}\u{0627}\u{0645}",                 // وام
    "\u{0642}\u{0633}\u{0637}",                 // قسط
    "\u{0648}\u{0627}\u{0631}\u{06CC}\u{0632}", // واریز
    "\u{0628}\u{0627}\u{0646}\u{06A9}",         // بانک
    "\u{0641}\u{0631}\u{0648}\u{0634}",         // فروش
    "\u{067E}\u{0648}\u{0644}",                 // پول
    "\u{0645}\u{0628}\u{0644}\u{063A}",         // مبلغ
    "\u{0642}\u{06CC}\u{0645}\u{062A}",         // قیمت
    "\u{0627}\u{0631}\u{0632}\u{0627}\u{0646}", // ارزان
    "\u{06AF}\u{0631}\u{0627}\u{0646}",         // گران
];

/// Check if a sentence contains money-related keywords.
#[uniffi::export]
pub fn contains_money(sentence: &str) -> bool {
    let normalized = sentence.trim();
    UNIT_WORDS.iter().any(|&w| normalized.contains(w))
        || CONTEXT_KEYWORDS.iter().any(|&w| normalized.contains(w))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_contains_money_with_amount() {
        assert!(contains_money("500 هزار تومن"));
        assert!(contains_money("۲۰ میلیون تومان"));
        assert!(contains_money("خریدم چیزی"));
    }

    #[test]
    fn test_does_not_contain_money() {
        assert!(!contains_money("سلام دنیا"));
        assert!(!contains_money("خوبی؟"));
    }
}
