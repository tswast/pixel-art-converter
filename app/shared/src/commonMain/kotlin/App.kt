import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class OutputFormat {
    PNG,
    ASEPRITE,
    ASEPRITE_TIMELAPSE
}

@Composable
fun App(
    onSelectFileClick: () -> Unit = {},
    statusText: String = "",
    hasPendingFile: Boolean = false,
    onFormatSelected: (OutputFormat?) -> Unit = {}
) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasPendingFile) {
                Text("Select Output Format:")
                Button(onClick = { onFormatSelected(OutputFormat.PNG) }) {
                    Text("PNG")
                }
                Button(onClick = { onFormatSelected(OutputFormat.ASEPRITE) }) {
                    Text("Aseprite")
                }
                Button(onClick = { onFormatSelected(OutputFormat.ASEPRITE_TIMELAPSE) }) {
                    Text("Aseprite Timelapse")
                }
                Button(onClick = { onFormatSelected(null) }) {
                    Text("Cancel")
                }
            } else {
                Button(onClick = {
                    onSelectFileClick()
                }) {
                    Text("Select File")
                }
            }
            if (statusText.isNotEmpty()) {
                Text(statusText)
            }
        }
    }
}
