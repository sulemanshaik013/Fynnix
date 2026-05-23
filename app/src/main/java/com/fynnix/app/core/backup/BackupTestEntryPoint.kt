package com.fynnix.app.core.backup

import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.security.SecureKeyVault
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets instrumented tests reach the app's real Hilt-provided singletons instead of building a
 * second, independent instance pointed at the same on-disk database — a Hilt `@EntryPoint`
 * must be compiled as part of the same module that generates the app's Hilt component to be
 * usable via [dagger.hilt.android.EntryPointAccessors.fromApplication], which is why this
 * lives in `main` rather than `androidTest`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupTestEntryPoint {
    fun appDatabase(): AppDatabase
    fun backupRestoreUseCase(): BackupRestoreUseCase
    fun secureKeyVault(): SecureKeyVault
}
