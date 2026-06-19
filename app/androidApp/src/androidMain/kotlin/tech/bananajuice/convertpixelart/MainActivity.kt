package tech.bananajuice.convertpixelart

import App
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {
    private var statusText by mutableStateOf("")

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleFileUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                onSelectFileClick = { openFilePicker() },
                statusText = statusText
            )
        }

        if (savedInstanceState == null && intent?.action == Intent.ACTION_SEND) {
            intent.clipData?.let { clipData ->
                if (clipData.itemCount > 0) {
                    val uri = clipData.getItemAt(0).uri
                    if (uri != null) {
                        handleFileUri(uri)
                    }
                }
            } ?: run {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    handleFileUri(uri)
                }
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        selectFileLauncher.launch(intent)
    }

    private fun handleFileUri(uri: Uri) {
        statusText = "Processing file..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri) ?: "input_file"
                val inputCacheDir = File(cacheDir, "inputs")
                inputCacheDir.mkdirs()

                var inputFile = File(inputCacheDir, fileName)

                val inputStream = contentResolver.openInputStream(uri) ?: throw java.io.IOException("Failed to open input stream for URI: $uri")
                inputStream.use { input ->
                    FileOutputStream(inputFile).use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }

                if (inputFile.name.endsWith(".pixaki", ignoreCase = true)) {
                    val unzippedDir = File(inputCacheDir, inputFile.nameWithoutExtension)
                    unzipFile(inputFile, unzippedDir)
                    inputFile = unzippedDir
                }

                val outputDir = File(cacheDir, "outputs")
                outputDir.mkdirs()

                val outputExtension = if (inputFile.name.endsWith(".psp", ignoreCase = true) ||
                                          inputFile.name.endsWith(".psd", ignoreCase = true) ||
                                          inputFile.name.endsWith(".ase", ignoreCase = true) ||
                                          inputFile.name.endsWith(".aseprite", ignoreCase = true) ||
                                          inputFile.name.endsWith(".pixaki", ignoreCase = true) ||
                                          inputFile.isDirectory) {
                    ".aseprite"
                } else {
                    ".png"
                }

                val outputFileName = "${if (inputFile.isDirectory) inputFile.name else inputFile.nameWithoutExtension}$outputExtension"
                val outputFile = File(outputDir, outputFileName)

                val result = RustCore.convertFile(inputFile.absolutePath, outputFile.absolutePath, false)

                withContext(Dispatchers.Main) {
                    if (result == 0) {
                        statusText = "Conversion successful! Sharing..."
                        shareFile(outputFile)
                    } else {
                        statusText = "Conversion failed."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText = "Error: ${e.message}"
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun unzipFile(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(targetDir, zipEntry.name)
                // Zip Slip vulnerability prevention
                val targetDirPath = targetDir.canonicalPath
                val newFilePath = newFile.canonicalPath
                if (!newFilePath.startsWith(targetDirPath + File.separator) && newFilePath != targetDirPath) {
                    throw SecurityException("Entry is outside of the target dir: ${zipEntry.name}")
                }

                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "tech.bananajuice.convertpixelart.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share output file"))
    }
}
