package io.github.mojri.hesabyar.domain.utils

/**
 * Normalizes person names into dedup keys (plans/011 §D4).
 *
 * ADR-001 permanent fallback ("Person-name normalization"): Room migrations
 * cannot load the native library, so backfill runs this Kotlin util inside
 * the migration. Every runtime create/rename path reuses it, so dedup
 * semantics never drift between migration and runtime.
 *
 * **Case folding contract:** [Char.lowercaseChar] is applied to every
 * retained code point, not only Latin letters. The Persian/Arabic script
 * has no case, so the fold is a no-op for the dominant script in this
 * app. Any future script with case (Cyrillic, Greek, etc.) will be
 * lowercased the same way. Callers that need to preserve case in
 * non-Latin scripts must do so before calling this function.
 */
object PersonNameNormalizer {
  // Code points: ZWSP(200B), ZWNJ(200C), ZWJ(200D), word joiner(2060), BOM(FEFF).
  private val zeroWidthCodes = setOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF)

  // Arabic-script variants fold to their Persian counterparts so visually
  // identical names share one dedup key. Keyed by code point so lookups in
  // [normalize] are safe for supplementary-plane chars.
  private val arabicToPersian: Map<Int, String> =
    mapOf(
      'ي'.code to "ی", // 064A arabic yeh
      'ى'.code to "ی", // 0649 alef maksura
      'ئ'.code to "ی", // 0626 yeh with hamza above
      'ك'.code to "ک", // 0643 arabic kaf
      'ة'.code to "ه", // 0629 teh marbuta
      'أ'.code to "ا", // 0623 alef with hamza above
      'إ'.code to "ا", // 0625 alef with hamza below
      'آ'.code to "ا", // 0622 alef with madda above
      'ٱ'.code to "ا", // 0671 alef wasla
      'ؤ'.code to "و", // 0624 waw with hamza
      // Lam-alef presentation-form ligatures (isolated and final forms) all
      // collapse to the canonical lam + alef sequence.
      'ﻻ'.code to "لا", // FEFB
      'ﻼ'.code to "لا", // FEFC
      'ﻹ'.code to "لا", // FEF9 (hamza folds away)
      'ﻺ'.code to "لا", // FEFA
      'ﻷ'.code to "لا", // FEF7
      'ﻸ'.code to "لا", // FEF8
      'ﻵ'.code to "لا", // FEF5
      'ﻶ'.code to "لا", // FEF6
      // Presentation forms of the individual letters (isolated/final/initial/
      // medial, U+FB50–U+FEFF block). Text pasted from PDFs or shaped-render
      // copy sources can carry these instead of the base letters; without the
      // fold they produce colliding display names with different dedup keys.
      'ﭐ'.code to "ا", // FB50 alef wasla isolated
      'ﭑ'.code to "ا", // FB51 alef wasla final
      'ﭖ'.code to "پ", // FB56 peh isolated
      'ﭗ'.code to "پ", // FB57 peh final
      'ﭘ'.code to "پ", // FB58 peh initial
      'ﭙ'.code to "پ", // FB59 peh medial
      'ﭺ'.code to "چ", // FB7A tcheh isolated
      'ﭻ'.code to "چ", // FB7B tcheh final
      'ﭼ'.code to "چ", // FB7C tcheh initial
      'ﭽ'.code to "چ", // FB7D tcheh medial
      'ﮊ'.code to "ژ", // FB8A jeh isolated
      'ﮋ'.code to "ژ", // FB8B jeh final
      'ﮎ'.code to "ک", // FB8E keheh isolated
      'ﮏ'.code to "ک", // FB8F keheh final
      'ﮐ'.code to "ک", // FB90 keheh initial
      'ﮑ'.code to "ک", // FB91 keheh medial
      'ﮒ'.code to "گ", // FB92 gaf isolated
      'ﮓ'.code to "گ", // FB93 gaf final
      'ﮔ'.code to "گ", // FB94 gaf initial
      'ﮕ'.code to "گ", // FB95 gaf medial
      'ﯨ'.code to "ی", // FBE8 alef maksura initial
      'ﯩ'.code to "ی", // FBE9 alef maksura medial
      'ﯼ'.code to "ی", // FBFC farsi yeh isolated
      'ﯽ'.code to "ی", // FBFD farsi yeh final
      'ﯾ'.code to "ی", // FBFE farsi yeh initial
      'ﯿ'.code to "ی", // FBFF farsi yeh medial
      'ﺀ'.code to "ء", // FE80 hamza isolated
      // Presentation forms of the variant base letters themselves: the base
      // (أ إ آ ؤ ئ) already folds through the rules above, so their shaped
      // forms target the same canonical letters — dedup keys match the
      // keyboard-typed variants either way.
      'ﺁ'.code to "ا", // FE81 alef with madda isolated
      'ﺂ'.code to "ا", // FE82 alef with madda final
      'ﺃ'.code to "ا", // FE83 alef with hamza above isolated
      'ﺄ'.code to "ا", // FE84 alef with hamza above final
      'ﺅ'.code to "و", // FE85 waw with hamza isolated
      'ﺆ'.code to "و", // FE86 waw with hamza final
      'ﺇ'.code to "ا", // FE87 alef with hamza below isolated
      'ﺈ'.code to "ا", // FE88 alef with hamza below final
      'ﺉ'.code to "ی", // FE89 yeh with hamza isolated
      'ﺊ'.code to "ی", // FE8A yeh with hamza final
      'ﺋ'.code to "ی", // FE8B yeh with hamza initial
      'ﺌ'.code to "ی", // FE8C yeh with hamza medial
      'ﺍ'.code to "ا", // FE8D alef isolated
      'ﺎ'.code to "ا", // FE8E alef final
      'ﺏ'.code to "ب", // FE8F beh isolated
      'ﺐ'.code to "ب", // FE90 beh final
      'ﺑ'.code to "ب", // FE91 beh initial
      'ﺒ'.code to "ب", // FE92 beh medial
      'ﺓ'.code to "ه", // FE93 teh marbuta isolated
      'ﺔ'.code to "ه", // FE94 teh marbuta final
      'ﺕ'.code to "ت", // FE95 teh isolated
      'ﺖ'.code to "ت", // FE96 teh final
      'ﺗ'.code to "ت", // FE97 teh initial
      'ﺘ'.code to "ت", // FE98 teh medial
      'ﺙ'.code to "ث", // FE99 theh isolated
      'ﺚ'.code to "ث", // FE9A theh final
      'ﺛ'.code to "ث", // FE9B theh initial
      'ﺜ'.code to "ث", // FE9C theh medial
      'ﺝ'.code to "ج", // FE9D jeem isolated
      'ﺞ'.code to "ج", // FE9E jeem final
      'ﺟ'.code to "ج", // FE9F jeem initial
      'ﺠ'.code to "ج", // FEA0 jeem medial
      'ﺡ'.code to "ح", // FEA1 hah isolated
      'ﺢ'.code to "ح", // FEA2 hah final
      'ﺣ'.code to "ح", // FEA3 hah initial
      'ﺤ'.code to "ح", // FEA4 hah medial
      'ﺥ'.code to "خ", // FEA5 khah isolated
      'ﺦ'.code to "خ", // FEA6 khah final
      'ﺧ'.code to "خ", // FEA7 khah initial
      'ﺨ'.code to "خ", // FEA8 khah medial
      'ﺩ'.code to "د", // FEA9 dal isolated
      'ﺪ'.code to "د", // FEAA dal final
      'ﺫ'.code to "ذ", // FEAB thal isolated
      'ﺬ'.code to "ذ", // FEAC thal final
      'ﺭ'.code to "ر", // FEAD reh isolated
      'ﺮ'.code to "ر", // FEAE reh final
      'ﺯ'.code to "ز", // FEAF zain isolated
      'ﺰ'.code to "ز", // FEB0 zain final
      'ﺱ'.code to "س", // FEB1 seen isolated
      'ﺲ'.code to "س", // FEB2 seen final
      'ﺳ'.code to "س", // FEB3 seen initial
      'ﺴ'.code to "س", // FEB4 seen medial
      'ﺵ'.code to "ش", // FEB5 sheen isolated
      'ﺶ'.code to "ش", // FEB6 sheen final
      'ﺷ'.code to "ش", // FEB7 sheen initial
      'ﺸ'.code to "ش", // FEB8 sheen medial
      'ﺹ'.code to "ص", // FEB9 sad isolated
      'ﺺ'.code to "ص", // FEBA sad final
      'ﺻ'.code to "ص", // FEBB sad initial
      'ﺼ'.code to "ص", // FEBC sad medial
      'ﺽ'.code to "ض", // FEBD dad isolated
      'ﺾ'.code to "ض", // FEBE dad final
      'ﺿ'.code to "ض", // FEBF dad initial
      'ﻀ'.code to "ض", // FEC0 dad medial
      'ﻁ'.code to "ط", // FEC1 tah isolated
      'ﻂ'.code to "ط", // FEC2 tah final
      'ﻃ'.code to "ط", // FEC3 tah initial
      'ﻄ'.code to "ط", // FEC4 tah medial
      'ﻅ'.code to "ظ", // FEC5 zah isolated
      'ﻆ'.code to "ظ", // FEC6 zah final
      'ﻇ'.code to "ظ", // FEC7 zah initial
      'ﻈ'.code to "ظ", // FEC8 zah medial
      'ﻉ'.code to "ع", // FEC9 ain isolated
      'ﻊ'.code to "ع", // FECA ain final
      'ﻋ'.code to "ع", // FECB ain initial
      'ﻌ'.code to "ع", // FECC ain medial
      'ﻍ'.code to "غ", // FECD ghain isolated
      'ﻎ'.code to "غ", // FECE ghain final
      'ﻏ'.code to "غ", // FECF ghain initial
      'ﻐ'.code to "غ", // FED0 ghain medial
      'ﻑ'.code to "ف", // FED1 feh isolated
      'ﻒ'.code to "ف", // FED2 feh final
      'ﻓ'.code to "ف", // FED3 feh initial
      'ﻔ'.code to "ف", // FED4 feh medial
      'ﻕ'.code to "ق", // FED5 qaf isolated
      'ﻖ'.code to "ق", // FED6 qaf final
      'ﻗ'.code to "ق", // FED7 qaf initial
      'ﻘ'.code to "ق", // FED8 qaf medial
      'ﻙ'.code to "ک", // FED9 kaf isolated
      'ﻚ'.code to "ک", // FEDA kaf final
      'ﻛ'.code to "ک", // FEDB kaf initial
      'ﻜ'.code to "ک", // FEDC kaf medial
      'ﻝ'.code to "ل", // FEDD lam isolated
      'ﻞ'.code to "ل", // FEDE lam final
      'ﻟ'.code to "ل", // FEDF lam initial
      'ﻠ'.code to "ل", // FEE0 lam medial
      'ﻡ'.code to "م", // FEE1 meem isolated
      'ﻢ'.code to "م", // FEE2 meem final
      'ﻣ'.code to "م", // FEE3 meem initial
      'ﻤ'.code to "م", // FEE4 meem medial
      'ﻥ'.code to "ن", // FEE5 noon isolated
      'ﻦ'.code to "ن", // FEE6 noon final
      'ﻧ'.code to "ن", // FEE7 noon initial
      'ﻨ'.code to "ن", // FEE8 noon medial
      'ﻩ'.code to "ه", // FEE9 heh isolated
      'ﻪ'.code to "ه", // FEEA heh final
      'ﻫ'.code to "ه", // FEEB heh initial
      'ﻬ'.code to "ه", // FEEC heh medial
      'ﻭ'.code to "و", // FEED waw isolated
      'ﻮ'.code to "و", // FEEE waw final
      'ﻯ'.code to "ی", // FEEF alef maksura isolated
      'ﻰ'.code to "ی", // FEF0 alef maksura final
      'ﻱ'.code to "ی", // FEF1 yeh isolated
      'ﻲ'.code to "ی", // FEF2 yeh final
      'ﻳ'.code to "ی", // FEF3 yeh initial
      'ﻴ'.code to "ی" // FEF4 yeh medial
    )

  /**
   * Collapses spelling variants to one dedup key: trims, collapses internal
   * whitespace to single spaces, strips zero-width characters, folds Arabic
   * variants to Persian, lowercases the retained code points (see contract
   * note on the class). Iterates by code point so supplementary-plane
   * case variants (e.g. Deseret) fold correctly.
   */
  fun normalize(name: String): String {
    var pendingSpace = false
    return buildString(name.length) {
      var i = 0
      while (i < name.length) {
        val cp = name.codePointAt(i)
        when (val folded = arabicToPersian[cp]) {
          null -> {
            when {
              cp in zeroWidthCodes -> Unit
              Character.isWhitespace(cp) -> pendingSpace = length > 0
              else -> {
                if (pendingSpace && length > 0) append(' ')
                pendingSpace = false
                appendCodePoint(Character.toLowerCase(cp))
              }
            }
          }
          else -> {
            // Fold targets are already in canonical Persian form; append them
            // verbatim (ligature targets are two chars, e.g. lam-alef).
            if (pendingSpace && length > 0) append(' ')
            pendingSpace = false
            append(folded)
          }
        }
        i += Character.charCount(cp)
      }
    }
  }

  /**
   * Display form of a raw input: trims outer whitespace only. Zero-width
   * characters are preserved so Persian ZWNJ spelling (e.g. "می‌روم") is
   * not altered; they are stripped only for the dedup key in [normalize]
   * (plans/011 §D4 — first trimmed original is the display name).
   * A name consisting only of zero-width characters remains non-empty here
   * but normalizes to empty and is rejected by callers; see
   * [HesabyarRepository.upsertPerson] and [HesabyarRepository.renamePerson].
   */
  fun displayForm(name: String): String = name.trim()
}
