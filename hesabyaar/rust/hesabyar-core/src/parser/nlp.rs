use crate::calendar::{gregorian_to_jalali, jalali_to_gregorian};
use crate::models::{ParsedResult, TransactionType};
use crate::parser::amount::parse_amount;
use crate::parser::text_preprocessor::preprocess_persian_text;

const TYPE_EXPENSE: &str = "EXPENSE";
const TYPE_INCOME: &str = "INCOME";
const TYPE_LOAN_DEBTOR: &str = "LOAN_DEBTOR";
const TYPE_LOAN_CREDITOR: &str = "LOAN_CREDITOR";
const TYPE_INSTALLMENT: &str = "INSTALLMENT";

const CATEGORY_FOOD: &str = "Food";
const CATEGORY_TRANSPORTATION: &str = "Transportation";
const CATEGORY_SHOPPING: &str = "Shopping";
const CATEGORY_BILLS: &str = "Bills";
const CATEGORY_INSTALLMENTS: &str = "Installments";
const CATEGORY_LOANS: &str = "Loans";
const CATEGORY_INCOME: &str = "Income";
const CATEGORY_OTHER: &str = "Other";

// Mirrors the full Kotlin category taxonomy (GeminiParser categoryKeywords).
// The offline parser (infer_expense_category_full) CAN infer these categories,
// but normalize_category collapses them to "Other" in the offline pipeline.
// Kept as documentation of the complete taxonomy.
#[allow(dead_code)]
mod unused_categories {
    pub const CATEGORY_PERSONAL_CARE: &str = "Personal Care";
    pub const CATEGORY_EDUCATION: &str = "Education";
    pub const CATEGORY_RENT_UTILITIES: &str = "Rent & Utilities";
    pub const CATEGORY_LOANS_DEBT: &str = "Loans & Debt";
    pub const CATEGORY_EVENTS_GIFTS: &str = "Events & Gifts";
    pub const CATEGORY_CHARITY: &str = "Charity";
    pub const CATEGORY_INVESTMENT: &str = "Investment";
}

#[derive(Debug, Clone)]
struct TypeClassification {
    tx_type: String,
    category: String,
    description: String,
    installment_title: Option<String>,
    days_from_now: Option<i32>,
    notes: Option<String>,
}

/// Full Persian expense category inference with 200+ keywords.
/// Ported from GeminiParser.inferExpenseCategory()
pub fn infer_expense_category_full(sentence: &str) -> (String, String) {
    if contains_any(
        sentence,
        &[
            "مرغ",
            "گوشت",
            "غذا",
            "میوه",
            "رستوران",
            "نان",
            "شیر",
            "پنیر",
            "شام",
            "ناهار",
            "صبحانه",
            "چای",
            "قهوه",
            "اسنک",
            "بستنی",
            "سالاد",
            "ماهی",
            "میگو",
            "سبزی",
            "مربا",
            "روغن",
            "برنج",
            "ماکارونی",
            "رب",
            "ادویه",
            "نوشابه",
            "آب معدنی",
            "آب",
            "دوغ",
            "دلستر",
            "چیپس",
            "شکلات",
            "کیک",
            "بیسکوییت",
            "موز",
            "سیب",
            "پرتقال",
            "هندوانه",
            "خربزه",
            "انگور",
            "توت",
            "تمشک",
            "کدو",
            "خیار",
            "گوجه",
            "کلم",
            "اسفناج",
            "لوبیا",
            "نخود",
            "عدس",
            "لپه",
            "سوپ",
            "آش",
            "حلیم",
            "کباب",
            "استیک",
            "سوسیس",
            "کالباس",
            "همبرگر",
            "پیتزا",
            "ساندویچ",
        ],
    ) {
        return ("Food".to_string(), "خرید مواد غذایی".to_string());
    }

    if contains_any(
        sentence,
        &[
            "بنزین",
            "اسنپ",
            "کرایه",
            "تاکسی",
            "مترو",
            "اتوبوس",
            "بلیط",
            "پارکینگ",
            "عوارض",
            "لنت",
            "لاستیک",
            "تعویض روغن",
            "مکانیک",
            "تعمیرگاه",
        ],
    ) {
        return ("Transportation".to_string(), "هزینه حمل و نقل".to_string());
    }

    if contains_any(
        sentence,
        &[
            "لباس",
            "کفش",
            "پوشاک",
            "کیف",
            "کلاه",
            "عینک",
            "ساعت مچی",
            "جواهرات",
            "زیورآلات",
            "کت",
            "شلوار",
            "پیراهن",
            "مانتو",
            "چادر",
        ],
    ) {
        return ("Shopping".to_string(), "خرید پوشاک و اکسسوری".to_string());
    }

    if contains_any(
        sentence,
        &[
            "قبض",
            "برق",
            "آب",
            "گاز",
            "تلفن",
            "اینترنت",
            "شارژ",
            "فیبر",
            "موبایل",
            "tv",
            "tv اشتراک",
        ],
    ) {
        return ("Bills".to_string(), "پرداخت قبوض و شارژ".to_string());
    }

    if contains_any(
        sentence,
        &[
            "اصلاح",
            "سالن",
            "آرایشگاه",
            "کوتاهی",
            "رنگ مو",
            "واکس",
            "پدیکور",
            "مانیکور",
            "ماساژ",
            "اسپا",
            "فیشال",
            "لیزر",
            "کرم",
            "شامپو",
            "عطر",
            "ادکلن",
            "لوازم آرایش",
            "آرایش",
            "پیرایش",
            "ابرو",
            "ریمل",
            "رژ لب",
            "پودر",
            "کانسیلر",
            "بنز",
            "سیگار",
            "قلیان",
            "قهوه خانه",
            "چایخانه",
            "هتل",
            "اقامت",
            "بلیط هواپیما",
            "بلیط قطار",
            "سفر",
            "گردشگری",
            "تفریح",
            "سینما",
            "تئاتر",
            "کنسرت",
            "بازی",
            "ورزش",
            "باشگاه",
            "fitness",
            "Gym",
            "دارو",
            "داروخانه",
            "ویتامین",
            "درمان",
            "دندانپزشکی",
            "چشم پزشکی",
            "آزمایش",
            "رادیولوژی",
            "سونوگرافی",
            "MRI",
            "CT",
            "تست",
        ],
    ) {
        return (
            "Personal Care".to_string(),
            "هزینه شخصی، آرایشی و بهداشتی".to_string(),
        );
    }

    if contains_any(
        sentence,
        &[
            "کتاب",
            "مجله",
            "روزنامه",
            "دوره آموزشی",
            "کلاس",
            "آموزش",
            "مدرسه",
            "دانشگاه",
            "شهریه",
            "سرویس مدرسه",
            "لوازم تحریر",
            "مداد",
            "خودکار",
            "دفتر",
            "کاغذ",
            "Printer",
            "پرینتر",
            "کارتریج",
            "نرم افزار",
            "اپلیکیشن",
            "اشتراک",
            "سرویس",
            "service",
            "membership",
        ],
    ) {
        return ("Education".to_string(), "هزینه آموزش و تحصیل".to_string());
    }

    if contains_any(
        sentence,
        &[
            "اجاره",
            "رهن",
            "آپارتمان",
            "خانه",
            "ملک",
            "زمین",
            "ویلا",
            "باغ",
            "کلبه",
            "اقامتگاه",
            "هتل",
            "مهمانخانه",
            "پارکینگ",
            "انبار",
            "دفتر کار",
            "مغازه",
            "فروشگاه",
            "بازرگانی",
            "شرکت",
            "کارخانه",
            "کارگاه",
            "بیمه",
            "مالیات",
            "عوارض شهرداری",
            "شارژ آپارتمان",
            "تعمیرات ساختمان",
            "نقاشی ساختمان",
            "لوله کشی",
            "برقکاری",
            "بنایی",
            "سنگ",
            "سیمان",
            "آجر",
            "چوب",
            "MDF",
            "لمینت",
            "سرامیک",
            "کاشی",
            "شیرآلات",
            "شوفاژ",
            "کولر",
            "بخاری",
            "شومینه",
            "پکیج",
            "رادیاتور",
            "لوله",
        ],
    ) {
        return (
            "Rent & Utilities".to_string(),
            "هزینه اجاره، رهن و نگهداری ملک".to_string(),
        );
    }

    if contains_any(
        sentence,
        &[
            "قرض",
            "وام",
            "بدهی",
            "قسط",
            "چک",
            "سفته",
            "ضمانت",
            "سود وام",
            "جریمه",
            "کارمزد",
            "سود بانکی",
            "بهره",
            "سود مرکب",
            "وام مسکن",
            "وام خودرو",
            "وام ازدواج",
            "وام تحصیلی",
            "وام ضربت",
            "وام فوری",
            "وام بازنشستگی",
            "وام کارمندی",
            "وام دولتی",
            "وام خصوصی",
            "وام بانکی",
            "وام بدون بهره",
            "وام با بهره",
            "وام با سود",
            "وام بدون سود",
            "وام با کارمزد",
            "وام بدون کارمزد",
            "وام با ضمانت",
            "وام بدون ضمانت",
            "وام با چک",
            "وام با سفته",
            "وام با ضامن",
            "وام بدون ضامن",
        ],
    ) {
        return ("Loans & Debt".to_string(), "بدهی و وام".to_string());
    }

    if contains_any(
        sentence,
        &[
            "درآمد",
            "حقوق",
            "واریز",
            "اضافه کار",
            "پاداش",
            "بونوس",
            "سود",
            "دریافتی",
            "واریزی",
            "حقوقی",
            "کارانه",
            "فروش",
            "درآمدزایی",
            "حق بیمه",
            "عیدی",
            "سنوات",
            "پرداختی",
            "حقوق ماه",
            "حقوق اداره",
            "حقوق شرکت",
            "حقوقم",
            "حقوقم رو",
            "دریافت کردم",
            "واریز شد",
            "رسید",
            "واریز کرد",
            "فروش رفت",
            "درآمد داشتم",
            "پول درآوردم",
            "سود کردم",
            "بازدهی",
            "return",
            "profit",
        ],
    ) {
        return ("Income".to_string(), "درآمد".to_string());
    }

    if contains_any(
        sentence,
        &[
            "هدیه",
            "جشن",
            "تولد",
            "عروسی",
            "نامزدی",
            "سالگرد",
            "مراسم",
            "مهمانی",
            "party",
            "celebration",
            "event",
            "wedding",
        ],
    ) {
        return ("Events & Gifts".to_string(), "جشن و هدیه".to_string());
    }

    if contains_any(
        sentence,
        &["خیریه", "صدقه", "کمک", "donate", "charity", "philanthropy"],
    ) {
        return ("Charity".to_string(), "خیریه و کمک مالی".to_string());
    }

    if contains_any(
        sentence,
        &[
            "سرمایه گذاری",
            "سرمایه‌گذاری",
            "خرید سهام",
            "صندوق سرمایه",
            "طلا",
            "سکه",
            "دلار",
            "ارز",
            "نفت",
            "گاز",
            "مسکن",
            "زمین",
            "باغ",
            "بیمه عمر",
            "بیمه تصادف",
            "بیمه آتش سوزی",
            "بیمه زلزله",
            "بیمه سرقت",
            "بیمه مسئولیت",
        ],
    ) {
        return ("Investment".to_string(), "سرمایه\u{200C}گذاری".to_string());
    }

    ("Other".to_string(), "سایر هزینه\u{200C}ها".to_string())
}

fn contains_any(text: &str, keywords: &[&str]) -> bool {
    keywords.iter().any(|&kw| text.contains(kw))
}

/// Replace `word` with `rep` only at word boundaries.
///
/// Persian letters and the ZWNJ (U+200C) are treated as word characters, so a
/// filler token that happens to be a substring of a larger word is NOT stripped
/// (e.g. "به" must not be removed from "نوشابه"). Standalone occurrences and
/// multi-word phrases (bounded by spaces) are still replaced.
///
/// Digit-to-letter transitions are treated as word boundaries so that
/// attached amounts like `۵۰۰۰تومان` still have `تومان` removed.
fn replace_word_bounded(s: &str, word: &str, rep: &str) -> String {
    if word.is_empty() {
        return s.to_string();
    }
    let chars: Vec<char> = s.chars().collect();
    let w: Vec<char> = word.chars().collect();
    // `word` is non-empty (guarded above), so `w` always has a last char.
    let Some(last_char) = w.last().copied() else {
        return s.to_string();
    };
    let mut out = String::with_capacity(s.len());
    let mut i = 0;
    while i < chars.len() {
        if i + w.len() <= chars.len() && chars[i..i + w.len()] == w[..] {
            let prev_ok = i == 0 || {
                let prev = chars[i - 1];
                // Digit-to-letter transition counts as a boundary
                !is_word_char(prev)
                    || (is_digit(prev) && is_letter(w[0]))
                    || (is_letter(prev) && is_digit(w[0]))
            };
            let next_ok = i + w.len() == chars.len() || {
                let next = chars[i + w.len()];
                // Letter-to-digit transition counts as a boundary
                !is_word_char(next)
                    || (is_digit(next) && is_letter(last_char))
                    || (is_letter(next) && is_digit(last_char))
            };
            if prev_ok && next_ok {
                out.push_str(rep);
                i += w.len();
                continue;
            }
        }
        out.push(chars[i]);
        i += 1;
    }
    out
}

/// True for characters that bind words together (letters, digits, ZWNJ).
fn is_word_char(c: char) -> bool {
    c.is_alphanumeric() || c == '\u{200C}'
}

/// True for characters that are digits (Persian or ASCII).
fn is_digit(c: char) -> bool {
    c.is_ascii_digit() || ('\u{06F0}'..='\u{06F9}').contains(&c)
}

/// True for characters that are letters (non-digit word chars).
fn is_letter(c: char) -> bool {
    is_word_char(c) && !is_digit(c)
}

/// Convert Persian/Arabic digits (۰-۹) to ASCII digits (0-9)
fn to_ascii_digits(s: &str) -> String {
    s.chars()
        .map(|c| {
            let code = c as u32;
            if (0x06F0..=0x06F9).contains(&code) {
                (b'0' + (code - 0x06F0) as u8) as char
            } else {
                c
            }
        })
        .collect()
}

/// Normalize category: Personal Care, Education, Rent & Utilities, Loans & Debt,
/// Events & Gifts, Charity, Investment are all mapped to "Other" in Kotlin.
fn normalize_category(category: &str) -> String {
    match category {
        CATEGORY_FOOD
        | CATEGORY_TRANSPORTATION
        | CATEGORY_SHOPPING
        | CATEGORY_BILLS
        | CATEGORY_INSTALLMENTS
        | CATEGORY_LOANS
        | CATEGORY_INCOME
        | CATEGORY_OTHER => category.to_string(),
        _ => CATEGORY_OTHER.to_string(),
    }
}

/// Extract subject (meaningful words) from a sentence after removing filler words.
/// Ported from GeminiParser.extractSubject()
fn extract_subject(sentence: &str) -> String {
    let filler_words = [
        "امروز",
        "دیروز",
        "پریروز",
        "فردا",
        "پسفردا",
        "پس فردا",
        "دیشب",
        "شب",
        "صبح",
        "عصر",
        "ظهر",
        "شب قبل",
        "ساعت",
        "نیم",
        "دقیقه",
        "روز",
        "خریدم",
        "خرید",
        "گرفتم",
        "گرفت",
        "دادم",
        "داد",
        "پرداخت",
        "پرداخت کردم",
        "هزینه",
        "خرج",
        "واریز",
        "واریز کردم",
        "فروش",
        "فروختم",
        "فروش رفت",
        "بابت",
        "برای",
        "از",
        "به",
        "تومان",
        "تومن",
        "هزار",
        "میلیون",
        "ملیون",
        "میلیارد",
        "قرض",
        "وام",
    ];

    let mut cleaned = sentence.to_string();
    for word in &filler_words {
        cleaned = replace_word_bounded(&cleaned, word, "");
    }
    cleaned.split_whitespace().collect::<Vec<&str>>().join(" ")
}

/// Classify the type of a Persian sentence.
/// Ported from GeminiParser.classifyType()
fn classify_type(
    sentence: &str,
    is_income: bool,
    person_name: Option<&str>,
    now_ms: i64,
) -> TypeClassification {
    if let Some(c) = classify_installment(sentence, now_ms) {
        return c;
    }
    if let Some(c) = classify_loan(sentence, person_name) {
        return c;
    }
    if is_income {
        return classify_income(sentence);
    }
    classify_expense(sentence)
}

/// Classify installment from sentence.
/// Ported from GeminiParser.classifyInstallment()
fn classify_installment(sentence: &str, now_ms: i64) -> Option<TypeClassification> {
    if !sentence.contains("قسط") {
        return None;
    }

    let installment_title = if sentence.contains("ماشین") {
        Some("قسط ماشین".to_string())
    } else if sentence.contains("خانه") || sentence.contains("مسکن") {
        Some("قسط وام مسکن".to_string())
    } else if sentence.contains("وام") {
        Some("قسط وام".to_string())
    } else {
        Some("قسط جدید".to_string())
    };

    let is_paid = contains_any(sentence, &["پرداخت", "دادم", "تسویه"]);

    if is_paid {
        Some(TypeClassification {
            tx_type: TYPE_EXPENSE.to_string(),
            category: CATEGORY_INSTALLMENTS.to_string(),
            description: format!("پرداخت {}", installment_title.unwrap_or_default()),
            installment_title: None,
            days_from_now: None,
            notes: None,
        })
    } else {
        // Apply relative-day keywords ("فردا"→1, "پس فردا"→2, "دیروز"→-1, "امروز"→0)
        // before falling back to Jalali month/day parsing or the 30-day default.
        // "امروز" (today, offset 0) is now distinguishable from "no keyword found" (None).
        let days_from_now = if let Some(offset) = extract_date_offset(sentence) {
            offset
        } else {
            extract_jalali_days_from_now_inner(sentence, now_ms + TEHRAN_OFFSET_MS)
        };
        Some(TypeClassification {
            tx_type: TYPE_INSTALLMENT.to_string(),
            category: CATEGORY_INSTALLMENTS.to_string(),
            description: "قسط آینده".to_string(),
            installment_title,
            days_from_now: Some(days_from_now),
            notes: Some("قسط در انتظار پرداخت".to_string()),
        })
    }
}

/// Classify loan from sentence.
/// Ported from GeminiParser.classifyLoan()
fn classify_loan(sentence: &str, person_name: Option<&str>) -> Option<TypeClassification> {
    let loan_received = ["قرض گرفتم", "بدهکار شدم", "گرفتم از"];
    if loan_received.iter().any(|&kw| sentence.contains(kw)) {
        return Some(TypeClassification {
            tx_type: TYPE_LOAN_CREDITOR.to_string(),
            category: CATEGORY_LOANS.to_string(),
            description: format!("قرض گرفتن از {}", person_name.unwrap_or("طلبکار")),
            installment_title: None,
            days_from_now: None,
            notes: Some("قرض جدید ثبت شده".to_string()),
        });
    }

    let loan_given = ["قرض دادم", "طلبکار شدم", "دادم به", "طلب دارم"];
    if loan_given.iter().any(|&kw| sentence.contains(kw)) {
        return Some(TypeClassification {
            tx_type: TYPE_LOAN_DEBTOR.to_string(),
            category: CATEGORY_LOANS.to_string(),
            description: format!("قرض دادن به {}", person_name.unwrap_or("بدهکار")),
            installment_title: None,
            days_from_now: None,
            notes: Some("طلب جدید ثبت شده".to_string()),
        });
    }

    None
}

/// Classify income from sentence.
/// Ported from GeminiParser.classifyIncome()
fn classify_income(sentence: &str) -> TypeClassification {
    let subject = extract_subject(sentence);
    let description = if contains_any(sentence, &["اضافه کار", "اضافه\u{200C}کار"])
    {
        "دریافت اضافه کار".to_string()
    } else if sentence.contains("پاداش") {
        "دریافت پاداش".to_string()
    } else if sentence.contains("دستمزد") {
        "دریافت دستمزد".to_string()
    } else if sentence.contains("فروش") {
        format!("درآمد از فروش ({})", subject)
    } else if sentence.contains("سود") {
        "دریافت سود".to_string()
    } else if sentence.contains("حقوق") {
        "دریافت حقوق".to_string()
    } else {
        format!("دریافت درآمد ({})", subject)
    };

    TypeClassification {
        tx_type: TYPE_INCOME.to_string(),
        category: CATEGORY_INCOME.to_string(),
        description,
        installment_title: None,
        days_from_now: None,
        notes: None,
    }
}

/// Classify expense from sentence.
/// Ported from GeminiParser.classifyExpense()
fn classify_expense(sentence: &str) -> TypeClassification {
    let (inferred_category, _) = infer_expense_category_full(sentence);
    let normalized = normalize_category(&inferred_category);
    let subject = extract_subject(sentence);

    let description = if normalized != CATEGORY_OTHER && normalized != inferred_category {
        format!("هزینه متفرقه ({})", subject)
    } else {
        let base_description = match normalized.as_str() {
            "Food" => "خرید مواد غذایی",
            "Transportation" => "هزینه حمل و نقل",
            "Shopping" => "خرید پوشاک و اکسسوری",
            "Bills" => "پرداخت قبوض و شارژ",
            "Personal Care" => "هزینه شخصی",
            "Education" => "هزینه آموزش",
            "Rent & Utilities" => "هزینه اجاره و نگهداری",
            "Loans & Debt" => "بدهی و وام",
            "Income" => "درآمد",
            "Events & Gifts" => "جشن و هدیه",
            "Charity" => "خیریه",
            "Investment" => "سرمایه\u{200C}گذاری",
            _ => "سایر هزینه\u{200C}ها",
        };
        if normalized == CATEGORY_OTHER {
            if subject.is_empty() {
                base_description.to_string()
            } else {
                format!("{} {}", base_description, subject)
            }
        } else {
            format!("{} ({})", base_description, subject)
        }
    };

    TypeClassification {
        tx_type: TYPE_EXPENSE.to_string(),
        category: normalized,
        description,
        installment_title: None,
        days_from_now: None,
        notes: None,
    }
}

/// Extract date offset from Persian sentence.
/// Ported from GeminiParser.extractDateOffset()
pub fn extract_date_offset(sentence: &str) -> Option<i32> {
    if sentence.contains("پریروز") {
        return Some(-2);
    }
    if sentence.contains("دیروز") {
        return Some(-1);
    }
    // Check compact "پسفردا" before "فردا" to avoid false partial match
    if sentence.contains("پسفردا") || sentence.contains("پس فردا") {
        return Some(2);
    }
    if sentence.contains("فردا") {
        return Some(1);
    }
    if sentence.contains("امروز") {
        return Some(0);
    }
    None
}

/// Extract time from Persian sentence.
/// Ported from GeminiParser.extractTime()
pub fn extract_time(sentence: &str) -> (Option<i32>, Option<i32>) {
    let hour_pattern = "ساعت";
    if let Some(pos) = sentence.find(hour_pattern) {
        let rest = sentence[pos + hour_pattern.len()..].trim_start();
        let raw_digits: String = rest
            .chars()
            .take_while(|c| c.is_ascii_digit() || *c as u32 >= 0x06F0 && *c as u32 <= 0x06F9)
            .collect();
        let digits = to_ascii_digits(&raw_digits);
        if let Ok(mut hour) = digits.parse::<i32>() {
            let tail_start = pos + hour_pattern.len();
            let trimmed_prefix = sentence[tail_start..].len() - rest.len();
            let digits_end = tail_start + trimmed_prefix + raw_digits.len();
            let is_pm = is_pm_marker_near_time(sentence, pos, digits_end);
            if is_pm && (1..=11).contains(&hour) {
                hour += 12;
            }
            let minute = if sentence.contains("نیم") {
                Some(30)
            } else {
                let min_pattern = "و";
                if let Some(min_pos) = sentence.find(min_pattern) {
                    let min_rest = sentence[min_pos + min_pattern.len()..].trim_start();
                    let raw_min: String = min_rest
                        .chars()
                        .take_while(|c| {
                            c.is_ascii_digit() || *c as u32 >= 0x06F0 && *c as u32 <= 0x06F9
                        })
                        .collect();
                    let min_clean = to_ascii_digits(&raw_min);
                    min_clean.parse::<i32>().ok()
                } else {
                    None
                }
            };
            return (Some(hour), minute);
        }
    }
    (None, None)
}

/// Detect a PM marker near the `ساعت <hour>` phrase.
///
/// The marker must sit within two words before `ساعت` or three words after
/// the complete time expression. A whole-sentence search misfires when the
/// same word appears later as unrelated description text. Example:
/// "ساعت 6 بلیط مترو خریدم، شب هم پیتزا گرفتم" must stay 6 o'clock.
/// Three tokens after the time cover the spaced spelling "بعد از ظهر".
///
/// A leading minute phrase ("و <digits> [دقیقه]" or "و نیم") is skipped
/// first. A marker that follows the minutes then stays inside the window.
///
/// Markers match whole words only. A word that merely contains a marker as
/// a substring does not shift the hour. Example: "دیشب" ("دی" + "شب") is a
/// day-relative word, so "دیشب ساعت 9 رفتم" stays 9 o'clock.
fn is_pm_marker_near_time(sentence: &str, saat_start: usize, digits_end: usize) -> bool {
    const PM_MARKER_WORDS_BEFORE: usize = 2;
    const PM_MARKER_WORDS_AFTER: usize = 3;
    const PM_MARKER_WORDS: [&str; 3] = ["شب", "عصر", "بعدازظهر"];
    const SPACED_PM_MARKER: [&str; 3] = ["بعد", "از", "ظهر"];
    const MINUTE_CONJUNCTION: &str = "و";
    const MINUTE_HALF_WORD: &str = "نیم";
    const MINUTE_UNIT_WORD: &str = "دقیقه";
    const TOKEN_PUNCTUATION: &[char] = &[
        '،', '؛', '؟', '!', '.', ',', ':', ';', '«', '»', '“', '”', '"', '\'', '(', ')', '[', ']',
        '{', '}',
    ];
    fn trim_token(token: &str) -> &str {
        token.trim_matches(TOKEN_PUNCTUATION)
    }
    let before_words = sentence[..saat_start]
        .split_whitespace()
        .rev()
        .take(PM_MARKER_WORDS_BEFORE)
        .map(trim_token);
    let mut after_tokens: Vec<&str> = sentence[digits_end..]
        .split_whitespace()
        .take(PM_MARKER_WORDS_AFTER * 2)
        .collect();
    if after_tokens.first().copied().map(trim_token) == Some(MINUTE_CONJUNCTION) {
        after_tokens.remove(0);
        let minute_value = after_tokens.first().copied().map(trim_token);
        let is_minute_value = minute_value.is_some_and(|word| {
            word == MINUTE_HALF_WORD
                || word
                    .chars()
                    .all(|c| c.is_ascii_digit() || ('\u{06F0}'..='\u{06F9}').contains(&c))
        });
        if is_minute_value {
            after_tokens.remove(0);
            if after_tokens.first().copied().map(trim_token) == Some(MINUTE_UNIT_WORD) {
                after_tokens.remove(0);
            }
        }
    }
    let after_words: Vec<&str> = after_tokens
        .into_iter()
        .take(PM_MARKER_WORDS_AFTER)
        .map(trim_token)
        .collect();
    if before_words
        .chain(after_words.iter().copied())
        .any(|word| PM_MARKER_WORDS.contains(&word))
    {
        return true;
    }
    after_words == SPACED_PM_MARKER
}

/// Extract person name from Persian sentence.
/// Ported from GeminiParser.extractPersonName()
pub fn extract_person_name(sentence: &str) -> Option<String> {
    let patterns = ["به ", "از "];
    for pattern in &patterns {
        if let Some(pos) = sentence.find(pattern) {
            let rest = &sentence[pos + pattern.len()..];
            let name: String = rest.split_whitespace().take(1).collect();
            let cleaned = name
                .replace("تومان", "")
                .replace("هزار", "")
                .replace("قرض", "")
                .replace("طلب دارم", "")
                .replace("طلبکار", "")
                .replace("بدهکار", "")
                .trim()
                .to_string();
            if cleaned.chars().count() > 2 && cleaned != "من" && cleaned != "خودم" {
                return Some(cleaned);
            }
        }
    }
    None
}

// Tehran is UTC+3:30 (no DST since 1401). Day math is aligned to Tehran-local time to
// match Kotlin's JalaliCalendarHelper; on non-Tehran devices the two sides may differ
// by a day — an accepted assumption for this Persian-first app.
const TEHRAN_OFFSET_MS: i64 = 3 * 3600 * 1000 + 30 * 60 * 1000;
const MS_PER_DAY: i64 = 86_400_000;

/// Current epoch milliseconds (UTC), matching the Kotlin
/// `System.currentTimeMillis()` convention used by the rest of the pipeline.
pub(crate) fn real_now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

/// Extract Jalali days from now.
/// Ported from GeminiParser.extractJalaliDaysFromNow()
pub fn extract_jalali_days_from_now(sentence: &str) -> i32 {
    extract_jalali_days_from_now_inner(sentence, real_now_ms() + TEHRAN_OFFSET_MS)
}

fn extract_jalali_days_from_now_inner(sentence: &str, now_ms: i64) -> i32 {
    let jalali_months = [
        ("فروردین", 1),
        ("اردیبهشت", 2),
        ("خرداد", 3),
        ("تیر", 4),
        ("مرداد", 5),
        ("شهریور", 6),
        ("مهر", 7),
        ("آبان", 8),
        ("آذر", 9),
        ("دی", 10),
        ("بهمن", 11),
        ("اسفند", 12),
    ];

    if let Ok(today) = gregorian_to_jalali(now_ms) {
        let current_year = today.year;
        for (month_name, month_num) in &jalali_months {
            if !sentence.contains(month_name) {
                continue;
            }
            let before_month = sentence.split(month_name).next().unwrap_or("");
            let day_str: String = before_month
                .chars()
                .rev()
                .take(3)
                .collect::<Vec<_>>()
                .into_iter()
                .rev()
                .collect();
            let day_str = to_ascii_digits(&day_str);
            if let Ok(day) = day_str.trim().parse::<i32>() {
                if (1..=31).contains(&day) {
                    if let Ok(target_ts) = jalali_to_gregorian(current_year, *month_num, day) {
                        let now_date = now_ms / MS_PER_DAY;
                        let target_date = (target_ts + TEHRAN_OFFSET_MS) / MS_PER_DAY;
                        if target_date < now_date {
                            if let Ok(next_ts) =
                                jalali_to_gregorian(current_year + 1, *month_num, day)
                            {
                                let next_date = (next_ts + TEHRAN_OFFSET_MS) / MS_PER_DAY;
                                return (next_date - now_date) as i32;
                            }
                            // Next-year date invalid (e.g. Esfand 30 in a non-leap year);
                            // never return negative, treat as due now.
                            return 0;
                        }
                        return (target_date - now_date) as i32;
                    }
                }
            }
        }
    }
    30
}

/// Extract description from Persian sentence.
/// Ported from GeminiParser.extractDescription()
pub fn extract_description(sentence: &str) -> String {
    let filler_words = [
        "امروز",
        "دیروز",
        "پریروز",
        "فردا",
        "پسفردا",
        "پس فردا",
        "ساعت",
        "نیم",
        "دقیقه",
        "هزار",
        "تومان",
        "تومن",
        "میلیون",
        "ملیون",
        "میلیارد",
        "طلب دارم",
        "طلبکار",
        "بدهکار",
    ];

    let mut cleaned = sentence.to_string();
    for word in &filler_words {
        cleaned = replace_word_bounded(&cleaned, word, "");
    }
    cleaned = cleaned.split_whitespace().collect::<Vec<&str>>().join(" ");
    if cleaned.trim().is_empty() {
        sentence.to_string()
    } else {
        cleaned
    }
}

/// Full offline sentence parser.
/// Ported from GeminiParser.parseSentenceOffline().
/// `now_ms` is the current epoch timestamp (UTC ms) — supply a fixed value
/// for deterministic tests, or `real_now_ms()` for production use.
pub fn parse_sentence_offline_full(raw_sentence: &str, now_ms: i64) -> ParsedResult {
    let sentence = preprocess_persian_text(raw_sentence);
    let amount_toman = parse_amount(&sentence, true);
    let date_offset_days = extract_date_offset(&sentence);
    let (hour, minute) = extract_time(&sentence);
    let person_name = extract_person_name(&sentence);

    let income_keywords = [
        "حقوق",
        "درآمد",
        "واریز",
        "اضافه کار",
        "اضافه\u{200C}کار",
        "دستمزد",
        "پاداش",
        "بونوس",
        "bonus",
        "سود",
        "دریافتی",
        "واریزی",
        "حقوقی",
        "کارانه",
        "فروش",
        "درآمدزایی",
        "حق بیمه",
        "عیدی",
        "سنوات",
        "پرداختی",
        "حقوق ماه",
        "حقوق اداره",
        "حقوق شرکت",
        "حقوقم",
        "حقوقم رو",
        "دریافت کردم",
        "واریز شد",
        "رسید",
        "واریز کرد",
    ];

    let expense_keywords = [
        "خریدم",
        "پرداخت",
        "هزینه",
        "قبض",
        "اجاره",
        "خرید",
        "پول دادم",
        "خرج",
        "پرداخت کردم",
        "دادم",
        "رفت",
        "گذاشتم",
        // Personal Care
        "اصلاح",
        "سالن",
        "آرایشگاه",
        "کوتاهی مو",
        "رنگ مو",
        "واکس",
        "پدیکور",
        "مانیکور",
        "ماساژ",
        "اسپا",
        "فیشال",
        "لیزر",
        "کرم",
        "شامپو",
        "عطر",
        "ادکلن",
        "لوازم آرایش",
        "آرایش",
        "پیرایش",
        "ابرو",
        "ریمل",
        "رژ لب",
        // Education
        "کتاب",
        "مجله",
        "روزنامه",
        "دوره آموزشی",
        "کلاس",
        "آموزش",
        "مدرسه",
        "دانشگاه",
        "شهریه",
        // Rent & Utilities
        "اجاره",
        "رهن",
        "آپارتمان",
        "خانه",
        "ملک",
        "زمین",
        "ویلا",
        "باغ",
        "کلبه",
        "اقامتگاه",
        "مهمانخانه",
        "پارکینگ",
        "انبار",
        "دفتر کار",
        "مغازه",
        "فروشگاه",
        "بازرگانی",
        "شرکت",
        "کارخانه",
        "کارگاه",
        // Bills
        "قبض",
        "برق",
        "گاز",
        "تلفن",
        "اینترنت",
        "شارژ",
        "موبایل",
        // Loans
        "قرض",
        "وام",
        "بدهی",
        "قسط",
        "چک",
        "سفته",
        "ضمانت",
        "بهره",
        // Events
        "هدیه",
        "جشن",
        "تولد",
        "عروسی",
        "نامزدی",
        "سالگرد",
        "مراسم",
        "مهمانی",
        // Charity
        "خیریه",
        "صدقه",
        "کمک",
        // Investment
        "سرمایه گذاری",
        "خرید سهام",
        "طلا",
        "سکه",
        "دلار",
        "ارز",
    ];

    let is_income = income_keywords.iter().any(|&kw| sentence.contains(kw));
    let is_expense = expense_keywords.iter().any(|&kw| sentence.contains(kw));

    let classification = classify_type(&sentence, is_income, person_name.as_deref(), now_ms);

    let mut factors = 0i32;
    if amount_toman > 0 {
        factors += 1;
    }
    if is_income || is_expense {
        factors += 1;
    }
    if person_name.is_some() {
        factors += 1;
    }
    if hour.is_some() {
        factors += 1;
    }
    if classification.days_from_now.is_some() {
        factors += 1;
    }

    let confidence = match factors {
        4.. => 0.95,
        3 => 0.90,
        2 => 0.85,
        1 => 0.75,
        _ => 0.60,
    } as f32;

    ParsedResult {
        tx_type: TransactionType::from_str(&classification.tx_type),
        amount: amount_toman * 10,
        category: classification.category,
        person_name,
        description: classification.description,
        days_from_now: classification.days_from_now,
        title: classification.installment_title,
        date_offset_days: Some(date_offset_days.unwrap_or(0)),
        hour,
        minute,
        confidence,
        notes: classification.notes,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Fixed "today" for all parser integration tests — 1405/04/10 (10 Tir 1405).
    /// Supply this to `parse_sentence_offline_full` / `classify_installment` so
    /// date-relative fields (daysFromNow, dateOffsetDays) never depend on the
    /// wall clock.
    fn test_now_ms() -> i64 {
        jalali_to_gregorian(1405, 4, 10).unwrap()
    }

    // =========================================================================
    // infer_expense_category_full tests
    // =========================================================================

    #[test]
    fn test_food_category() {
        let (cat, desc) = infer_expense_category_full("مرغ خریدم");
        assert_eq!(cat, "Food");
        assert_eq!(desc, "خرید مواد غذایی");
    }

    #[test]
    fn test_food_keywords() {
        let keywords = [
            "مرغ",
            "گوشت",
            "غذا",
            "میوه",
            "رستوران",
            "نان",
            "شیر",
            "پنیر",
            "شام",
            "ناهار",
            "صبحانه",
            "چای",
            "قهوه",
            "اسنک",
            "بستنی",
            "سالاد",
            "ماهی",
            "میگو",
            "سبزی",
            "مربا",
            "روغن",
            "برنج",
            "ماکارونی",
            "رب",
            "ادویه",
            "نوشابه",
            "آب معدنی",
            "آب",
            "دوغ",
            "دلستر",
            "چیپس",
            "شکلات",
            "کیک",
            "بیسکوییت",
            "موز",
            "سیب",
            "پرتقال",
            "هندوانه",
            "خربزه",
            "انگور",
            "توت",
            "تمشک",
            "کدو",
            "خیار",
            "گوجه",
            "کلم",
            "اسفناج",
            "لوبیا",
            "نخود",
            "عدس",
            "لپه",
            "سوپ",
            "آش",
            "حلیم",
            "کباب",
            "استیک",
            "سوسیس",
            "کالباس",
            "همبرگر",
            "پیتزا",
            "ساندویچ",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} خریدم", kw));
            assert_eq!(cat, "Food", "Keyword '{}' should map to Food", kw);
        }
    }

    #[test]
    fn test_transportation_category() {
        let (cat, _) = infer_expense_category_full("بنزین زدم");
        assert_eq!(cat, "Transportation");
    }

    #[test]
    fn test_transportation_keywords() {
        let keywords = [
            "بنزین",
            "اسنپ",
            "کرایه",
            "تاکسی",
            "مترو",
            "اتوبوس",
            "پارکینگ",
            "عوارض",
            "لنت",
            "مکانیک",
            "تعمیرگاه",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} گرفتم", kw));
            assert_eq!(
                cat, "Transportation",
                "Keyword '{}' should map to Transportation",
                kw
            );
        }
    }

    #[test]
    fn test_shopping_category() {
        let (cat, _) = infer_expense_category_full("لباس خریدم");
        assert_eq!(cat, "Shopping");
    }

    #[test]
    fn test_shopping_keywords() {
        let keywords = [
            "لباس",
            "کفش",
            "پوشاک",
            "کیف",
            "کلاه",
            "عینک",
            "ساعت مچی",
            "جواهرات",
            "زیورآلات",
            "کت",
            "شلوار",
            "پیراهن",
            "مانتو",
            "چادر",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} خریدم", kw));
            assert_eq!(cat, "Shopping", "Keyword '{}' should map to Shopping", kw);
        }
    }

    #[test]
    fn test_bills_category() {
        let (cat, _) = infer_expense_category_full("قبض برق دادم");
        assert_eq!(cat, "Bills");
    }

    #[test]
    fn test_bills_keywords() {
        let keywords = [
            "قبض",
            "برق",
            "گاز",
            "تلفن",
            "اینترنت",
            "شارژ",
            "فیبر",
            "موبایل",
            "tv",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} پرداخت کردم", kw));
            assert_eq!(cat, "Bills", "Keyword '{}' should map to Bills", kw);
        }
    }

    #[test]
    fn test_personal_care_category() {
        let (cat, _) = infer_expense_category_full("اصلاح کردم");
        assert_eq!(cat, "Personal Care");
    }

    #[test]
    fn test_personal_care_keywords() {
        let keywords = [
            "اصلاح",
            "سالن",
            "آرایشگاه",
            "کوتاهی",
            "رنگ مو",
            "واکس",
            "پدیکور",
            "مانیکور",
            "ماساژ",
            "اسپا",
            "فیشال",
            "لیزر",
            "کرم",
            "عطر",
            "ادکلن",
            "لوازم آرایش",
            "آرایش",
            "پیرایش",
            "ابرو",
            "ریمل",
            "رژ لب",
            "پودر",
            "کانسیلر",
            "سیگار",
            "قلیان",
            "اقامت",
            "سفر",
            "گردشگری",
            "تفریح",
            "سینما",
            "تئاتر",
            "کنسرت",
            "بازی",
            "ورزش",
            "باشگاه",
            "fitness",
            "Gym",
            "دارو",
            "داروخانه",
            "ویتامین",
            "درمان",
            "دندانپزشکی",
            "چشم پزشکی",
            "آزمایش",
            "رادیولوژی",
            "سونوگرافی",
            "MRI",
            "CT",
            "تست",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} رفتم", kw));
            assert_eq!(
                cat, "Personal Care",
                "Keyword '{}' should map to Personal Care",
                kw
            );
        }
    }

    #[test]
    fn test_education_category() {
        let (cat, _) = infer_expense_category_full("دانشگاه رفتم");
        assert_eq!(cat, "Education");
    }

    #[test]
    fn test_education_keywords() {
        let keywords = [
            "مجله",
            "روزنامه",
            "مدرسه",
            "دانشگاه",
            "شهریه",
            "سرویس مدرسه",
            "لوازم تحریر",
            "خودکار",
            "دفتر",
            "کاغذ",
            "Printer",
            "پرینتر",
            "کارتریج",
            "نرم افزار",
            "اپلیکیشن",
            "اشتراک",
            "سرویس",
            "service",
            "membership",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} رفتم", kw));
            assert_eq!(cat, "Education", "Keyword '{}' should map to Education", kw);
        }
    }

    #[test]
    fn test_rent_utilities_category() {
        let (cat, _) = infer_expense_category_full("اجاره خانه دادم");
        assert_eq!(cat, "Rent & Utilities");
    }

    #[test]
    fn test_rent_utilities_keywords() {
        // Keywords exclusive to Rent & Utilities (no overlap with earlier categories Food/Transport/Shopping/Bills/PersonalCare/Education)
        let keywords = [
            "اجاره",
            "رهن",
            "آپارتمان",
            "ملک",
            "ویلا",
            "مهمانخانه",
            "انبار",
            "مغازه",
            "فروشگاه",
            "بازرگانی",
            "کارخانه",
            "کارگاه",
            "مالیات",
            "تعمیرات ساختمان",
            "نقاشی ساختمان",
            "لوله کشی",
            "بنایی",
            "سنگ",
            "سیمان",
            "آجر",
            "چوب",
            "MDF",
            "لمینت",
            "سرامیک",
            "کاشی",
            "شوفاژ",
            "کولر",
            "بخاری",
            "شومینه",
            "پکیج",
            "رادیاتور",
            "لوله",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} پرداخت کردم", kw));
            assert_eq!(
                cat, "Rent & Utilities",
                "Keyword '{}' should map to Rent & Utilities",
                kw
            );
        }
    }

    #[test]
    fn test_loans_debt_category() {
        let (cat, _) = infer_expense_category_full("قرض گرفتم");
        assert_eq!(cat, "Loans & Debt");
    }

    #[test]
    fn test_loans_debt_keywords() {
        let keywords = [
            "قرض",
            "وام",
            "بدهی",
            "قسط",
            "چک",
            "سفته",
            "ضمانت",
            "سود وام",
            "جریمه",
            "کارمزد",
            "سود بانکی",
            "بهره",
            "سود مرکب",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} دارم", kw));
            assert_eq!(
                cat, "Loans & Debt",
                "Keyword '{}' should map to Loans & Debt",
                kw
            );
        }
    }

    #[test]
    fn test_loans_debt_compound_keywords() {
        let compounds = [
            "وام خودرو",
            "وام ازدواج",
            "وام تحصیلی",
            "وام فوری",
            "وام بازنشستگی",
            "وام کارمندی",
            "وام دولتی",
            "وام خصوصی",
            "وام بانکی",
            "وام بدون بهره",
            "وام با بهره",
            "وام با سود",
            "وام بدون سود",
            "وام با کارمزد",
            "وام بدون کارمزد",
            "وام با ضمانت",
            "وام بدون ضمانت",
            "وام با چک",
            "وام با سفته",
            "وام با ضامن",
            "وام بدون ضامن",
        ];
        for kw in &compounds {
            let (cat, _) = infer_expense_category_full(&format!("{} گرفتم", kw));
            assert_eq!(
                cat, "Loans & Debt",
                "Compound '{}' should map to Loans & Debt",
                kw
            );
        }
    }

    #[test]
    fn test_income_category() {
        let (cat, _) = infer_expense_category_full("درآمد داشتم");
        assert_eq!(cat, "Income");
    }

    #[test]
    fn test_income_keywords() {
        let keywords = [
            "درآمد",
            "حقوق",
            "واریز",
            "اضافه کار",
            "پاداش",
            "بونوس",
            "سود",
            "دریافتی",
            "واریزی",
            "حقوقی",
            "کارانه",
            "فروش",
            "درآمدزایی",
            "عیدی",
            "سنوات",
            "پرداختی",
            "حقوق ماه",
            "حقوق اداره",
            "حقوقم",
            "حقوقم رو",
            "دریافت کردم",
            "واریز شد",
            "رسید",
            "واریز کرد",
            "فروش رفت",
            "درآمد داشتم",
            "پول درآوردم",
            "سود کردم",
            "بازدهی",
            "return",
            "profit",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} {}", kw, "دارم"));
            assert_eq!(cat, "Income", "Keyword '{}' should map to Income", kw);
        }
    }

    #[test]
    fn test_events_gifts_category() {
        let (cat, _) = infer_expense_category_full("هدیه خریدم");
        assert_eq!(cat, "Events & Gifts");
    }

    #[test]
    fn test_events_gifts_keywords() {
        let keywords = [
            "هدیه",
            "جشن",
            "تولد",
            "عروسی",
            "نامزدی",
            "سالگرد",
            "مراسم",
            "مهمانی",
            "party",
            "celebration",
            "event",
            "wedding",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} دارم", kw));
            assert_eq!(
                cat, "Events & Gifts",
                "Keyword '{}' should map to Events & Gifts",
                kw
            );
        }
    }

    #[test]
    fn test_charity_category() {
        let (cat, _) = infer_expense_category_full("خیریه دادم");
        assert_eq!(cat, "Charity");
    }

    #[test]
    fn test_charity_keywords() {
        let keywords = ["خیریه", "صدقه", "کمک", "donate", "charity", "philanthropy"];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} کردم", kw));
            assert_eq!(cat, "Charity", "Keyword '{}' should map to Charity", kw);
        }
    }

    #[test]
    fn test_investment_category() {
        let (cat, _) = infer_expense_category_full("سرمایه گذاری کردم");
        assert_eq!(cat, "Investment");
    }

    #[test]
    fn test_investment_keywords() {
        let keywords = [
            "سرمایه گذاری",
            "خرید سهام",
            "صندوق سرمایه",
            "طلا",
            "سکه",
            "دلار",
            "ارز",
            "نفت",
        ];
        for kw in &keywords {
            let (cat, _) = infer_expense_category_full(&format!("{} خریدم", kw));
            assert_eq!(
                cat, "Investment",
                "Keyword '{}' should map to Investment",
                kw
            );
        }
    }

    #[test]
    fn test_investment_insurance_keywords() {
        // بیمه contains "بیمه" which is in Rent & Utilities (checked first).
        // In Kotlin this also maps to Rent & Utilities first due to when{} ordering.
        // Test compound forms that contain بیمه plus additional words.
        let keywords = [
            "بیمه عمر",
            "بیمه تصادف",
            "بیمه آتش سوزی",
            "بیمه زلزله",
            "بیمه سرقت",
            "بیمه مسئولیت",
        ];
        for kw in &keywords {
            // These actually map to Rent & Utilities due to "بیمه" being in that category
            let (cat, _) = infer_expense_category_full(&format!("{} خریدم", kw));
            assert_eq!(
                cat, "Rent & Utilities",
                "Insurance '{}' maps to Rent & Utilities (bimه matches first)",
                kw
            );
        }
    }

    #[test]
    fn test_other_category() {
        let (cat, _) = infer_expense_category_full("چیز عجیبی خریدم");
        assert_eq!(cat, "Other");
    }

    // =========================================================================
    // classify_installment tests
    // =========================================================================

    #[test]
    fn test_installment_not_detected() {
        let result = classify_installment("پرداخت قبض برق", test_now_ms());
        assert!(result.is_none());
    }

    #[test]
    fn test_installment_paid_car() {
        let result = classify_installment("قسط ماشین پرداخت کردم", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "EXPENSE");
        assert_eq!(result.category, "Installments");
        assert_eq!(result.description, "پرداخت قسط ماشین");
        assert!(result.installment_title.is_none());
        assert!(result.days_from_now.is_none());
    }

    #[test]
    fn test_installment_paid_house() {
        let result = classify_installment("قسط خانه دادم", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "EXPENSE");
        assert_eq!(result.description, "پرداخت قسط وام مسکن");
    }

    #[test]
    fn test_installment_paid_loan() {
        let result = classify_installment("قسط وام تسویه کردم", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "EXPENSE");
        assert_eq!(result.description, "پرداخت قسط وام");
    }

    #[test]
    fn test_installment_paid_generic() {
        let result = classify_installment("قسط جدید پرداخت کردم", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "EXPENSE");
        assert_eq!(result.description, "پرداخت قسط جدید");
    }

    #[test]
    fn test_installment_unpaid() {
        let result = classify_installment("قسط ماشین فردا", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "INSTALLMENT");
        assert_eq!(result.description, "قسط آینده");
        assert_eq!(result.installment_title, Some("قسط ماشین".to_string()));
        assert!(result.notes.is_some());
        // "فردا" (tomorrow) is a relative keyword resolving to a 1-day offset,
        // applied before the Jalali month/day parsing fallback.
        assert_eq!(result.days_from_now, Some(1));
    }

    #[test]
    fn test_installment_unpaid_house() {
        let result = classify_installment("قسط مسکن ۱۵ فروردین", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "INSTALLMENT");
        assert_eq!(result.installment_title, Some("قسط وام مسکن".to_string()));
        // "۱۵ فروردین" is month 1, day 15.  test_now_ms() is Tir 10, 1405, so
        // Farvardin 15, 1405 has already passed this year and the helper rolls
        // to the same day next year (Farvardin 15, 1406).  The difference is
        // 277 days.
        assert_eq!(result.days_from_now, Some(277));
    }

    #[test]
    fn test_installment_unpaid_same_year_future_date() {
        // "۱۵ مرداد" is month 5, day 15 — a future date in the same Jalali
        // year as test_now_ms() (Tir 10, 1405), so no year-rollover occurs.
        let result = classify_installment("قسط وام ۱۵ مرداد", test_now_ms()).unwrap();
        assert_eq!(result.tx_type, "INSTALLMENT");
        assert_eq!(result.installment_title, Some("قسط وام".to_string()));
        assert_eq!(result.days_from_now, Some(36));
    }

    // =========================================================================
    // classify_loan tests
    // =========================================================================

    #[test]
    fn test_loan_not_detected() {
        let result = classify_loan("پرداخت قبض برق", None);
        assert!(result.is_none());
    }

    #[test]
    fn test_loan_received() {
        let result = classify_loan("قرض گرفتم از علی", Some("علی")).unwrap();
        assert_eq!(result.tx_type, "LOAN_CREDITOR");
        assert_eq!(result.category, "Loans");
        assert_eq!(result.description, "قرض گرفتن از علی");
        assert_eq!(result.notes, Some("قرض جدید ثبت شده".to_string()));
    }

    #[test]
    fn test_loan_received_debtor() {
        let result = classify_loan("بدهکار شدم", None).unwrap();
        assert_eq!(result.tx_type, "LOAN_CREDITOR");
        assert_eq!(result.description, "قرض گرفتن از طلبکار");
    }

    #[test]
    fn test_loan_received_from() {
        let result = classify_loan("گرفتم از محمد", Some("محمد")).unwrap();
        assert_eq!(result.tx_type, "LOAN_CREDITOR");
        assert_eq!(result.description, "قرض گرفتن از محمد");
    }

    #[test]
    fn test_loan_given() {
        let result = classify_loan("قرض دادم به رضا", Some("رضا")).unwrap();
        assert_eq!(result.tx_type, "LOAN_DEBTOR");
        assert_eq!(result.category, "Loans");
        assert_eq!(result.description, "قرض دادن به رضا");
        assert_eq!(result.notes, Some("طلب جدید ثبت شده".to_string()));
    }

    #[test]
    fn test_loan_given_creditor() {
        let result = classify_loan("طلبکار شدم", None).unwrap();
        assert_eq!(result.tx_type, "LOAN_DEBTOR");
        assert_eq!(result.description, "قرض دادن به بدهکار");
    }

    #[test]
    fn test_loan_given_to() {
        let result = classify_loan("دادم به علی", Some("علی")).unwrap();
        assert_eq!(result.tx_type, "LOAN_DEBTOR");
        assert_eq!(result.description, "قرض دادن به علی");
    }

    #[test]
    fn test_loan_given_claim() {
        let result = classify_loan("طلب دارم از حسن", Some("حسن")).unwrap();
        assert_eq!(result.tx_type, "LOAN_DEBTOR");
        assert_eq!(result.description, "قرض دادن به حسن");
    }

    // =========================================================================
    // classify_income tests
    // =========================================================================

    #[test]
    fn test_income_overtime() {
        let result = classify_income("اضافه کار گرفتم");
        assert_eq!(result.tx_type, "INCOME");
        assert_eq!(result.category, "Income");
        assert_eq!(result.description, "دریافت اضافه کار");
    }

    #[test]
    fn test_income_bonus() {
        let result = classify_income("پاداش گرفتم");
        assert_eq!(result.description, "دریافت پاداش");
    }

    #[test]
    fn test_income_wage() {
        let result = classify_income("دستمزد گرفتم");
        assert_eq!(result.description, "دریافت دستمزد");
    }

    #[test]
    fn test_income_sale() {
        let result = classify_income("فروش ماشین");
        assert!(result.description.starts_with("درآمد از فروش"));
    }

    #[test]
    fn test_income_profit() {
        let result = classify_income("سود گرفتم");
        assert_eq!(result.description, "دریافت سود");
    }

    #[test]
    fn test_income_salary() {
        let result = classify_income("حقوق گرفتم");
        assert_eq!(result.description, "دریافت حقوق");
    }

    #[test]
    fn test_income_other() {
        let result = classify_income("درآمد داشتم");
        assert!(result.description.starts_with("دریافت درآمد"));
    }

    // =========================================================================
    // classify_expense tests
    // =========================================================================

    #[test]
    fn test_expense_food() {
        let result = classify_expense("نان خریدم");
        assert_eq!(result.tx_type, "EXPENSE");
        // Food is not normalized, stays as Food
        assert_eq!(result.category, "Food");
    }

    #[test]
    fn test_expense_personal_care_normalized() {
        let result = classify_expense("اصلاح کردم");
        assert_eq!(result.tx_type, "EXPENSE");
        // Personal Care is normalized to Other
        assert_eq!(result.category, "Other");
    }

    #[test]
    fn test_expense_education_normalized() {
        let result = classify_expense("دانشگاه رفتم");
        assert_eq!(result.category, "Other");
    }

    #[test]
    fn test_expense_rent_normalized() {
        let result = classify_expense("اجاره خانه دادم");
        assert_eq!(result.category, "Other");
    }

    #[test]
    fn test_expense_other_empty_subject() {
        // Sentence where all meaningful words are filler → empty subject
        let result = classify_expense("خریدم");
        assert_eq!(result.category, "Other");
        // Empty subject → base description only
        assert_eq!(result.description, "سایر هزینه\u{200C}ها");
    }

    #[test]
    fn test_expense_other_nonempty_subject() {
        // Sentence with a meaningful subject
        let result = classify_expense("چیز عجیبی خریدم");
        assert_eq!(result.category, "Other");
        // Non-empty subject → base description + subject
        assert!(result.description.contains("چیز عجیبی"));
    }

    // =========================================================================
    // normalize_category tests
    // =========================================================================

    #[test]
    fn test_normalize_category_preserves() {
        assert_eq!(normalize_category("Food"), "Food");
        assert_eq!(normalize_category("Transportation"), "Transportation");
        assert_eq!(normalize_category("Shopping"), "Shopping");
        assert_eq!(normalize_category("Bills"), "Bills");
        assert_eq!(normalize_category("Installments"), "Installments");
        assert_eq!(normalize_category("Loans"), "Loans");
        assert_eq!(normalize_category("Income"), "Income");
        assert_eq!(normalize_category("Other"), "Other");
    }

    #[test]
    fn test_normalize_category_normalizes() {
        assert_eq!(normalize_category("Personal Care"), "Other");
        assert_eq!(normalize_category("Education"), "Other");
        assert_eq!(normalize_category("Rent & Utilities"), "Other");
        assert_eq!(normalize_category("Loans & Debt"), "Other");
        assert_eq!(normalize_category("Events & Gifts"), "Other");
        assert_eq!(normalize_category("Charity"), "Other");
        assert_eq!(normalize_category("Investment"), "Other");
    }

    // =========================================================================
    // extract_date_offset tests
    // =========================================================================

    #[test]
    fn test_date_today() {
        // "امروز" (today) returns Some(0) — distinct from None (no keyword found).
        assert_eq!(extract_date_offset("امروز خرید کردم"), Some(0));
    }

    #[test]
    fn test_date_yesterday() {
        assert_eq!(extract_date_offset("دیروز رفتم"), Some(-1));
    }

    #[test]
    fn test_date_day_before_yesterday() {
        assert_eq!(extract_date_offset("پریروز رفتم"), Some(-2));
    }

    #[test]
    fn test_date_tomorrow() {
        assert_eq!(extract_date_offset("فردا می‌روم"), Some(1));
    }

    #[test]
    fn test_date_day_after_tomorrow() {
        assert_eq!(extract_date_offset("پس فردا می‌روم"), Some(2));
    }

    #[test]
    fn test_date_day_after_tomorrow_compact() {
        assert_eq!(extract_date_offset("پسفردا می‌روم"), Some(2));
    }

    #[test]
    fn test_date_no_match() {
        // No relative keyword → None (not Some(0)).
        assert_eq!(extract_date_offset("ساعت 14 جلسه دارم"), None);
    }

    // =========================================================================
    // extract_time tests
    // =========================================================================

    #[test]
    fn test_time_hour_only() {
        let (hour, minute) = extract_time("ساعت 14 جلسه دارم");
        assert_eq!(hour, Some(14));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_half() {
        let (hour, minute) = extract_time("ساعت 2 و نیم می‌روم");
        assert_eq!(hour, Some(2));
        assert_eq!(minute, Some(30));
    }

    #[test]
    fn test_time_evening() {
        let (hour, minute) = extract_time("ساعت 3 عصر می‌روم");
        assert_eq!(hour, Some(15));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_night() {
        let (hour, minute) = extract_time("ساعت 9 شب می‌روم");
        assert_eq!(hour, Some(21));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_afternoon() {
        let (hour, minute) = extract_time("ساعت 4 بعدازظهر جلسه دارم");
        assert_eq!(hour, Some(16));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_afternoon_spaced() {
        let (hour, minute) = extract_time("ساعت 4 بعد از ظهر جلسه دارم");
        assert_eq!(hour, Some(16));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_with_minutes() {
        let (hour, minute) = extract_time("ساعت 14 و 30 دقیقه");
        assert_eq!(hour, Some(14));
        assert_eq!(minute, Some(30));
    }

    #[test]
    fn test_time_no_match() {
        let (hour, minute) = extract_time("خرید نان");
        assert_eq!(hour, None);
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_persian_digits() {
        let (hour, minute) = extract_time("ساعت ۱۴ جلسه");
        assert_eq!(hour, Some(14));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_pm_marker_before_hour_token() {
        // The marker precedes the ساعت token: "امروز شب ساعت 9".
        let (hour, minute) = extract_time("امروز شب ساعت 9 رفتم");
        assert_eq!(hour, Some(21));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_pm_word_in_time_context_still_applies() {
        // "فردا شب" directly qualifies the time expression.
        let (hour, minute) = extract_time("ساعت 5 فردا شب می رویم");
        assert_eq!(hour, Some(17));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_night_word_far_away_does_not_shift() {
        // "شب" belongs to a later, unrelated description. It must not
        // turn 6 o'clock into 18 o'clock.
        let (hour, minute) = extract_time("ساعت 6 بلیط مترو خریدم، شب هم پیتزا گرفتم");
        assert_eq!(hour, Some(6));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_asr_word_far_away_does_not_shift() {
        // "عصر" sits beyond the three-word window after the digits.
        let (hour, minute) = extract_time("ساعت 10 قهوه نوشیدم و عصر برگشتم");
        assert_eq!(hour, Some(10));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_noon_with_pm_marker_not_shifted() {
        // 12 is outside the 1..=11 shift window, so it stays 12.
        let (hour, minute) = extract_time("ساعت 12 شب رسید");
        assert_eq!(hour, Some(12));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_first_hour_with_pm_marker_shifts() {
        let (hour, minute) = extract_time("ساعت 1 بعدازظهر رسید");
        assert_eq!(hour, Some(13));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_dishab_substring_does_not_force_pm() {
        // "دیشب" contains the substring "شب", but it is a day-relative word,
        // not a time-of-day marker. The hour must stay unshifted.
        let (hour, minute) = extract_time("دیشب ساعت 9 رفتم");
        assert_eq!(hour, Some(9));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_night_marker_directly_after_digits() {
        let (hour, minute) = extract_time("ساعت ۹ شب رفتم");
        assert_eq!(hour, Some(21));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_asr_marker_directly_after_digits() {
        let (hour, minute) = extract_time("ساعت ۳ عصر");
        assert_eq!(hour, Some(15));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_pm_marker_after_minute_phrase() {
        // The marker follows the full "hour + minutes" expression.
        let (hour, minute) = extract_time("ساعت 9 و نیم شب رسید");
        assert_eq!(hour, Some(21));
        assert_eq!(minute, Some(30));
    }

    #[test]
    fn test_time_spaced_pm_marker_after_minute_phrase() {
        let (hour, minute) = extract_time("ساعت 5 و نیم بعد از ظهر رسید");
        assert_eq!(hour, Some(17));
        assert_eq!(minute, Some(30));
    }

    #[test]
    fn test_time_pm_marker_after_numeric_minutes() {
        let (hour, minute) = extract_time("ساعت 2 و 45 دقیقه عصر رسید");
        assert_eq!(hour, Some(14));
        assert_eq!(minute, Some(45));
    }

    #[test]
    fn test_time_quoted_pm_marker_still_applies() {
        let (hour, minute) = extract_time("ساعت 9 «شب» رسید");
        assert_eq!(hour, Some(21));
        assert_eq!(minute, None);
    }

    #[test]
    fn test_time_spaced_marker_requires_exact_words() {
        // The spaced marker matches the exact three words only. A longer
        // word that starts with one of them must not trigger the shift.
        let (hour, minute) = extract_time("ساعت 4 بعد از روزنامه خواندن");
        assert_eq!(hour, Some(4));
        assert_eq!(minute, None);
    }

    // =========================================================================
    // extract_person_name tests
    // =========================================================================

    #[test]
    fn test_person_name_be() {
        let name = extract_person_name("پرداخت به علی");
        assert_eq!(name, Some("علی".to_string()));
    }

    #[test]
    fn test_person_name_az() {
        let name = extract_person_name("دریافت از محمد");
        assert_eq!(name, Some("محمد".to_string()));
    }

    #[test]
    fn test_person_name_filtered() {
        // "من" should be filtered
        let name = extract_person_name("پرداخت به من");
        assert!(name.is_none());
    }

    #[test]
    fn test_person_name_short() {
        // Single character name "من" should be filtered
        let name = extract_person_name("پرداخت به من");
        assert!(name.is_none());
    }

    #[test]
    fn test_person_name_no_match() {
        let name = extract_person_name("خرید نان");
        assert!(name.is_none());
    }

    // =========================================================================
    // extract_description tests
    // =========================================================================

    #[test]
    fn test_description_filler_words() {
        let desc = extract_description("امروز خرید نان");
        assert_eq!(desc, "خرید نان");
    }

    #[test]
    fn test_description_time_filler() {
        let desc = extract_description("ساعت 14 خرید نان");
        assert_eq!(desc, "14 خرید نان");
    }

    #[test]
    fn test_description_amount_filler() {
        let desc = extract_description("۵۰۰۰ تومان خرید نان");
        assert_eq!(desc, "۵۰۰۰ خرید نان");
    }

    #[test]
    fn test_description_empty_after_cleaning() {
        let desc = extract_description("امروز");
        // Should return original if empty after cleaning
        assert_eq!(desc, "امروز");
    }

    #[test]
    fn test_description_no_filler() {
        let desc = extract_description("خرید نان");
        assert_eq!(desc, "خرید نان");
    }

    // =========================================================================
    // extract_jalali_days_from_now tests
    // =========================================================================

    #[test]
    fn test_jalali_default() {
        // If no jalali date found, returns 30
        let days = extract_jalali_days_from_now("خرید نان");
        assert_eq!(days, 30);
    }

    #[test]
    fn test_jalali_farvardin() {
        // Should return some number for Farvardin
        let days = extract_jalali_days_from_now("۱ فروردین");
        // The exact value depends on current date, just check it's not 30
        // (it will be 30 if the date is invalid or in the past)
        let _ = days; // Just check it doesn't panic
    }

    #[test]
    fn test_jalali_month_name() {
        // Should not panic with just a month name
        let _ = extract_jalali_days_from_now("خرداد");
    }

    #[test]
    fn test_jalali_days_rolls_to_next_year_for_past_date() {
        // Regression: when the specified Jalali date already passed this year,
        // the result must roll forward to next year (~365 days) and stay non-negative
        // (previously it returned a negative day count). Fix "now" to 1405/05/01 so
        // "تیر ۲۵" has already passed and the rollover branch is exercised deterministically.
        let now_ms = jalali_to_gregorian(1405, 5, 1).unwrap();
        let days = extract_jalali_days_from_now_inner("قسط ماشین ۲۵ تیر ۱۰ میلیون", now_ms);
        assert!(days >= 0, "daysFromNow must not be negative, got {days}");
        assert!(days > 300, "past date must roll to next year, got {days}");
    }

    // =========================================================================
    // parse_sentence_offline_full integration tests
    // =========================================================================

    #[test]
    fn test_parse_simple_expense() {
        let result = parse_sentence_offline_full("۵۰۰۰ تومان خرید نان", test_now_ms());
        assert_eq!(result.amount, 50000); // 5000 toman * 10 = 50000 rial
        assert_eq!(result.category, "Food");
    }

    #[test]
    fn test_parse_expense_with_date() {
        let result = parse_sentence_offline_full("فردا ۱۰۰۰۰ تومان خرید نان", test_now_ms());
        assert_eq!(result.amount, 100000);
        assert_eq!(result.date_offset_days, Some(1));
    }

    #[test]
    fn test_parse_income() {
        let result = parse_sentence_offline_full("حقوق ۵۰۰۰۰۰ تومان واریز شد", test_now_ms());
        assert_eq!(result.amount, 5000000);
        assert_eq!(result.tx_type, TransactionType::Income);
    }

    #[test]
    fn test_parse_loan_received() {
        let result = parse_sentence_offline_full("قرض گرفتم از علی ۱۰۰۰۰۰ تومان", test_now_ms());
        assert_eq!(result.tx_type, TransactionType::LoanCreditor);
        assert_eq!(result.person_name, Some("علی".to_string()));
    }

    #[test]
    fn test_parse_loan_given() {
        let result = parse_sentence_offline_full("قرض دادم به رضا ۲۰۰۰۰۰ تومان", test_now_ms());
        assert_eq!(result.tx_type, TransactionType::LoanDebtor);
        assert_eq!(result.person_name, Some("رضا".to_string()));
    }

    #[test]
    fn test_parse_installment() {
        let result = parse_sentence_offline_full("قسط ماشین فردا ۳۰۰۰۰۰ تومان", test_now_ms());
        assert_eq!(result.tx_type, TransactionType::Installment);
        assert_eq!(result.title, Some("قسط ماشین".to_string()));
    }

    #[test]
    fn test_parse_installment_today() {
        // "امروز" is today (offset 0). Must NOT fall through to the 30-day
        // Jalali-parsing default — days_from_now must be Some(0).
        let result = parse_sentence_offline_full("قسط ماشین امروز ۳۰۰۰۰۰ تومان", test_now_ms());
        assert_eq!(result.tx_type, TransactionType::Installment);
        assert_eq!(result.title, Some("قسط ماشین".to_string()));
        assert_eq!(result.days_from_now, Some(0));
        assert_eq!(result.date_offset_days, Some(0));
    }

    #[test]
    fn test_parse_installment_paid() {
        let result =
            parse_sentence_offline_full("قسط ماشین پرداخت کردم ۳۰۰۰۰۰ تومان", test_now_ms());
        assert_eq!(result.tx_type, TransactionType::Expense);
        assert_eq!(result.category, "Installments");
    }

    #[test]
    fn test_parse_no_date_keyword_yields_zero_offset() {
        // When no relative-date keyword is present, extract_date_offset returns
        // None. parse_sentence_offline_full must normalize that to Some(0) to
        // match the Kotlin fallback (GeminiParser fallback uses optInt("dateOffsetDays", 0)).
        let result = parse_sentence_offline_full("۵۰۰۰ تومان نان خریدم", test_now_ms());
        assert_eq!(
            result.date_offset_days,
            Some(0),
            "date_offset_days must be Some(0), not None, when no date keyword is present"
        );
    }

    #[test]
    fn test_parse_with_time() {
        let result = parse_sentence_offline_full("ساعت 14 جلسه دارم", test_now_ms());
        assert_eq!(result.hour, Some(14));
    }

    #[test]
    fn test_parse_confidence_high() {
        // Multiple factors: amount + income keyword + time
        let result =
            parse_sentence_offline_full("ساعت 14 حقوق ۵۰۰۰۰۰ تومان واریز شد", test_now_ms());
        assert!(result.confidence >= 0.90);
    }

    #[test]
    fn test_parse_confidence_low() {
        // No factors
        let result = parse_sentence_offline_full("چیز عجیبی", test_now_ms());
        assert!(result.confidence <= 0.65);
    }

    #[test]
    fn test_parse_persian_digits() {
        let result = parse_sentence_offline_full("۵۰۰۰ تومان خرید نان", test_now_ms());
        assert_eq!(result.amount, 50000);
    }

    #[test]
    fn test_parse_empty() {
        let result = parse_sentence_offline_full("", test_now_ms());
        assert_eq!(result.amount, 0);
        assert_eq!(result.confidence, 0.60);
    }

    // =========================================================================
    // extract_subject tests
    // =========================================================================

    #[test]
    fn test_subject_basic() {
        let subject = extract_subject("نان بربری");
        assert_eq!(subject, "نان بربری");
    }

    #[test]
    fn test_subject_removes_filler() {
        let subject = extract_subject("امروز نان بربری");
        assert_eq!(subject, "نان بربری");
    }

    #[test]
    fn test_subject_removes_amount() {
        let subject = extract_subject("۵۰۰۰ تومان نان بربری");
        assert_eq!(subject, "۵۰۰۰ نان بربری");
    }

    #[test]
    fn test_subject_empty_after_cleaning() {
        let subject = extract_subject("خریدم");
        assert_eq!(subject, "");
    }

    #[test]
    fn test_subject_preserves_word_containing_filler_substring() {
        // "به" is a filler token but must not be stripped from inside "نوشابه".
        let subject = extract_subject("نوشابه خریدم 85 هزار تومن");
        assert_eq!(subject, "نوشابه 85");
    }

    #[test]
    fn test_subject_removes_attached_currency_word() {
        // When digits and تومان are attached without space, تومان should still be removed.
        // خرید is also a filler word, so it gets removed too.
        let subject = extract_subject("۵۰۰۰تومان خرید نان");
        assert_eq!(subject, "۵۰۰۰ نان");
    }
}
