import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubytech.tracked.ui.Client
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable

@Composable
fun ClientSearchDialog(
    clients: List<Client>,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onClientSelected: (Client) -> Unit
) {
    var rawInput by remember { mutableStateOf("") } // Raw input with leading zeros
    var search by remember { mutableStateOf("") } // Sanitized input for filtering
    val filteredClients = clients.filter {
        it.name.contains(search, ignoreCase = true) ||
                it.idno.toString().contains(search, ignoreCase = true) ||
                it.contact.toString().contains(search, ignoreCase = true)
    }
    var selectedClient by remember { mutableStateOf<Client?>(null) }

    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { input ->
                            rawInput = input // Display raw input including leading zeros
                            search = input.trimStart(' ', '0') // Sanitize for search
                        },
                        placeholder = {
                            Text(
                                "Enter number or id..",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )

                    if (rawInput.isEmpty()) { // Use rawInput for icon visibility
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp),
                            tint = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { search = rawInput.trimStart(' ', '0') }, // Optional explicit search
                    modifier = Modifier.height(52.dp)
                ) { Text("Search") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Table header
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF0F0F0))
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Name",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Start
                )
                Text(
                    "Contact",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Start
                )
                Text(
                    "ID No",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Start
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(filteredClients) { index, client ->
                    val isSelected = selectedClient == client
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) Color(0xFFB3E5FC)
                                else if (index % 2 == 0) Color(0xFFFFFFFF)
                                else Color(0xFFF8F8F8)
                            )
                            .padding(vertical = 8.dp)
                            .clickable {
                                selectedClient = client
                                onClientSelected(client)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            client.name,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            client.contact.toString().padStart(10, '0'), // Add leading zeros
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            client.idno.toString(),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = onSubmit) { Text("Submit") }
            }
        }
    }
}