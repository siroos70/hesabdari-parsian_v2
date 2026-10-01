use crate::models::{HesabyarError, JalaliDate};

const G_MONTH_DAY_OFFSETS: [i32; 13] = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 0];
const J_MONTH_DAYS: [i32; 12] = [31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29];

// ===========================================================================
// Packed Jalali Date (i64)
//
// For FFI safety, Jalali dates are packed into a single i64:
//
//   bits 63..16 : year   (i64 >> 16)
//   bits 15..8  : month  (i64 >> 8) & 0xFF
//   bits  7..0  : day    i64 & 0xFF
//
// Example: 1403/01/01
//   packed = (1403 << 16) | (1 << 8) | 1 = 92_027_905
//
// To decode on the Kotlin/JVM side:
//   val year  = (packed shr 16).toInt()          // 1403
//   val month = ((packed shr 8) and 0xFF).toInt() // 1
//   val day   = (packed and 0xFF).toInt()         // 1
//
// Error sentinel: packed == 0 means the conversion failed.
// ===========================================================================

#[uniffi::export]
pub fn is_jalali_leap_year(year: i32) -> bool {
    let r = year % 33;
    matches!(r, 1 | 5 | 9 | 13 | 17 | 22 | 26 | 30)
}

#[uniffi::export]
pub fn get_jalali_days_in_month(year: i32, month: i32) -> i32 {
    if (1..=6).contains(&month) {
        31
    } else if (7..=11).contains(&month) || is_jalali_leap_year(year) {
        30
    } else {
        29
    }
}

pub fn gregorian_to_jalali(timestamp_ms: i64) -> Result<JalaliDate, HesabyarError> {
    let (gy, gm, gd) = timestamp_to_gregorian(timestamp_ms)?;
    gregorian_to_jalali_date(gy, gm, gd)
}

/// FFI-safe wrapper: returns packed i64 (year << 16 | month << 8 | day).
/// On error, returns 0 (representing an invalid date).
pub fn gregorian_to_jalali_packed(timestamp_ms: i64) -> i64 {
    match gregorian_to_jalali(timestamp_ms) {
        Ok(jd) => ((jd.year as i64) << 16) | ((jd.month as i64) << 8) | (jd.day as i64),
        Err(_) => 0,
    }
}

fn is_leap_gregorian(year: i64) -> bool {
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

fn timestamp_to_gregorian(timestamp_ms: i64) -> Result<(i32, i32, i32), HesabyarError> {
    // Floor division so timestamps just before the epoch map to the previous
    // calendar day (fixes pre-epoch date conversion — see R64).
    let days = timestamp_ms.div_euclid(86_400_000);

    // Convert days-since-epoch to year/month/day by walking year-by-year.
    // `days` is the offset from 1970-01-01 (day 0) and may be negative.
    let mut year: i64 = 1970;
    let mut day_of_year = days;

    if day_of_year >= 0 {
        loop {
            let days_in_year: i64 = if is_leap_gregorian(year) { 366 } else { 365 };
            if day_of_year < days_in_year {
                break;
            }
            day_of_year -= days_in_year;
            year += 1;
        }
    } else {
        year = 1969;
        loop {
            let days_in_year: i64 = if is_leap_gregorian(year) { 366 } else { 365 };
            // A day belongs to `year` when it lies within [-(days_in_year), 0].
            if day_of_year >= -days_in_year {
                break;
            }
            day_of_year += days_in_year;
            year -= 1;
        }
        let days_in_year: i64 = if is_leap_gregorian(year) { 366 } else { 365 };
        day_of_year += days_in_year;
    }

    let month_days: [i64; 12] = if is_leap_gregorian(year) {
        [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    } else {
        [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    };

    let mut month = 0usize;
    while month < 12 && day_of_year >= month_days[month] {
        day_of_year -= month_days[month];
        month += 1;
    }

    Ok((year as i32, (month + 1) as i32, (day_of_year + 1) as i32))
}

pub fn gregorian_to_jalali_date(
    g_year: i32,
    g_month: i32,
    g_day: i32,
) -> Result<JalaliDate, HesabyarError> {
    if !(1..=12).contains(&g_month) {
        return Err(HesabyarError::CalendarError {
            detail: format!("Invalid Gregorian date: {}/{}/{}", g_year, g_month, g_day),
        });
    }
    let max_day = match g_month {
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        4 | 6 | 9 | 11 => 30,
        2 => {
            if (g_year % 4 == 0 && g_year % 100 != 0) || (g_year % 400 == 0) {
                29
            } else {
                28
            }
        }
        _ => unreachable!(),
    };
    if g_day < 1 || g_day > max_day {
        return Err(HesabyarError::CalendarError {
            detail: format!("Invalid Gregorian date: {}/{}/{}", g_year, g_month, g_day),
        });
    }
    let gy = g_year - 1600;
    let gm = g_month - 1;
    let gd = g_day - 1;

    let mut g_day_no = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400;
    g_day_no += G_MONTH_DAY_OFFSETS[gm as usize];
    if gm > 1 && ((g_year % 4 == 0 && g_year % 100 != 0) || (g_year % 400 == 0)) {
        g_day_no += 1;
    }
    g_day_no += gd;

    let mut j_day_no = g_day_no - 79;
    let j_np = j_day_no / 12053;
    j_day_no %= 12053;
    let mut jy = 979 + 33 * j_np + 4 * (j_day_no / 1461);
    j_day_no %= 1461;
    if j_day_no >= 366 {
        jy += (j_day_no - 1) / 365;
        j_day_no = (j_day_no - 1) % 365;
    }

    let mut i = 0;
    while i < 11 && j_day_no >= J_MONTH_DAYS[i] {
        j_day_no -= J_MONTH_DAYS[i];
        i += 1;
    }

    Ok(JalaliDate {
        year: jy,
        month: (i + 1) as i32,
        day: (j_day_no + 1),
    })
}

pub fn jalali_to_gregorian(j_year: i32, j_month: i32, j_day: i32) -> Result<i64, HesabyarError> {
    if j_year < 1 || !(1..=12).contains(&j_month) || j_day < 1 {
        return Err(HesabyarError::CalendarError {
            detail: format!("Invalid Jalali date: {}/{}/{}", j_year, j_month, j_day),
        });
    }
    // Validate day against month-specific limits
    let max_day = get_jalali_days_in_month(j_year, j_month);
    if j_day > max_day {
        return Err(HesabyarError::CalendarError {
            detail: format!(
                "Invalid Jalali day {} for month {} (max {})",
                j_day, j_month, max_day
            ),
        });
    }
    let jy = j_year - 979;
    let jm = j_month - 1;
    let jd = j_day - 1;

    let mut j_day_no = 365 * jy + (jy / 33) * 8 + (jy % 33 + 3) / 4;
    for i in 0..jm {
        j_day_no += J_MONTH_DAYS[i as usize];
    }
    j_day_no += jd;

    let mut g_day_no = j_day_no + 79;
    let mut gy = 1600 + 400 * (g_day_no / 146097);
    g_day_no %= 146097;

    let mut leap = true;
    if g_day_no >= 36525 {
        g_day_no -= 1;
        gy += 100 * (g_day_no / 36524);
        g_day_no %= 36524;
        if g_day_no >= 365 {
            g_day_no += 1;
        } else {
            leap = false;
        }
    }
    gy += 4 * (g_day_no / 1461);
    g_day_no %= 1461;
    if g_day_no >= 366 {
        leap = false;
        g_day_no -= 1;
        gy += g_day_no / 365;
        g_day_no %= 365;
    }

    let g_days_in_month = [
        31,
        if leap { 29 } else { 28 },
        31,
        30,
        31,
        30,
        31,
        31,
        30,
        31,
        30,
        31,
    ];

    let mut i = 0;
    while i < 12 && g_day_no >= g_days_in_month[i] {
        g_day_no -= g_days_in_month[i];
        i += 1;
    }

    let gm = (i + 1) as i32;
    let gd = g_day_no + 1;

    gregorian_to_timestamp(gy, gm, gd)
}

/// FFI-safe wrapper: returns epoch milliseconds (i64).
/// On error, returns i64::MIN (a value that cannot be a valid timestamp).
pub fn jalali_to_gregorian_packed(j_year: i32, j_month: i32, j_day: i32) -> i64 {
    jalali_to_gregorian(j_year, j_month, j_day).unwrap_or(i64::MIN)
}

fn gregorian_to_timestamp(year: i32, month: i32, day: i32) -> Result<i64, HesabyarError> {
    // Compute days since 1970-01-01
    let y = year as i64;
    let m = month as i64;
    let d = day as i64;

    let mut days = 365 * (y - 1970) + (y - 1969) / 4 - (y - 1901) / 100 + (y - 1601) / 400;
    let month_days = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
    days += month_days[(m - 1) as usize];
    if m > 2 && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) {
        days += 1;
    }
    days += d - 1;

    Ok(days * 86400 * 1000)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_known_jalali_date() {
        // 2024-03-20 = 1403/01/01 (Nowruz)
        let result = gregorian_to_jalali_date(2024, 3, 20).unwrap();
        assert_eq!(result.year, 1403);
        assert_eq!(result.month, 1);
        assert_eq!(result.day, 1);
    }

    #[test]
    fn test_known_gregorian_date() {
        // 1403/01/01 = 2024-03-20
        let ts = jalali_to_gregorian(1403, 1, 1).unwrap();
        let result = timestamp_to_gregorian(ts).unwrap();
        assert_eq!(result.0, 2024);
        assert_eq!(result.1, 3);
        assert_eq!(result.2, 20);
    }

    #[test]
    fn test_gregorian_2025_nowruz_roundtrip() {
        // 2025-03-20 = 1403/12/30 (day before Nowruz 1404). Leap-year boundary must not
        // overflow to an invalid month 13.
        let jd = gregorian_to_jalali_date(2025, 3, 20).unwrap();
        assert_eq!(jd.year, 1403);
        assert_eq!(jd.month, 12);
        assert_eq!(jd.day, 30);
        // Round-trips back to the same Gregorian date.
        let ts = jalali_to_gregorian(1403, 12, 30).unwrap();
        let g = timestamp_to_gregorian(ts).unwrap();
        assert_eq!((g.0, g.1, g.2), (2025, 3, 20));
    }

    #[test]
    fn test_leap_year() {
        assert!(is_jalali_leap_year(1403)); // 1403 % 33 = 1
        assert!(!is_jalali_leap_year(1402)); // 1402 % 33 = 0
    }

    #[test]
    fn test_days_in_month() {
        assert_eq!(get_jalali_days_in_month(1403, 1), 31);
        assert_eq!(get_jalali_days_in_month(1403, 7), 30);
        assert_eq!(get_jalali_days_in_month(1403, 12), 30); // 1403 is leap
        assert_eq!(get_jalali_days_in_month(1402, 12), 29); // 1402 is not leap
    }

    #[test]
    fn test_five_year_leap_anomaly_1407_and_1408() {
        // Jalali leap years follow the 33-year Birashk cycle, not `year % 4`.
        // After the 1403 leap year the next leap is 1408 — a 5-year gap
        // (1404–1407 are 365-day years). Esfand 1407 must be 29 days and
        // Esfand 1408 must be 30 days.
        assert!(!is_jalali_leap_year(1407)); // 1407 % 33 = 21
        assert!(is_jalali_leap_year(1408)); // 1408 % 33 = 22
        assert_eq!(get_jalali_days_in_month(1407, 12), 29);
        assert_eq!(get_jalali_days_in_month(1408, 12), 30);

        // The day after Esfand 1407 is Farvardin 1, 1408; the day after Esfand
        // 1408 is Farvardin 1, 1409 — confirming the 30-day leap Esfand rolls
        // over correctly.
        let next_after_1407 = jalali_to_gregorian(1407, 12, 29).unwrap() + 86_400_000;
        let rollover_1407 = gregorian_to_jalali(next_after_1407).unwrap();
        assert_eq!(rollover_1407.year, 1408);
        assert_eq!(rollover_1407.month, 1);
        assert_eq!(rollover_1407.day, 1);

        let next_after_1408 = jalali_to_gregorian(1408, 12, 30).unwrap() + 86_400_000;
        let rollover_1408 = gregorian_to_jalali(next_after_1408).unwrap();
        assert_eq!(rollover_1408.year, 1409);
        assert_eq!(rollover_1408.month, 1);
        assert_eq!(rollover_1408.day, 1);
    }

    // =====================================================================
    // FFI-safe packed function tests
    // =====================================================================

    #[test]
    fn test_gregorian_to_jalali_packed() {
        // 2024-03-20 = 1403/01/01
        // Packed: (1403 << 16) | (1 << 8) | 1 = 92027905
        let packed = gregorian_to_jalali_packed(1710950400000); // approx 2024-03-20
        let year = (packed >> 16) as i32;
        let month = ((packed >> 8) & 0xFF) as i32;
        let day = (packed & 0xFF) as i32;
        assert_eq!(year, 1403);
        assert_eq!(month, 1);
        assert!(
            (1..=2).contains(&day),
            "day should be 1 or 2 for approx March 20, got {}",
            day
        );
    }

    #[test]
    fn test_gregorian_to_jalali_packed_invalid() {
        // Negative timestamp should still produce a valid date (no panic)
        let packed = gregorian_to_jalali_packed(-1);
        // Should not be 0 (which means error)
        assert_ne!(packed, 0);
    }

    #[test]
    fn test_jalali_to_gregorian_packed() {
        let ts = jalali_to_gregorian_packed(1403, 1, 1);
        assert!(ts > 0);
    }

    #[test]
    fn test_jalali_to_gregorian_packed_invalid() {
        // Invalid date should return i64::MIN (no panic)
        let ts = jalali_to_gregorian_packed(0, 0, 0);
        assert_eq!(ts, i64::MIN);
    }

    #[test]
    fn test_pre_epoch_one_day_before() {
        // 1969-12-31 00:00:00 UTC = -86400000 ms
        let ts = -86_400_000i64;
        let (y, m, d) = timestamp_to_gregorian(ts).unwrap();
        assert_eq!((y, m, d), (1969, 12, 31));
    }

    #[test]
    fn test_pre_epoch_january_1970() {
        // 1969-01-01 00:00:00 UTC ≈ -31536000000 ms (365 days before epoch)
        let ts = -31_536_000_000i64;
        let (y, m, d) = timestamp_to_gregorian(ts).unwrap();
        assert_eq!((y, m, d), (1969, 1, 1));
    }

    #[test]
    fn test_pre_epoch_leap_year_boundary() {
        // 1968 is a leap year. 1968-12-31 should be valid.
        // 1968-12-31 00:00:00 UTC = -365 days * 86400000 = -31536000000 - 86400000
        let ts = -31_622_400_000i64;
        let (y, m, d) = timestamp_to_gregorian(ts).unwrap();
        assert_eq!((y, m, d), (1968, 12, 31));
    }

    // =====================================================================
    // gregorian_to_jalali_date invalid date validation
    // =====================================================================

    #[test]
    fn test_gregorian_to_jalali_invalid_month_zero() {
        let err = gregorian_to_jalali_date(2024, 0, 15).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_invalid_month_13() {
        let err = gregorian_to_jalali_date(2024, 13, 15).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_invalid_day_zero() {
        let err = gregorian_to_jalali_date(2024, 1, 0).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_feb_30_non_leap() {
        // 2023 is not a leap year
        let err = gregorian_to_jalali_date(2023, 2, 30).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_feb_29_non_leap() {
        // 2023 is not a leap year
        let err = gregorian_to_jalali_date(2023, 2, 29).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_feb_29_leap() {
        // 2024 is a leap year - should be valid
        let result = gregorian_to_jalali_date(2024, 2, 29);
        assert!(result.is_ok());
    }

    #[test]
    fn test_gregorian_to_jalali_april_31() {
        // April has 30 days
        let err = gregorian_to_jalali_date(2024, 4, 31).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_june_31() {
        let err = gregorian_to_jalali_date(2024, 6, 31).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_september_31() {
        let err = gregorian_to_jalali_date(2024, 9, 31).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }

    #[test]
    fn test_gregorian_to_jalali_november_31() {
        let err = gregorian_to_jalali_date(2024, 11, 31).unwrap_err();
        match err {
            HesabyarError::CalendarError { detail } => {
                assert!(detail.contains("Invalid Gregorian date"))
            }
            _ => panic!("Expected CalendarError"),
        }
    }
}
