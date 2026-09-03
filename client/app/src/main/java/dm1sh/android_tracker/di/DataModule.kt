package dm1sh.android_tracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dm1sh.android_tracker.data.local.DeviceMetricsDao
import dm1sh.android_tracker.data.local.TrackerDatabase
import dm1sh.android_tracker.data.local.UsageEventDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideUsageEventDao(database: TrackerDatabase): UsageEventDao =
        database.usageEventDao()

    @Provides
    @Singleton
    fun provideDeviceMetricsDao(database: TrackerDatabase): DeviceMetricsDao =
        database.deviceMetricsDao()
}
