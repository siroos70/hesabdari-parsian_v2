package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer

internal data class PersonKeyMaps(
  val sourceIdToKey: Map<Long, String>,
  val keyToLocalId: Map<String, Long>,
  // Legacy backups (empty persons array) carry identities only as
  // denormalized names; their null-id rows must resolve by name. Modern
  // payloads keep the unlinked guarantee instead: a null personId is the
  // deliberate state deletePerson leaves behind, and re-linking it by a name
  // match would undo the user's unlink decision.
  val allowNullIdNameFallback: Boolean = true
)

internal fun resolvePersonId(
  sourcePersonId: Long?,
  fallbackName: String?,
  maps: PersonKeyMaps
): Long? {
  val resolvedById =
    sourcePersonId
      ?.let { maps.sourceIdToKey[it] }
      ?.takeIf { it.isNotEmpty() }
      ?.let { maps.keyToLocalId[it] }
  // An id with no usable key (referenced by a loan/transaction but not
  // carried in the persons list) must not orphan the link: fall through to
  // name resolution below instead of returning null outright.
  if (resolvedById != null) return resolvedById
  // Legacy backups (empty persons array) resolve by name; modern payloads
  // keep null-id rows unlinked (see PersonKeyMaps.allowNullIdNameFallback).
  val nameFallbackAllowed = sourcePersonId != null || maps.allowNullIdNameFallback
  val fallbackKey =
    fallbackName
      ?.takeIf { nameFallbackAllowed }
      ?.let { PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(it)) }
      ?.takeIf { it.isNotEmpty() }
  return fallbackKey?.let { maps.keyToLocalId[it] }
}
