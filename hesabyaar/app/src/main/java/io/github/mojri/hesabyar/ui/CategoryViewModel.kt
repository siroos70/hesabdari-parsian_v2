package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.domain.usecase.ManageCategoryUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel
  @Inject
  constructor(
    private val manageCategoryUseCase: ManageCategoryUseCase
  ) : ViewModel() {
    val categories: StateFlow<List<Category>> =
      manageCategoryUseCase.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(
      name: String,
      key: String,
      icon: String,
      color: Long,
      type: CategoryType
    ) {
      viewModelScope.launch {
        manageCategoryUseCase.addCategory(name, key, icon, color, type)
      }
    }

    fun updateCategory(category: Category) {
      viewModelScope.launch {
        manageCategoryUseCase.updateCategory(category)
      }
    }

    fun deleteCategory(category: Category) {
      viewModelScope.launch {
        manageCategoryUseCase.deleteCategory(category)
      }
    }

    suspend fun getCategoryByKey(key: String): Category? = manageCategoryUseCase.getCategoryByKey(key)
  }
