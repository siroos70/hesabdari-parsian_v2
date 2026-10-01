package io.github.mojri.hesabyar.ui.screens
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.ui.CategoryViewModel
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.SectionHeader
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

private val CATEGORY_ICONS =
  mapOf(
    "Restaurant" to Icons.Filled.Restaurant,
    "DirectionsCar" to Icons.Filled.DirectionsCar,
    "ShoppingBag" to Icons.Filled.ShoppingBag,
    "ReceiptLong" to Icons.Filled.ReceiptLong,
    "CreditCard" to Icons.Filled.CreditCard,
    "HistoryEdu" to Icons.Filled.HistoryEdu,
    "Paid" to Icons.Filled.Paid,
    "AttachMoney" to Icons.Filled.AttachMoney,
    "Home" to Icons.Filled.Home,
    "HealthAndSafety" to Icons.Filled.HealthAndSafety,
    "School" to Icons.Filled.School,
    "Flight" to Icons.Filled.Flight,
    "LocalCafe" to Icons.Filled.LocalCafe,
    "Pets" to Icons.Filled.Pets,
    "CardGiftcard" to Icons.Filled.CardGiftcard,
    "Work" to Icons.Filled.Work,
    "SportsEsports" to Icons.Filled.SportsEsports,
    "Checkroom" to Icons.Filled.Checkroom,
    "LocalGroceryStore" to Icons.Filled.LocalGroceryStore,
    "Savings" to Icons.Filled.Savings,
    "AccountBalance" to Icons.Filled.AccountBalance,
    "TrendingUp" to Icons.Filled.TrendingUp,
    "TrendingDown" to Icons.Filled.TrendingDown,
    "Build" to Icons.Filled.Build,
    "Phone" to Icons.Filled.Phone,
    "Wifi" to Icons.Filled.Wifi,
    "LocalHospital" to Icons.Filled.LocalHospital,
    "ChildCare" to Icons.Filled.ChildCare,
    "LocalDining" to Icons.Filled.LocalDining,
    "CleaningServices" to Icons.Filled.CleaningServices
  )

private val CATEGORY_COLORS =
  listOf(
    0xFF4CAF50L,
    0xFFFF9800L,
    0xFF2196F3L,
    0xFF009688L,
    0xFFF44336L,
    0xFF9C27B0L,
    0xFF757575L,
    0xFFE91E63L,
    0xFF3F51B5L,
    0xFF00BCD4L,
    0xFF8BC34AL,
    0xFFFF5722L,
    0xFF607D8BL,
    0xFF795548L,
    0xFFCDDC39L,
    0xFF03A9F4L
  )

private val CategoryType.displayName: String
  @Composable
  get() =
    when (this) {
      CategoryType.EXPENSE -> stringResource(R.string.category_type_expense)
      CategoryType.INCOME -> stringResource(R.string.category_type_income)
      CategoryType.BOTH -> stringResource(R.string.category_type_both)
      CategoryType.UNKNOWN -> stringResource(R.string.category_type_unknown)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
  categoryViewModel: CategoryViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val categories by categoryViewModel.categories.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }
  var editingCategory by remember { mutableStateOf<Category?>(null) }
  var showDeleteConfirmation by remember { mutableStateOf<Category?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "مدیریت دسته‌بندی‌ها",
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.Filled.ArrowForward,
              contentDescription = "بازگشت"
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ) {
        Icon(
          imageVector = Icons.Filled.Add,
          contentDescription = "افزودن دسته‌بندی"
        )
      }
    }
  ) { innerPadding ->
    if (categories.isEmpty()) {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "هنوز دسته‌بندی‌ای ثبت نشده است.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      val defaultCategories = categories.filter { it.isDefault }
      val customCategories = categories.filter { !it.isDefault }

      LazyColumn(
        modifier =
          modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        if (defaultCategories.isNotEmpty()) {
          item {
            SectionHeader(title = "دسته‌بندی‌های پیش‌فرض")
          }
          items(defaultCategories, key = { it.id }) { category ->
            CategoryItem(
              category = category,
              isDefault = true,
              onEdit = { editingCategory = it },
              onDelete = { showDeleteConfirmation = it }
            )
          }
        }

        if (customCategories.isNotEmpty()) {
          item {
            SectionHeader(title = "دسته‌بندی‌های اختصاصی")
          }
          items(customCategories, key = { it.id }) { category ->
            CategoryItem(
              category = category,
              isDefault = false,
              onEdit = { editingCategory = it },
              onDelete = { showDeleteConfirmation = it }
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(SpacingTokens.lg))
        }
      }
    }
  }

  if (showAddDialog) {
    CategoryDialog(
      initialCategory = null,
      onDismiss = { showAddDialog = false },
      onSave = { name, key, icon, color, type ->
        categoryViewModel.addCategory(name, key, icon, color, type)
        showAddDialog = false
      }
    )
  }

  if (editingCategory != null) {
    CategoryDialog(
      initialCategory = editingCategory,
      onDismiss = { editingCategory = null },
      onSave = { name, key, icon, color, type ->
        categoryViewModel.updateCategory(
          editingCategory!!.copy(
            name = name,
            key = key,
            icon = icon,
            color = color,
            type = type
          )
        )
        editingCategory = null
      }
    )
  }

  if (showDeleteConfirmation != null) {
    ConfirmDialog(
      title = "حذف دسته‌بندی",
      message = "آیا از حذف دسته‌بندی «${showDeleteConfirmation!!.name}» اطمینان دارید؟",
      confirmText = "حذف",
      dismissText = "انصراف",
      onDismiss = { showDeleteConfirmation = null },
      onConfirm = {
        categoryViewModel.deleteCategory(showDeleteConfirmation!!)
        showDeleteConfirmation = null
      }
    )
  }
}

@Composable
private fun CategoryItem(
  category: Category,
  isDefault: Boolean,
  onEdit: (Category) -> Unit,
  onDelete: (Category) -> Unit
) {
  val icon = CATEGORY_ICONS[category.icon] ?: Icons.Filled.Paid
  val categoryColor = Color(category.color)

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = SpacingTokens.lg),
    shape = ShapeTokens.Medium,
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      IconCircle(
        icon = icon,
        tint = categoryColor,
        backgroundColor = categoryColor,
        containerSize = Dimens.AvatarMedium,
        iconSize = 20.dp
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = category.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = category.key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = category.type.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = categoryColor,
            fontWeight = FontWeight.Medium
          )
        }
      }

      IconButton(
        onClick = { onEdit(category) },
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = "ویرایش",
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.primary
        )
      }

      if (!isDefault) {
        IconButton(
          onClick = { onDelete(category) },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
  initialCategory: Category?,
  onDismiss: () -> Unit,
  onSave: (name: String, key: String, icon: String, color: Long, type: CategoryType) -> Unit
) {
  val isEditing = initialCategory != null
  var name by remember { mutableStateOf(initialCategory?.name.orEmpty()) }
  var key by remember { mutableStateOf(initialCategory?.key.orEmpty()) }
  var selectedIcon by remember { mutableStateOf(initialCategory?.icon ?: "Paid") }
  var selectedColor by remember { mutableStateOf(initialCategory?.color ?: 0xFF4CAF50L) }
  var selectedType by remember { mutableStateOf(initialCategory?.type ?: CategoryType.EXPENSE) }
  var typeDropdownExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (isEditing) "ویرایش دسته‌بندی" else "افزودن دسته‌بندی جدید",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        HesabyarInputField(
          value = name,
          onValueChange = { name = it },
          label = "نام فارسی",
          placeholder = "مثلاً: حمل و نقل",
          shape = ShapeTokens.Medium
        )

        HesabyarInputField(
          value = key,
          onValueChange = { key = it },
          label = "کلید انگلیسی (اختیاری)",
          placeholder = "مثلاً: Transportation",
          shape = ShapeTokens.Medium,
          enabled = !isEditing || initialCategory?.isDefault != true
        )

        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
          Text(
            text = "نوع:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          ExposedDropdownMenuBox(
            expanded = typeDropdownExpanded,
            onExpandedChange = { typeDropdownExpanded = it }
          ) {
            OutlinedTextField(
              value = selectedType.displayName,
              onValueChange = {},
              modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
              readOnly = true,
              shape = ShapeTokens.Medium,
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) }
            )
            ExposedDropdownMenu(
              expanded = typeDropdownExpanded,
              onDismissRequest = { typeDropdownExpanded = false }
            ) {
              DropdownMenuItem(
                text = { Text("هزینه") },
                onClick = {
                  selectedType = CategoryType.EXPENSE
                  typeDropdownExpanded = false
                }
              )
              DropdownMenuItem(
                text = { Text("درآمد") },
                onClick = {
                  selectedType = CategoryType.INCOME
                  typeDropdownExpanded = false
                }
              )
              DropdownMenuItem(
                text = { Text("هر دو") },
                onClick = {
                  selectedType = CategoryType.BOTH
                  typeDropdownExpanded = false
                }
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
          Text(
            text = "رنگ:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
          ) {
            items(CATEGORY_COLORS) { color ->
              Box(
                modifier =
                  Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { selectedColor = color },
                contentAlignment = Alignment.Center
              ) {
                if (selectedColor == color) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Dimens.IconSmall)
                  )
                }
              }
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
          Text(
            text = "آیکون:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
          ) {
            items(CATEGORY_ICONS.entries.toList()) { (iconName, iconVector) ->
              val isSelected = selectedIcon == iconName
              Box(
                modifier =
                  Modifier
                    .size(36.dp)
                    .clip(ShapeTokens.Small)
                    .background(
                      if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                      } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                      }
                    ).clickable { selectedIcon = iconName },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = iconVector,
                  contentDescription = iconName,
                  modifier = Modifier.size(20.dp),
                  tint =
                    if (isSelected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
              }
            }
          }
        }

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(ShapeTokens.Medium)
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .padding(SpacingTokens.md),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          val previewIcon = CATEGORY_ICONS[selectedIcon] ?: Icons.Filled.Paid
          val previewColor = Color(selectedColor)
          IconCircle(
            icon = previewIcon,
            tint = previewColor,
            backgroundColor = previewColor,
            containerSize = Dimens.AvatarMedium,
            iconSize = 20.dp
          )
          Column {
            Text(
              text = name.ifBlank { "نام دسته‌بندی" },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = selectedType.displayName,
              style = MaterialTheme.typography.labelSmall,
              color = previewColor
            )
          }
        }
      }
    },
    confirmButton = {
      HesabyarButton(
        onClick = {
          val finalKey =
            key.trim().ifBlank {
              name.trim().replace("\\s+".toRegex(), "")
            }
          onSave(name.trim(), finalKey, selectedIcon, selectedColor, selectedType)
        },
        text = "ذخیره",
        variant = ButtonVariant.Filled,
        enabled = name.isNotBlank()
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = "انصراف",
        variant = ButtonVariant.Text
      )
    }
  )
}
