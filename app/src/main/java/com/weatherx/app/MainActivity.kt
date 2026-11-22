package com.weatherx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(
    val id: Long,
    val name: String,
    val admin: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val subtitle: String
        get() = listOf(admin, country).filter { it.isNotBlank() }.distinct().joinToString("، ")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherXApp() }
    }
}

@Composable
fun WeatherXApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weatherx_places", 0) }
    val places = remember {
        mutableStateListOf<Place>().apply {
            addAll(loadPlaces(prefs))
        }
    }
    var selectedId by remember { mutableStateOf(places.firstOrNull()?.id) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun persist() {
        val array = JSONArray()
        places.forEach { place ->
            array.put(JSONObject().apply {
                put("id", place.id)
                put("name", place.name)
                put("admin", place.admin)
                put("country", place.country)
                put("lat", place.latitude)
                put("lon", place.longitude)
            })
        }
        prefs.edit().putString("saved_places", array.toString()).apply()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1266A5),
            secondary = Color(0xFF4BA3D9),
            background = Color(0xFFF2F8FD),
            surface = Color.White
        )
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF2F8FD)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xFF087CC1), Color(0xFF65B9E8))))
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                    ) {
                        Text("WeatherX", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("آب‌وهوا، هر جا که بخواهی", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp)
                        Spacer(Modifier.height(18.dp))
                        Text("مکان‌های من", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text("شهرهای مورد علاقه‌ات را اضافه و مدیریت کن", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }

                    Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("افزودن مکان جدید", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF153B59))
                        Spacer(Modifier.height(9.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("نام شهر را وارد کن؛ مثلاً رامسر") },
                            trailingIcon = { Icon(Icons.Default.Search, contentDescription = "جست‌وجو") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (query.isNotBlank() && !loading) {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try { results = searchPlaces(query.trim()) }
                                        catch (_: Exception) { error = "جست‌وجو انجام نشد؛ اتصال اینترنت را بررسی کن." }
                                        finally { loading = false }
                                    }
                                }
                            }),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (query.isNotBlank() && !loading) {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        results = emptyList()
                                        try {
                                            results = searchPlaces(query.trim())
                                            if (results.isEmpty()) error = "شهری با این نام پیدا نشد."
                                        } catch (_: Exception) {
                                            error = "ارتباط با سرویس مکان‌یابی برقرار نشد. دوباره تلاش کن."
                                        } finally { loading = false }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = query.isNotBlank() && !loading
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("جست‌وجوی شهر")
                        }
                        if (error != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(error!!, color = Color(0xFFB3261E), fontSize = 13.sp)
                        }
                        if (results.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("نتایج جست‌وجو", fontWeight = FontWeight.Bold, color = Color(0xFF153B59))
                            Spacer(Modifier.height(6.dp))
                            results.forEach { place ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(place.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF173E5B))
                                            if (place.subtitle.isNotBlank()) Text(place.subtitle, fontSize = 12.sp, color = Color.Gray)
                                        }
                                        TextButton(onClick = {
                                            val exists = places.any { it.id == place.id || (it.name.equals(place.name, true) && kotlin.math.abs(it.latitude - place.latitude) < 0.05) }
                                            if (exists) {
                                                error = "این مکان قبلاً اضافه شده است."
                                            } else {
                                                places.add(place)
                                                selectedId = place.id
                                                persist()
                                                results = emptyList()
                                                query = ""
                                                error = null
                                            }
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Text("افزودن")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مکان‌های ذخیره‌شده", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF153B59))
                            Spacer(Modifier.weight(1f))
                            Text("${places.size} مکان", color = Color(0xFF5D7486), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (places.isEmpty()) {
                            Text("هنوز مکانی ذخیره نکرده‌ای. از قسمت بالا یک شهر جست‌وجو کن.", color = Color.Gray, fontSize = 13.sp)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(places, key = { it.id }) { place ->
                                    val selected = selectedId == place.id
                                    Card(
                                        onClick = { selectedId = place.id },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE0F1FF) else Color.White),
                                        shape = RoundedCornerShape(16.dp),
                                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF318AC5)) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1680BD))
                                            Spacer(Modifier.size(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(place.name, fontWeight = FontWeight.Bold, color = Color(0xFF173E5B))
                                                if (place.subtitle.isNotBlank()) Text(place.subtitle, fontSize = 12.sp, color = Color.Gray)
                                                if (selected) Text("مکان انتخاب‌شده", fontSize = 11.sp, color = Color(0xFF1266A5))
                                            }
                                            IconButton(onClick = {
                                                places.remove(place)
                                                if (selectedId == place.id) selectedId = places.firstOrNull()?.id
                                                persist()
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف مکان", tint = Color(0xFF9B5151))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "اطلاعات شهرها از سرویس Open-Meteo Geocoding دریافت می‌شود.",
                        modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun loadPlaces(prefs: android.content.SharedPreferences): List<Place> {
    val saved = prefs.getString("saved_places", null)
    if (saved != null) {
        return try {
            val array = JSONArray(saved)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Place(
                    item.getLong("id"),
                    item.getString("name"),
                    item.optString("admin"),
                    item.optString("country"),
                    item.getDouble("lat"),
                    item.getDouble("lon")
                )
            }
        } catch (_: Exception) { defaultPlaces() }
    }
    return defaultPlaces()
}

private fun defaultPlaces() = listOf(
    Place(1L, "رحیم‌آباد", "گیلان", "ایران", 36.80, 50.27),
    Place(2L, "نشتارود", "مازندران", "ایران", 36.80, 51.05)
)

private suspend fun searchPlaces(name: String): List<Place> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(name, "UTF-8")
    val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=fa&format=json")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
        setRequestProperty("Accept", "application/json")
    }
    try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val array = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Place(
                id = item.optLong("id", (item.optDouble("latitude") * 100000).toLong()),
                name = item.optString("name"),
                admin = item.optString("admin1"),
                country = item.optString("country"),
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude")
            )
        }
    } finally {
        connection.disconnect()
    }
}
