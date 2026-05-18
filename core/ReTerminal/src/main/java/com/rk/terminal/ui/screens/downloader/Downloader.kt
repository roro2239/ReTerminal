package com.rk.terminal.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.libcommons.child
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.UnknownHostException

@Composable
fun Downloader(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val installingStr = stringResource(strings.installing)
    val downloadingStr = stringResource(strings.downloading)
    val networkErrorStr = stringResource(strings.network_error)
    val setupFailedStr = stringResource(strings.setup_failed)
    var progressText by remember { mutableStateOf(installingStr) }
    var isSetupComplete by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            if (Rootfs.isFilesDownloaded()) {
                Rootfs.isDownloaded.value = true
                isSetupComplete = true
                return@LaunchedEffect
            }

            val alpine = latestAlpineMiniRootfs()
            val files = listOf(
                DownloadFile("alpine.tar.gz", alpine.url, Rootfs.reTerminal.child("alpine.tar.gz"), alpine.sha256),
                DownloadFile("proot", PROOT_URL, Rootfs.reTerminal.child("proot"), null),
                DownloadFile("libtalloc.so.2", TALLOC_URL, Rootfs.reTerminal.child("libtalloc.so.2"), null)
            )

            needsDownload = true
            setupEnvironment(
                files,
                onProgress = { currentProgress ->
                    if (needsDownload) {
                        progress = currentProgress.coerceIn(0f, 1f)
                        progressText = downloadingStr.format((progress * 100).toInt())
                    }
                },
                onComplete = {
                    Rootfs.markDownloaded(alpine.sha256)
                    isSetupComplete = true
                },
                onError = { error ->
                    toast(if (error is UnknownHostException) networkErrorStr else setupFailedStr.format(error.message))
                }
            )
        } catch (e: Exception) {
            toast(if (e is UnknownHostException) networkErrorStr else setupFailedStr.format(e.message))
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!isSetupComplete) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(progressText, style = MaterialTheme.typography.bodyLarge)
                if (needsDownload) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f))
                }
            }
        } else {
            TerminalScreen(mainActivityActivity = mainActivity, navController = navController)
        }
    }
}

private data class DownloadFile(val name: String, val url: String, val outputFile: File, val sha256: String?)

private data class AlpineRelease(val url: String, val sha256: String)

private suspend fun setupEnvironment(
    files: List<DownloadFile>,
    onProgress: (Float) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            if (!Rootfs.isFilesDownloaded()) {
                Rootfs.resetInstalledRootfs()
                files.forEachIndexed { index, file ->
                    val outputFile = file.outputFile.apply { parentFile?.mkdirs() }
                    outputFile.delete()
                    try {
                        downloadFile(file, outputFile) { downloaded, total ->
                            val fileProgress = downloaded.toFloat() / total
                            runOnUiThread { onProgress((index + fileProgress) / files.size) }
                        }
                    } catch (e: Exception) {
                        throw Exception("下载 ${file.name} 失败：${e.message}", e)
                    }
                }
            }
            runOnUiThread { onComplete() }
        } catch (e: Exception) {
            files.forEach { it.outputFile.delete() }
            withContext(Dispatchers.Main) { onError(e) }
        }
    }
}

private suspend fun latestAlpineMiniRootfs(): AlpineRelease {
    return withContext(Dispatchers.IO) {
        val arch = alpineArch()
        val baseUrl = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$arch"
        val yaml = OkHttpClient().newCall(Request.Builder().url("$baseUrl/latest-releases.yaml").build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) throw Exception("获取 Alpine 版本失败：${response.code}")
                response.body?.string() ?: throw Exception("Alpine 版本信息为空")
            }
        val block = yaml.split("\n-").firstOrNull { it.contains("flavor: alpine-minirootfs") }
            ?: throw Exception("未找到 Alpine minirootfs")
        val file = block.valueOf("file")
        val sha256 = block.valueOf("sha256")
        AlpineRelease("$baseUrl/$file", sha256)
    }
}

private fun alpineArch(): String {
    if ("arm64-v8a" !in Build.SUPPORTED_ABIS) {
        throw RuntimeException("仅支持 arm64-v8a")
    }
    return "aarch64"
}

private fun String.valueOf(key: String): String {
    return lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(":")
        ?.trim()
        ?.trim('"')
        ?: throw Exception("缺少 Alpine 字段：$key")
}

private suspend fun downloadFile(file: DownloadFile, outputFile: File, onProgress: (Long, Long) -> Unit) {
    withContext(Dispatchers.IO) {
        OkHttpClient().newCall(Request.Builder().url(file.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

            val body = response.body ?: throw Exception("内容为空")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: 1L
            var downloadedBytes = 0L
            val digest = java.security.MessageDigest.getInstance("SHA-256")

            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                    }
                }
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (file.sha256 != null && checksum != file.sha256) {
                outputFile.delete()
                throw Exception("校验失败")
            }
        }
    }
}

private const val PROOT_URL = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/proot"
private const val TALLOC_URL = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/libtalloc.so.2"
