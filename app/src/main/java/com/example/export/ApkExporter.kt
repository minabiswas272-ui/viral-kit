package com.example.export

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

data class ApkPackageInfo(
    val fileName: String,
    val versionName: String,
    val sizeBytes: Long,
    val sourcePath: String
) {
    val sizeMbFormatted: String
        get() = String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
}

object ApkExporter {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    const val DEFAULT_APK_FILE_NAME = "ViralKit-v1.0-debug.apk"
    private const val EXPORTS_DIR = "exports"

    fun resolveSourceApkFile(context: Context): File? {
        val sourceDir = context.applicationInfo?.sourceDir
        if (!sourceDir.isNullOrBlank()) {
            val installedFile = File(sourceDir)
            if (installedFile.isFile && installedFile.length() > 0L) {
                return installedFile
            }
        }
        val workspaceFallbacks = listOf(
            File("app/build/outputs/apk/debug/app-debug.apk"),
            File("build/outputs/apk/debug/app-debug.apk"),
            File(".build-outputs/app-debug.apk"),
            File("../app/build/outputs/apk/debug/app-debug.apk"),
            File("../.build-outputs/app-debug.apk")
        )
        return workspaceFallbacks.firstOrNull { it.isFile && it.length() > 0L }
    }

    fun inspectApkPackage(context: Context): ApkPackageInfo? {
        val sourceFile = resolveSourceApkFile(context) ?: return null
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
        return ApkPackageInfo(
            fileName = "ViralKit-v$versionName-debug.apk",
            versionName = versionName,
            sizeBytes = sourceFile.length(),
            sourcePath = "app/build/outputs/apk/debug/app-debug.apk"
        )
    }

    suspend fun downloadApkToDownloadsFolder(context: Context): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = resolveSourceApkFile(context)
                    ?: throw IllegalStateException("Compiled APK file not found on device.")
                val info = inspectApkPackage(context)
                val fileName = info?.fileName ?: DEFAULT_APK_FILE_NAME
                val sizeMb = String.format(Locale.US, "%.1f MB", sourceFile.length() / (1024.0 * 1024.0))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (itemUri != null) {
                        FileInputStream(sourceFile).use { input ->
                            resolver.openOutputStream(itemUri)?.use { output ->
                                input.copyTo(output)
                            } ?: throw IllegalStateException("Unable to open Downloads output stream.")
                        }
                        val completeValues = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(itemUri, completeValues, null, null)
                        return@runCatching "Downloaded $fileName ($sizeMb) to Downloads/$fileName"
                    }
                }

                val artifact = exportInstalledApk(context)
                "APK ready ($sizeMb): ${artifact.file.absolutePath}"
            }
        }

    suspend fun exportInstalledApk(context: Context): ExportedArtifact =
        withContext(Dispatchers.IO) {
            val sourceFile = resolveSourceApkFile(context)
                ?: throw IllegalStateException("Compiled APK file not found on device.")
            val exportDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
            val targetFile = File(exportDir, DEFAULT_APK_FILE_NAME)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            val uri = GrowthKitExporter.getShareUri(context, targetFile)
            ExportedArtifact(
                file = targetFile,
                uri = uri,
                mimeType = APK_MIME_TYPE,
                chooserTitle = "Share or Save ViralKit APK"
            )
        }

    suspend fun writeInstalledApkToUri(context: Context, destinationUri: Uri): Long =
        withContext(Dispatchers.IO) {
            val sourceFile = resolveSourceApkFile(context)
                ?: throw IllegalStateException("Compiled APK file not found on device.")
            var bytesCopied = 0L
            FileInputStream(sourceFile).use { input ->
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    bytesCopied = input.copyTo(output)
                } ?: throw IllegalStateException("Could not open destination URI for writing APK.")
            }
            bytesCopied
        }

    fun launchApkShareSheet(context: Context, artifact: ExportedArtifact): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, artifact.uri)
            putExtra(Intent.EXTRA_SUBJECT, "ViralKit Android App ($DEFAULT_APK_FILE_NAME)")
            putExtra(
                Intent.EXTRA_TEXT,
                "Install ViralKit — AI Retention & Viral Growth Studio ($DEFAULT_APK_FILE_NAME)"
            )
            clipData = ClipData.newRawUri(artifact.chooserTitle, artifact.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, artifact.chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return runCatching {
            context.startActivity(chooser)
        }.isSuccess
    }
}
