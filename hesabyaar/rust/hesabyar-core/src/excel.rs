use rust_xlsxwriter::{Color, Format, FormatAlign, FormatBorder, Workbook};

use crate::models::HesabyarError;

/// A single cell in the spreadsheet.
#[derive(Debug, Clone, uniffi::Record)]
pub struct Cell {
    /// Pre-formatted string value (already localized to Persian).
    pub value: String,
    /// Whether this cell should be bold.
    pub bold: bool,
}

/// A sheet to include in the workbook.
#[derive(Debug, Clone, uniffi::Record)]
pub struct SheetData {
    /// Sheet name (e.g., "همه تراکنش‌ها").
    pub name: String,
    /// Column headers (first row, styled with dark blue background).
    pub headers: Vec<String>,
    /// Data rows. Each inner Vec is one row of cells.
    pub rows: Vec<Vec<Cell>>,
    /// Optional summary row (e.g., "مجموع:" + total amount).
    pub summary_row: Option<Vec<Cell>>,
}

/// Complete workbook data passed from Kotlin to Rust.
#[derive(Debug, Clone, uniffi::Record)]
pub struct WorkbookData {
    pub sheets: Vec<SheetData>,
}

/// Generate an XLSX workbook from pre-formatted sheet data.
///
/// Returns raw XLSX bytes (a valid .xlsx file).
pub fn generate_excel(workbook: &WorkbookData) -> Result<Vec<u8>, HesabyarError> {
    let mut wb = Workbook::new();

    // Define styles
    let header_format = Format::new()
        .set_bold()
        .set_font_size(12)
        .set_background_color(Color::RGB(0x1F3864))
        .set_font_color(Color::RGB(0xFFFFFF))
        .set_border(FormatBorder::Thin)
        .set_align(FormatAlign::Center);

    let cell_format = Format::new().set_border(FormatBorder::Thin);

    let summary_label_format = Format::new()
        .set_bold()
        .set_font_size(11)
        .set_border(FormatBorder::Thin);

    let summary_value_format = Format::new()
        .set_bold()
        .set_font_size(11)
        .set_border(FormatBorder::Thin)
        .set_align(FormatAlign::Right);

    for sheet_data in &workbook.sheets {
        let sheet = wb.add_worksheet();
        sheet
            .set_name(&sheet_data.name)
            .map_err(|e| HesabyarError::ValidationError {
                detail: format!("Failed to set sheet name '{}': {}", sheet_data.name, e),
            })?;

        // RTL layout for Persian
        sheet.set_right_to_left(true);

        // Freeze the header row (best-effort; not fatal if unsupported)
        let _ = sheet.set_freeze_panes(1, 0);

        // Write headers
        for (col_idx, header) in sheet_data.headers.iter().enumerate() {
            let col = col_idx as u16;
            sheet
                .write_string_with_format(0, col, header.as_str(), &header_format)
                .map_err(|e| HesabyarError::ValidationError {
                    detail: format!("Header write error: {}", e),
                })?;
        }

        // Auto-calculate column widths from headers + data
        let num_cols = sheet_data.headers.len();
        let mut col_widths: Vec<usize> = sheet_data.headers.iter().map(|h| h.len()).collect();

        for row in &sheet_data.rows {
            for (col_idx, cell) in row.iter().enumerate() {
                if col_idx < num_cols {
                    let len = cell.value.len();
                    if len > col_widths[col_idx] {
                        col_widths[col_idx] = len;
                    }
                }
            }
        }

        if let Some(ref summary) = sheet_data.summary_row {
            for (col_idx, cell) in summary.iter().enumerate() {
                if col_idx < num_cols {
                    let len = cell.value.len();
                    if len > col_widths[col_idx] {
                        col_widths[col_idx] = len;
                    }
                }
            }
        }

        // Set column widths (min 8, +4 padding)
        for (col_idx, width) in col_widths.iter().enumerate() {
            let w = (*width).max(8) + 4;
            sheet
                .set_column_width(col_idx as u16, w as f64)
                .map_err(|e| HesabyarError::ValidationError {
                    detail: format!("Column width error: {}", e),
                })?;
        }

        // Write data rows
        for (row_idx, row) in sheet_data.rows.iter().enumerate() {
            let excel_row = (row_idx + 1) as u32;
            for (col_idx, cell) in row.iter().enumerate() {
                let col = col_idx as u16;
                let fmt = if cell.bold {
                    &summary_value_format
                } else {
                    &cell_format
                };
                sheet
                    .write_string_with_format(excel_row, col, cell.value.as_str(), fmt)
                    .map_err(|e| HesabyarError::ValidationError {
                        detail: format!("Cell write error: {}", e),
                    })?;
            }
        }

        // Write summary row if present
        if let Some(ref summary) = sheet_data.summary_row {
            let summary_row = (sheet_data.rows.len() + 1) as u32;
            for (col_idx, cell) in summary.iter().enumerate() {
                let col = col_idx as u16;
                let fmt = if cell.bold {
                    &summary_value_format
                } else {
                    &summary_label_format
                };
                sheet
                    .write_string_with_format(summary_row, col, cell.value.as_str(), fmt)
                    .map_err(|e| HesabyarError::ValidationError {
                        detail: format!("Summary write error: {}", e),
                    })?;
            }
        }
    }

    // Serialize to bytes
    let buffer = wb
        .save_to_buffer()
        .map_err(|e| HesabyarError::ValidationError {
            detail: format!("XLSX serialization failed: {}", e),
        })?;

    Ok(buffer)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_cell(value: &str) -> Cell {
        Cell {
            value: value.to_string(),
            bold: false,
        }
    }

    fn make_bold_cell(value: &str) -> Cell {
        Cell {
            value: value.to_string(),
            bold: true,
        }
    }

    #[test]
    fn test_empty_workbook_produces_valid_xlsx() {
        let wb = WorkbookData { sheets: vec![] };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 4);
        assert_eq!(bytes[0], 0x50); // P
        assert_eq!(bytes[1], 0x4B); // K
    }

    #[test]
    fn test_single_sheet_with_headers_and_rows() {
        let wb = WorkbookData {
            sheets: vec![SheetData {
                name: "Test".to_string(),
                headers: vec!["Name".to_string(), "Amount".to_string()],
                rows: vec![
                    vec![make_cell("Ali"), make_cell("1000")],
                    vec![make_cell("Reza"), make_cell("2000")],
                ],
                summary_row: None,
            }],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 100);
        assert_eq!(bytes[0], 0x50);
    }

    #[test]
    fn test_multiple_sheets() {
        let wb = WorkbookData {
            sheets: vec![
                SheetData {
                    name: "Sheet1".to_string(),
                    headers: vec!["A".to_string()],
                    rows: vec![vec![make_cell("1")]],
                    summary_row: None,
                },
                SheetData {
                    name: "Sheet2".to_string(),
                    headers: vec!["B".to_string()],
                    rows: vec![vec![make_cell("2")]],
                    summary_row: None,
                },
            ],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 100);
    }

    #[test]
    fn test_summary_row() {
        let wb = WorkbookData {
            sheets: vec![SheetData {
                name: "Test".to_string(),
                headers: vec!["Item".to_string(), "Total".to_string()],
                rows: vec![vec![make_cell("Food"), make_cell("5000")]],
                summary_row: Some(vec![make_bold_cell("Total:"), make_bold_cell("5000")]),
            }],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 100);
    }

    #[test]
    fn test_persian_text() {
        let wb = WorkbookData {
            sheets: vec![SheetData {
                name: "تراکنش\u{200C}ها".to_string(),
                headers: vec!["ردیف".to_string(), "مبلغ".to_string()],
                rows: vec![vec![make_cell("1"), make_cell("۵,۰۰۰ تومان")]],
                summary_row: None,
            }],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 100);
    }

    #[test]
    fn test_special_characters() {
        let wb = WorkbookData {
            sheets: vec![SheetData {
                name: "Test".to_string(),
                headers: vec!["Data".to_string()],
                rows: vec![vec![make_cell("A & B < C > D \"E'")]],
                summary_row: None,
            }],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 100);
    }

    #[test]
    fn test_large_dataset() {
        let rows: Vec<Vec<Cell>> = (0..1000)
            .map(|i| vec![make_cell(&i.to_string()), make_cell("value")])
            .collect();

        let wb = WorkbookData {
            sheets: vec![SheetData {
                name: "Large".to_string(),
                headers: vec!["ID".to_string(), "Value".to_string()],
                rows,
                summary_row: None,
            }],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 10_000);
    }

    #[test]
    fn test_bold_cells() {
        let wb = WorkbookData {
            sheets: vec![SheetData {
                name: "Test".to_string(),
                headers: vec!["Col".to_string()],
                rows: vec![vec![make_bold_cell("bold")]],
                summary_row: None,
            }],
        };
        let bytes = generate_excel(&wb).unwrap();
        assert!(bytes.len() > 100);
    }
}
