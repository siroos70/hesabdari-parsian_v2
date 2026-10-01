package io.github.mojri.hesabyar.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameNormalizerTest {
  @Test
  fun normalizeFoldsArabicYehKafAndTehMarbutaToPersian() {
    assertEquals("علی", PersonNameNormalizer.normalize("علي"))
    assertEquals("عکبر", PersonNameNormalizer.normalize("عكبر"))
    assertEquals("حوزه", PersonNameNormalizer.normalize("حوزة"))
  }

  @Test
  fun normalizeFoldsAlefVariantsToPlainAlef() {
    assertEquals("ابو", PersonNameNormalizer.normalize("أبو"))
    assertEquals("ایران", PersonNameNormalizer.normalize("إيران"))
    assertEquals("اب", PersonNameNormalizer.normalize("آب"))
    assertEquals("الاسلام", PersonNameNormalizer.normalize("ٱلاسلام"))
  }

  @Test
  fun normalizeFoldsAlefMaksuraYehHamzaAndWawHamza() {
    assertEquals("علی", PersonNameNormalizer.normalize("على"))
    assertEquals("بی", PersonNameNormalizer.normalize("بئ"))
    assertEquals("سو", PersonNameNormalizer.normalize("سؤ"))
  }

  @Test
  fun normalizeFoldsLamAlefLigaturePresentationFormsToLamAlef() {
    val isolated = String(Character.toChars(0xFEFB))
    val final = String(Character.toChars(0xFEFC))
    val lamAlef = "لا"
    assertEquals(lamAlef, PersonNameNormalizer.normalize(isolated))
    assertEquals(lamAlef, PersonNameNormalizer.normalize(final))
    // Hamza-bearing ligatures fold away the hamza to the canonical form.
    for (code in intArrayOf(0xFEF5, 0xFEF6, 0xFEF7, 0xFEF8, 0xFEF9, 0xFEFA)) {
      assertEquals(lamAlef, PersonNameNormalizer.normalize(String(Character.toChars(code))))
    }
  }

  @Test
  fun normalizeFoldsSingleLetterPresentationFormsToBaseLetters() {
    // Isolated-form sample of every base letter used in Persian/Arabic names
    // (final/initial/medial variants of the same letters fold through the
    // same map entries, one test per letter covers the shared rule). PDF-
    // pasted text carries these shaped glyphs instead of the base letters;
    // without the fold two visually identical names get different dedup keys.
    val forms =
      listOf(
        0xFB50 to "ا", // alef wasla isolated (folds through the alef-wasla rule)
        0xFE8D to "ا", // alef isolated
        0xFE8E to "ا", // alef final
        0xFE8F to "ب", // beh isolated
        0xFE95 to "ت", // teh isolated
        0xFE99 to "ث", // theh isolated
        0xFE9D to "ج", // jeem isolated
        0xFEA1 to "ح", // hah isolated
        0xFEA5 to "خ", // khah isolated
        0xFEA9 to "د", // dal isolated
        0xFEAB to "ذ", // thal isolated
        0xFEAD to "ر", // reh isolated
        0xFEAF to "ز", // zain isolated
        0xFEB1 to "س", // seen isolated
        0xFEB5 to "ش", // sheen isolated
        0xFEB9 to "ص", // sad isolated
        0xFEBD to "ض", // dad isolated
        0xFEC1 to "ط", // tah isolated
        0xFEC5 to "ظ", // zah isolated
        0xFEC9 to "ع", // ain isolated
        0xFECD to "غ", // ghain isolated
        0xFED1 to "ف", // feh isolated
        0xFED5 to "ق", // qaf isolated
        0xFEDD to "ل", // lam isolated
        0xFEE1 to "م", // meem isolated
        0xFEE5 to "ن", // noon isolated
        0xFEED to "و", // waw isolated
        0xFE93 to "ه", // teh marbuta isolated (folds to heh)
        0xFEE9 to "ه", // heh isolated
        0xFEEF to "ی", // alef maksura isolated (folds to yeh)
        0xFEF1 to "ی" // yeh isolated
      )
    for ((code, expected) in forms) {
      assertEquals(
        "U+" + Integer.toHexString(code) + " must fold to " + expected,
        expected,
        PersonNameNormalizer.normalize(String(Character.toChars(code)))
      )
    }
  }

  @Test
  fun normalizeFoldsVariantBaseLetterPresentationForms() {
    // Presentation forms of the variant base letters (alef madda/hamza, waw
    // hamza, yeh hamza) fold to the same targets as their base variants, so
    // shaped text shares the key with the typed variants.
    val forms =
      listOf(
        0xFE81 to "ا", // alef with madda isolated
        0xFE82 to "ا", // alef with madda final
        0xFE83 to "ا", // alef with hamza above isolated
        0xFE84 to "ا", // alef with hamza above final
        0xFE85 to "و", // waw with hamza isolated
        0xFE86 to "و", // waw with hamza final
        0xFE87 to "ا", // alef with hamza below isolated
        0xFE88 to "ا", // alef with hamza below final
        0xFE89 to "ی", // yeh with hamza isolated
        0xFE8A to "ی", // yeh with hamza final
        0xFE8B to "ی", // yeh with hamza initial
        0xFE8C to "ی" // yeh with hamza medial
      )
    for ((code, expected) in forms) {
      assertEquals(
        "U+" + Integer.toHexString(code) + " must fold to " + expected,
        expected,
        PersonNameNormalizer.normalize(String(Character.toChars(code)))
      )
    }
  }

  @Test
  fun normalizeFoldsPersianOnlyLetterPresentationForms() {
    // Persian-only letters (peh, tcheh, jeh, keheh, gaf, farsi yeh) have
    // presentation forms in the U+FB50 block; each folds back to its base.
    val forms =
      listOf(
        0xFB56 to "پ", // peh isolated
        0xFB59 to "پ", // peh medial
        0xFB7A to "چ", // tcheh isolated
        0xFB7D to "چ", // tcheh medial
        0xFB8A to "ژ", // jeh isolated
        0xFB8B to "ژ", // jeh final
        0xFB8E to "ک", // keheh isolated
        0xFB91 to "ک", // keheh medial
        0xFB92 to "گ", // gaf isolated
        0xFB95 to "گ", // gaf medial
        0xFBFC to "ی", // farsi yeh isolated
        0xFBFF to "ی", // farsi yeh medial
        0xFBE8 to "ی", // uighur alef maksura initial (folds to yeh)
        0xFBE9 to "ی" // uighur alef maksura medial
      )
    for ((code, expected) in forms) {
      assertEquals(
        "U+" + Integer.toHexString(code) + " must fold to " + expected,
        expected,
        PersonNameNormalizer.normalize(String(Character.toChars(code)))
      )
    }
  }

  @Test
  fun normalizePdfPastedKafSharesKeyWithKeyboardKaf() {
    // The original bug report: keyboard kaf vs PDF-pasted presentation-form
    // kaf produced two records for the visually identical name.
    val keyboardKaf = "اکبر"
    val pdfPastedKaf = "ا" + String(Character.toChars(0xFED9)) + "بر"
    assertEquals(
      PersonNameNormalizer.normalize(keyboardKaf),
      PersonNameNormalizer.normalize(pdfPastedKaf)
    )
  }

  @Test
  fun normalizeArabicVariantKeyEqualsPersianSpellingKey() {
    // The dedup invariant: each Arabic-script variant shares one key with the
    // visually equivalent Persian spelling.
    assertEquals(
      PersonNameNormalizer.normalize("علی"),
      PersonNameNormalizer.normalize("علي")
    )
    assertEquals(
      PersonNameNormalizer.normalize("فا"),
      PersonNameNormalizer.normalize("فأ")
    )
    assertEquals(
      PersonNameNormalizer.normalize("لا"),
      PersonNameNormalizer.normalize(String(Character.toChars(0xFEFB)))
    )
  }

  @Test
  fun normalizeTrimsAndCollapsesInternalWhitespace() {
    assertEquals("علی رضا", PersonNameNormalizer.normalize("  علی   رضا  "))
    assertEquals("علی رضا", PersonNameNormalizer.normalize("\tعلی\nرضا"))
  }

  @Test
  fun normalizeStripsZeroWidthCharacters() {
    assertEquals("علیرضا", PersonNameNormalizer.normalize("علی\u200Cرضا"))
    assertEquals("علیرضا", PersonNameNormalizer.normalize("علی\u200Bرضا"))
  }

  @Test
  fun normalizeLowercasesLatinPartOnly() {
    assertEquals("ali رضا", PersonNameNormalizer.normalize("ALI رضا"))
  }

  @Test
  fun normalizeReturnsEmptyStringForWhitespaceOnlyInput() {
    assertEquals("", PersonNameNormalizer.normalize("   "))
  }

  @Test
  fun normalizeReturnsEmptyStringForZeroWidthOnlyInput() {
    assertEquals("", PersonNameNormalizer.normalize("\u200B"))
    assertEquals("", PersonNameNormalizer.normalize("\u200C\u200D\uFEFF\u2060"))
  }

  @Test
  fun displayFormKeepsFirstTrimmedOriginalSpelling() {
    assertEquals("  علی  ".trim(), PersonNameNormalizer.displayForm("  علی  "))
    assertEquals("ALI", PersonNameNormalizer.displayForm("ALI"))
  }

  @Test
  fun displayFormPreservesZeroWidthCharacters() {
    // D4: display preserves ZWNJ/zero-width; only normalize strips them.
    // A zero-width-only input stays non-empty after trim but normalizes to empty.
    assertEquals("\u200B", PersonNameNormalizer.displayForm("\u200B"))
    assertEquals("\u200Bعلی\u200C", PersonNameNormalizer.displayForm("\u200Bعلی\u200C"))
    assertEquals("علی\u200Cرضا", PersonNameNormalizer.displayForm("علی\u200Cرضا"))
  }
}
