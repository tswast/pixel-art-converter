package tech.bananajuice.convertpixelart

import App
import OutputFormat
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
    private var pendingUri by mutableStateOf<Uri?>(null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingUri?.let { outState.putParcelable("pendingUri", it) }
        outState.putString("statusText", statusText)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelable("pendingUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            savedInstanceState.getParcelable<Uri>("pendingUri")
        }
        if (savedUri != null) {
            pendingUri = savedUri
        }
        val savedStatus = savedInstanceState.getString("statusText")
        if (savedStatus != null) {
            statusText = savedStatus
        }
    }

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingUri = uri
                statusText = "Please select output format"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                onSelectFileClick = { openFilePicker() },
                statusText = statusText,
                hasPendingFile = pendingUri != null,
                onFormatSelected = { format ->
                    pendingUri?.let { uri ->
                        pendingUri = null
                        if (format != null) {
                            handleFileUri(uri, format)
                        } else {
                            statusText = ""
                        }
                    }
                }
            )
        }

        if (savedInstanceState == null && intent?.action == Intent.ACTION_SEND) {
            intent.clipData?.let { clipData ->
                if (clipData.itemCount > 0) {
                    val uri = clipData.getItemAt(0).uri
                    if (uri != null) {
                        pendingUri = uri
                        statusText = "Please select output format"
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
                    pendingUri = uri
                    statusText = "Please select output format"
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

    private fun handleFileUri(uri: Uri, outputFormat: OutputFormat) {
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

                val outputExtension = when (outputFormat) {
                    OutputFormat.PNG -> ".png"
                    OutputFormat.ASEPRITE, OutputFormat.ASEPRITE_TIMELAPSE -> ".aseprite"
                }

                val outputFileName = "${if (inputFile.isDirectory) inputFile.name else inputFile.nameWithoutExtension}$outputExtension"
                val outputFile = File(outputDir, outputFileName)

                val isTimelapse = outputFormat == OutputFormat.ASEPRITE_TIMELAPSE
                val result = RustCore.convertFile(inputFile.absolutePath, outputFile.absolutePath, isTimelapse)

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
                if (!File(newFilePath).startsWith(File(targetDirPath))) {
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
