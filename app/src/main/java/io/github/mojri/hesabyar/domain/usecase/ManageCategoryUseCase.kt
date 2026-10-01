package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import kotlinx.coroutines.flow.Flow

class ManageCategoryUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val allCategories: Flow<List<Category>> = repository.allCategories

  fun getCategoriesByType(type: CategoryType): Flow<List<Category>> = repository.getCategoriesByType(type.name)

  suspend fun getCategoryById(id: Long): Category? = repository.getCategoryById(id)

  suspend fun getCategoryByKey(key: String): Category? = repository.getCategoryByKey(key)

  suspend fun addCategory(
    name: String,
    key: String,
    icon: String,
    color: Long,
    type: CategoryType
  ): Long = repository.insertCategory(Category(name = name, key = key, icon = icon, color = color, type = type))

  suspend fun updateCategory(category: Category) = repository.updateCategory(category)

  suspend fun deleteCategory(category: Category) = repository.deleteCategory(category)
}
