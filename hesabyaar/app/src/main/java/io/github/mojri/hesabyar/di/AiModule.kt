package io.github.mojri.hesabyar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.domain.usecase.AiForecastAdviceCache
import io.github.mojri.hesabyar.domain.usecase.ManageAiConfigUseCase
import io.github.mojri.hesabyar.domain.usecase.SharedPrefsAiForecastAdviceCache
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
  @Provides
  @Singleton
  fun provideAiConfigManager(
    @ApplicationContext context: Context
  ): AiConfigManager = AiConfigManager(context)

  @Provides
  @Singleton
  fun provideManageAiConfigUseCase(aiConfigManager: AiConfigManager): ManageAiConfigUseCase =
    ManageAiConfigUseCase(aiConfigManager)

  @Provides
  @Singleton
  fun provideAiForecastAdviceCache(
    @ApplicationContext context: Context
  ): AiForecastAdviceCache {
    val sharedPrefs = context.getSharedPreferences("ai_cache_prefs", Context.MODE_PRIVATE)
    return SharedPrefsAiForecastAdviceCache(sharedPrefs)
  }
}
