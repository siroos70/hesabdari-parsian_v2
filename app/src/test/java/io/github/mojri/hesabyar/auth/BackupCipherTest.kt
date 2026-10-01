package io.github.mojri.hesabyar.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupCipherTest {
  private val salt = BackupCipher.generateSalt()
  private val passphrase = "test-passphrase-123"
  private val key = BackupCipher.deriveKey(passphrase, salt)

  private val account1IbanAad = BackupCipher.accountFieldAad(1L, "iban")
  private val account1CardNumberAad = BackupCipher.accountFieldAad(1L, "cardNumber")
  private val account2IbanAad = BackupCipher.accountFieldAad(2L, "iban")

  // Clearly synthetic stand-ins for card-number / IBAN field values — never
  // realistic PANs, so the test data cannot be mistaken for real account data.
  private val syntheticCardNumber = "0000-0000-0000-0000"
  private val syntheticIban = "IR00 0000 0000 0000 0000 0000 00"

  @Test
  fun encryptThenDecryptRecoversOriginalValue() {
    val plaintext = syntheticCardNumber
    val encrypted = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    val decrypted = BackupCipher.decrypt(encrypted, key, account1IbanAad)
    assertEquals("Decrypted value must match original", plaintext, decrypted)
  }

  @Test(expected = AEADBadTagException::class)
  fun wrongPassphraseThrowsAEADBadTagException() {
    val plaintext = syntheticCardNumber
    val encrypted = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    val wrongKey = BackupCipher.deriveKey("wrong-passphrase", salt)
    BackupCipher.decrypt(encrypted, wrongKey, account1IbanAad)
  }

  @Test(expected = AEADBadTagException::class)
  fun decryptUnderDifferentAccountIdAadThrowsAEADBadTagException() {
    val plaintext = syntheticIban
    val encrypted = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    // Same key and ciphertext, but the AAD was bound to accountId=1 while
    // decrypting as accountId=2 — a cross-account substitution must fail.
    BackupCipher.decrypt(encrypted, key, account2IbanAad)
  }

  @Test(expected = AEADBadTagException::class)
  fun decryptUnderDifferentFieldAadThrowsAEADBadTagException() {
    val plaintext = syntheticIban
    val encrypted = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    // Same key, ciphertext and account, but the AAD was bound to the "iban"
    // field while decrypting as "cardNumber" — a cross-field substitution
    // (iban ciphertext moved into the cardNumber slot) must fail.
    BackupCipher.decrypt(encrypted, key, account1CardNumberAad)
  }

  @Test
  fun differentSaltsProduceDifferentCiphertext() {
    val plaintext = syntheticIban
    val salt2 = BackupCipher.generateSalt()
    val key2 = BackupCipher.deriveKey(passphrase, salt2)
    val encrypted1 = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    val encrypted2 = BackupCipher.encrypt(plaintext, key2, account1IbanAad)
    assertNotEquals("Different salts must produce different ciphertext", encrypted1, encrypted2)
    // Both must still decrypt correctly
    assertEquals(
      "ciphertext from salt A must still decrypt with key A",
      plaintext,
      BackupCipher.decrypt(encrypted1, key, account1IbanAad)
    )
    assertEquals(
      "ciphertext from salt B must still decrypt with key B",
      plaintext,
      BackupCipher.decrypt(encrypted2, key2, account1IbanAad)
    )
  }

  @Test
  fun ivIsRandomPerEncryption() {
    val plaintext = syntheticCardNumber
    val encrypted1 = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    val encrypted2 = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    assertNotEquals("Same plaintext encrypted twice must differ (different IVs)", encrypted1, encrypted2)
    // Both must decrypt to the same plaintext
    assertEquals(
      "ciphertext from first IV must decrypt to the original",
      plaintext,
      BackupCipher.decrypt(encrypted1, key, account1IbanAad)
    )
    assertEquals(
      "ciphertext from second IV must decrypt to the original",
      plaintext,
      BackupCipher.decrypt(encrypted2, key, account1IbanAad)
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun decryptingTruncatedCiphertextThrowsIllegalArgumentException() {
    // Base64 of just 5 bytes — shorter than IV_LENGTH_BYTES + 1
    val tooShort =
      java.util.Base64
        .getEncoder()
        .encodeToString(byteArrayOf(1, 2, 3, 4, 5))
    BackupCipher.decrypt(tooShort, key, account1IbanAad)
  }

  @Test(expected = IllegalArgumentException::class)
  fun decryptingTruncatedTagThrowsIllegalArgumentException() {
    // Start from a valid ciphertext and cut the GCM tag from 16 to 8 bytes
    // (IV 12 + half tag 8 = 20 bytes). 20 bytes clears any "IV + 1 byte" check,
    // so only a full IV + tag-length validation rejects it at the require layer —
    // the Cipher provider would otherwise throw a different exception type.
    val encrypted = BackupCipher.encrypt("621986101234567890123456", key, account1IbanAad)
    val truncated =
      java.util.Base64
        .getDecoder()
        .decode(encrypted)
        .copyOfRange(0, 20)
    BackupCipher.decrypt(
      java.util.Base64
        .getEncoder()
        .encodeToString(truncated),
      key,
      account1IbanAad
    )
  }

  @Test
  fun decryptingEmptyPlaintextRoundTripsAtTheBoundary() {
    // Empty plaintext: IV (12) + no ciphertext + full GCM tag (16) = 28 bytes,
    // exactly the validation minimum — must still decrypt to an empty string.
    val encrypted = BackupCipher.encrypt("", key, account1IbanAad)
    assertEquals("Empty plaintext must round-trip", "", BackupCipher.decrypt(encrypted, key, account1IbanAad))
  }

  @Test(expected = AEADBadTagException::class)
  fun decryptingTamperedButWellFormedCiphertextThrowsAEADBadTagException() {
    val plaintext = syntheticCardNumber
    val encrypted = BackupCipher.encrypt(plaintext, key, account1IbanAad)
    // Tamper: flip a character in the base64 to change a byte in the ciphertext
    // The encrypted string is long enough that changing one char alters a byte
    // in the ciphertext portion (after the IV), invalidating the GCM tag.
    val tampered =
      encrypted.substring(0, encrypted.length - 1) +
        if (encrypted.last() == 'A') "B" else "A"
    BackupCipher.decrypt(tampered, key, account1IbanAad)
  }

  @Test
  fun encryptOrNullReturnsNullForNullInput() {
    val result = BackupCipher.encryptOrNull(null, key, account1IbanAad)
    assertTrue("Null input must return JSONObject.NULL", result === org.json.JSONObject.NULL)
  }

  @Test
  fun decryptOrNullReturnsNullForNullInput() {
    val result = BackupCipher.decryptOrNull(org.json.JSONObject.NULL, key, account1IbanAad)
    assertEquals("JSONObject.NULL input must return null", null, result)
  }

  @Test
  fun accountFieldAadBindsAccountIdAndFieldName() {
    val aad = BackupCipher.accountFieldAad(7L, "cardNumber")
    assertTrue("AAD must bind the account id", aad.contains("accountId:7"))
    assertTrue("AAD must bind the field name", aad.contains("field:cardNumber"))
  }

  @Test
  fun roundTripWithNullFieldsInAccountContext() {
    val realCard = syntheticCardNumber
    val realIban = syntheticIban

    // Simulate account export with some null fields, each encrypted under its
    // own field's AAD context (as exportBackupJson does)
    val cardEncrypted = BackupCipher.encryptOrNull(realCard, key, account1CardNumberAad)
    val accountNumEncrypted = BackupCipher.encryptOrNull(null, key, account1CardNumberAad)
    val ibanEncrypted = BackupCipher.encryptOrNull(realIban, key, account1IbanAad)

    // Encrypted values are strings, null stays JSONObject.NULL
    assertTrue("Card should be encrypted string", cardEncrypted is String)
    assertTrue("Account number should be JSONObject.NULL", accountNumEncrypted === org.json.JSONObject.NULL)
    assertTrue("IBAN should be encrypted string", ibanEncrypted is String)

    // Decrypt back under the matching AAD context
    assertEquals("Card must recover", realCard, BackupCipher.decryptOrNull(cardEncrypted, key, account1CardNumberAad))
    assertEquals(
      "Null account number must stay null",
      null,
      BackupCipher.decryptOrNull(accountNumEncrypted, key, account1CardNumberAad)
    )
    assertEquals("IBAN must recover", realIban, BackupCipher.decryptOrNull(ibanEncrypted, key, account1IbanAad))
  }
}
