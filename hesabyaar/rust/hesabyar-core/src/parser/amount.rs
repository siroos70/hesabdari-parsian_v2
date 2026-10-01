use super::money_detector::contains_money;
use super::text_preprocessor::{normalize_money_text, to_ascii_digits};

#[derive(Debug, Clone, PartialEq)]
pub enum Token {
    Number(f64),
    Unit(UnitType),
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum UnitType {
    Billion,
    Million,
    Thousand,
    Tuman,
}

impl UnitType {
    pub fn multiplier(self) -> i64 {
        match self {
            Self::Billion => 1_000_000_000,
            Self::Million => 1_000_000,
            Self::Thousand => 1_000,
            Self::Tuman => 1,
        }
    }

    pub fn lower(self) -> Option<UnitType> {
        match self {
            Self::Billion => Some(Self::Million),
            Self::Million => Some(Self::Thousand),
            Self::Thousand => Some(Self::Tuman),
            Self::Tuman => None,
        }
    }
}

const UNIT_WORDS: &[(&str, UnitType)] = &[
    (
        "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0648}\u{0646} \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}",
        UnitType::Million,
    ), // میلیون تومان
    (
        "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0627}\u{0631}\u{062F}",
        UnitType::Billion,
    ), // میلیارد
    (
        "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0648}\u{0646}",
        UnitType::Million,
    ), // میلیون
    (
        "\u{0645}\u{0644}\u{06CC}\u{0648}\u{0646}",
        UnitType::Million,
    ), // ملیون
    ("\u{0647}\u{0632}\u{0627}\u{0631}", UnitType::Thousand), // هزار
    ("\u{062A}\u{0648}\u{0645}\u{0627}\u{0646}", UnitType::Tuman), // تومان
    ("\u{062A}\u{0648}\u{0645}\u{0646}", UnitType::Tuman),    // تومن
];

fn tokenize(text: &str) -> Vec<Token> {
    let mut tokens = Vec::new();
    let chars: Vec<char> = text.chars().collect();
    let mut i = 0;

    while i < chars.len() {
        if chars[i].is_whitespace() {
            i += 1;
            continue;
        }

        if chars[i].is_ascii_digit() {
            let start = i;
            while i < chars.len() && chars[i].is_ascii_digit() {
                i += 1;
            }
            // Consume optional decimal fraction
            if i < chars.len() && chars[i] == '.' {
                let frac_start = i + 1;
                i += 1;
                while i < chars.len() && chars[i].is_ascii_digit() {
                    i += 1;
                }
                if i == frac_start {
                    // No digits after decimal, rewind past the dot
                    i = frac_start - 1;
                }
            }
            let num_str: String = chars[start..i].iter().collect();
            if let Ok(num) = num_str.parse::<f64>() {
                tokens.push(Token::Number(num));
            }
            continue;
        }

        // Try to match unit words
        let remaining: String = chars[i..].iter().collect();
        let mut matched = false;
        for (word, unit_type) in UNIT_WORDS {
            if remaining.starts_with(word) {
                tokens.push(Token::Unit(*unit_type));
                i += word.chars().count();
                matched = true;
                break;
            }
        }
        if !matched {
            i += 1;
        }
    }

    tokens
}

fn interpret_with_units(tokens: &[Token]) -> i64 {
    let mut total: f64 = 0.0;
    let mut current_num: f64 = 0.0;
    let mut last_unit: Option<UnitType> = None;

    for token in tokens {
        match token {
            Token::Number(n) => current_num = *n,
            Token::Unit(u) => {
                if current_num > 0.0 {
                    total += current_num * u.multiplier() as f64;
                }
                last_unit = Some(*u);
                current_num = 0.0;
            }
        }
    }

    if current_num > 0.0 {
        let multiplier = last_unit
            .and_then(|u| u.lower())
            .map(|u| u.multiplier())
            .unwrap_or(1);
        total += current_num * multiplier as f64;
    }

    // Round (not truncate) so float imprecision on large Rial totals does not
    // silently drop a single unit.
    total.round() as i64
}

fn interpret_shorthand(tokens: &[Token]) -> i64 {
    let numbers: Vec<f64> = tokens
        .iter()
        .filter_map(|t| {
            if let Token::Number(n) = t {
                Some(*n)
            } else {
                None
            }
        })
        .collect();

    if numbers.is_empty() {
        return 0;
    }
    if numbers.len() == 1 {
        return numbers[0] as i64;
    }

    let unit_steps = [
        UnitType::Billion.multiplier() as f64,
        UnitType::Million.multiplier() as f64,
        UnitType::Thousand.multiplier() as f64,
    ];
    // `numbers.len() <= 3` is the supported shorthand shape: the first number
    // maps to the largest remaining unit. For >3 numbers we clamp the unit
    // index so we never index out of bounds (and never underflow `3 - len`).
    let start_idx = (3_usize).saturating_sub(numbers.len());

    let mut total: f64 = 0.0;
    for (i, num) in numbers.iter().enumerate() {
        let idx = (start_idx + i).min(unit_steps.len() - 1);
        total += num * unit_steps[idx];
    }
    // Round (not truncate) so float imprecision does not drop a single unit.
    total.round() as i64
}

fn interpret_bare_last(tokens: &[Token]) -> i64 {
    tokens
        .iter()
        .rev()
        .find_map(|t| {
            if let Token::Number(n) = t {
                Some(*n as i64)
            } else {
                None
            }
        })
        .unwrap_or(0)
}

/// Parse a Persian amount sentence and return the amount in Toman.
#[uniffi::export]
pub fn parse_amount(sentence: &str, shorthand_mode: bool) -> i64 {
    if !contains_money(sentence) {
        return 0;
    }

    let normalized = normalize_money_text(sentence);
    let cleaned = normalized.replace(" \u{0648} ", " ");
    let ascii = to_ascii_digits(&cleaned);
    let tokens = tokenize(&ascii);

    if tokens.is_empty() {
        return 0;
    }

    let has_units = tokens.iter().any(|t| matches!(t, Token::Unit(_)));
    if has_units {
        interpret_with_units(&tokens)
    } else if shorthand_mode {
        interpret_shorthand(&tokens)
    } else {
        interpret_bare_last(&tokens)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_with_units() {
        assert_eq!(parse_amount("5 میلیون تومان", true), 5_000_000);
        assert_eq!(parse_amount("450 هزار تومن", true), 450_000);
        assert_eq!(parse_amount("2 میلیارد", true), 2_000_000_000);
    }

    #[test]
    fn test_parse_shorthand() {
        // parse_amount requires a money keyword to pass contains_money() gate
        assert_eq!(parse_amount("۵۰۰ تومن", true), 500);
        assert_eq!(parse_amount("۵۰۰۰۰۰ تومان", true), 500_000);
        // Bare numbers without money keywords return 0
        assert_eq!(parse_amount("۵۰۰", true), 0);
    }

    #[test]
    fn test_parse_no_money() {
        assert_eq!(parse_amount("سلام دنیا", true), 0);
    }
}
