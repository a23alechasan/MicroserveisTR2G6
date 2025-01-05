package com.example.projectemicroserveis

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.delay
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val TAG = "MainActivity"
        val contrassenya = "noesaquesta"
        val socket = IO.socket("http://tr2g6.dam.inspedralbes.cat:22555")
        socket.connect()

        setContent {
            val processos = remember { mutableStateListOf<Proces>() }
            val logs = remember { mutableStateListOf<String>() }

            LaunchedEffect(Unit) {
                fetchProcessos(TAG, contrassenya, processos)
            }

            // Verifica que el socket estigui connectat abans d'escoltar esdeveniments
            if (socket.connected()) {
                socket.on("logs") { data: Array<Any> ->
                    logs.clear()
                    logs.addAll(data.map { it.toString() })
                }

                socket.on("log_response") { data: Array<Any> ->
                    Log.d(TAG, "Raw data content: ${data.contentToString()}") // Mostra tot el contingut brut

                    runOnUiThread {
                        logs.clear() // Neteja els logs existents

                        try {
                            data.forEachIndexed { index, item ->
                                Log.d(TAG, "Processing item at index $index: $item")

                                // Intenta convertir a JSONObject
                                val jsonObject = try {
                                    JSONObject(item.toString())
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error convertint a JSONObject: ${e.message}", e)
                                    null
                                }

                                // Continua només si jsonObject no és null
                                if (jsonObject != null) {
                                    val message = jsonObject.optString("message", "Missatge no disponible")
                                    val timestamp = jsonObject.optString("timestamp", "Data no disponible")

                                    // Converteix el timestamp
                                    val formattedDate = try {
                                        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                                        parser.timeZone = TimeZone.getTimeZone("UTC")
                                        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                        val date = parser.parse(timestamp)
                                        formatter.format(date!!)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsejant el timestamp: $timestamp", e)
                                        timestamp
                                    }

                                    // Afegir el missatge i la data amb un salt de línia
                                    logs.add("$formattedDate\n$message")
                                }
                            }
                            Log.d(TAG, "Final processed logs: $logs") // Verifica els logs processats
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processant els logs: ${e.message}", e)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Socket no està connectat.")
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProcessosScreen(TAG, contrassenya, socket, processos, logs)

                    // Cuadro de logs amb desplaçament que ocupa l'espai restant
                    if (logs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f) // Ocupa l'espai restant
                                .padding(top = 16.dp)
                                .verticalScroll(rememberScrollState()) // Habilita el desplaçament
                        ) {
                            Text(text = "Logs:", modifier = Modifier.padding(bottom = 8.dp))
                            logs.forEach { log ->
                                Text(
                                    text = log, // Mostra el log formatat
                                    modifier = Modifier
                                        .padding(vertical = 4.dp) // Separació entre logs
                                        .padding(8.dp), // Espaiat intern
                                    style = MaterialTheme.typography.body1 // Estil de text
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Funció per analitzar el log i obtenir la data i el missatge
    private fun parseLog(log: String): Pair<String, String> {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val parts = log.split(" - ", limit = 2)
        return if (parts.size == 2) {
            parts[0] to parts[1]
        } else {
            currentTime to log
        }
    }
}


public fun fetchProcessos(TAG: String, contrassenya: String, processos: MutableList<Proces>) {
    Log.d(TAG, "Fent la petició per obtenir els processos...")

    if (RetrofitInstance.api == null) {
        Log.e(TAG, "RetrofitInstance.api és null.")
        return
    }

    RetrofitInstance.api.getProcessos(contrassenya).enqueue(object : Callback<List<Proces>> {
        override fun onResponse(call: Call<List<Proces>>, response: Response<List<Proces>>) {
            if (response.isSuccessful) {
                response.body()?.let { processosResponse ->
                    processos.clear()
                    processos.addAll(processosResponse)
                } ?: run {
                    Log.e(TAG, "La resposta no conté processos.")
                }
            } else {
                Log.e(TAG, "Error en la resposta: ${response.code()} - ${response.message()}")
            }
        }

        override fun onFailure(call: Call<List<Proces>>, t: Throwable) {
            Log.e(TAG, "Excepció durant la petició: ${t.message}", t)
        }
    })
}




@Composable
fun ProcessosScreen(TAG: String, contrassenya: String, socket: Socket, processos: MutableList<Proces>, logs: List<String>) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            fetchProcessos(TAG, contrassenya, processos)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        processos.forEach { proces ->
            ProcesItem(proces = proces, onProcessAction = { serveiNom, action ->
                when (action) {
                    "encendre" -> {
                        socket.emit("encendre", contrassenya, serveiNom)
                        fetchProcessos(TAG, contrassenya, processos)
                    }
                    "parar" -> {
                        socket.emit("parar", contrassenya, serveiNom)
                        fetchProcessos(TAG, contrassenya, processos)
                    }
                    "getLog" -> {
                        socket.emit("getLog", contrassenya, serveiNom)
                    }
                }
            })
        }
    }
}

@Composable
fun ProcesItem(proces: Proces, onProcessAction: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Nom i estat del procés
        Text(
            text = "${proces.nom} - ${if (proces.actiu) "Actiu" else "Aturat"}",
            modifier = Modifier.width(150.dp), // Amplada fixa per al nom del procés
            style = MaterialTheme.typography.h6, // Millorar la visibilitat del text
            color = if (proces.actiu) Color.Green else Color.Gray // Color depenent de l'estat
        )

        // Botó per encendre o aturar
        Button(
            onClick = {
                if (proces.actiu) {
                    onProcessAction(proces.nom, "parar")
                } else {
                    onProcessAction(proces.nom, "encendre")
                }
            },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (proces.actiu) Color.Red else Color.Green
            ),
            modifier = Modifier.width(80.dp), // Amplada més ajustada per al botó
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp) // Reduir espai intern
        ) {
            Text(
                text = if (proces.actiu) "Aturar" else "Encendre",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), // Text més petit
                color = Color.White // Text en blanc per millor contrast
            )
        }

        // Botó per obtenir els logs
        Button(
            onClick = { onProcessAction(proces.nom, "getLog") },
            modifier = Modifier.width(80.dp), // Amplada més ajustada per al botó de logs
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp) // Reduir espai intern
        ) {
            Text(
                text = "Logs",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), // Text més petit
                color = Color.White // Text en blanc per millor contrast
            )
        }
    }
}