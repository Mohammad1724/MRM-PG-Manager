package com.mrm.pgmanager.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.utils.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker پشتیبان‌گیری خودکار در پس‌زمینه.
 * در صورت عدم انتخاب پوشه یا غیرفعال‌بودن بکاپ هیچ کاری نمی‌کند.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val store = SessionStore(ctx)
        try {
            if (!store.readBackupEnabled()) return@withContext Result.success()
            val uriStr = store.readBackupUri() ?: return@withContext Result.success()
            val interval = store.readBackupIntervalHours()
            if (interval <= 0) return@withContext Result.success()

            val uri = Uri.parse(uriStr)
            val cr = ctx.contentResolver
            // Take persistable permission if still valid
            runCatching {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                cr.takePersistableUriPermission(uri, takeFlags)
            }

            // Create new file in the directory
            val fileName = BackupManager.generateFileName()
            val newDocUri = DocumentsContract.createDocument(
                cr,
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)),
                "application/octet-stream",
                fileName
            ) ?: return@withContext Result.retry()

            val password = store.readBackupPassword()
            val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            cr.openOutputStream(newDocUri)?.use { os ->
                BackupManager.createBackup(ctx, os, password, version)
            }

            // Prune old backups
            val keep = store.readBackupKeepCount()
            BackupManager.pruneBackups(ctx, uri, keep)

            store.saveLastBackupAt(System.currentTimeMillis())
            store.saveLastBackupSuccess(true)
            store.saveLastBackupMessage("پشتیبان‌گیری خودکار موفق")
            Result.success()
        } catch (e: Exception) {
            store.saveLastBackupSuccess(false)
            store.saveLastBackupMessage("خطا در بکاپ خودکار: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "mrm_periodic_backup"

        fun schedule(context: Context, hours: Int) {
            val wm = WorkManager.getInstance(context)
            if (hours <= 0) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                hours.toLong(), TimeUnit.HOURS,
                15L, TimeUnit.MINUTES
            ).build()
            wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
