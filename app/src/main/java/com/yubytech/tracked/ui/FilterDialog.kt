import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilterDialog(onApply: () -> Unit, onCancel: () -> Unit) {
    val regions = listOf("Nairobi North", "Nairobi South", "Nairobi East")
    val branches = listOf("Kawangware", "Westlands", "CBD")
    var selectedRegion by remember { mutableStateOf(regions[0]) }
    var selectedBranch by remember { mutableStateOf(branches[0]) }
    var regionExpanded by remember { mutableStateOf(false) }
    var branchExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Filter By", fontSize = 18.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            // Region Dropdown
            Text("Region", fontSize = 14.sp)
            Box {
                OutlinedButton(onClick = { regionExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedRegion)
                }
                DropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
                    regions.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = {
                            selectedRegion = it
                            regionExpanded = false
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Branch Dropdown
            Text("Branch", fontSize = 14.sp)
            Box {
                OutlinedButton(onClick = { branchExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedBranch)
                }
                DropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                    branches.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = {
                            selectedBranch = it
                            branchExpanded = false
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onApply) { Text("Apply") }
            }
        }
    }
} 