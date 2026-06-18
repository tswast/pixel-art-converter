import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun App(
    onSelectFileClick: () -> Unit = {},
    statusText: String = ""
) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                onSelectFileClick()
            }) {
                Text("Select File")
            }
            if (statusText.isNotEmpty()) {
                Text(statusText)
            }
        }
    }
}
