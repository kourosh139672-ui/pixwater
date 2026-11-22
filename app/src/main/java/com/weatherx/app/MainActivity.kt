package com.weatherx.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class Place(val id: Long, val name: String, val admin: String, val country: String, val lat: Double, val lon: Double) {
    val subtitle get() = listOf(admin, country).filter { it.isNotBlank() }.distinct().joinToString("، ")
}
data class ForecastDay(val date: String, val max: Int, val min: Int, val rain: Int, val code: Int)
data class Weather(
    val temp: Int, val feels: Int, val humidity: Int, val wind: Int, val rain: Int,
    val uv: Double, val code: Int, val days: List<ForecastDay>,
    val hourly: List<Pair<String, Int>>, val alerts: List<String>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherXApp() }
    }
}

private fun defaultPlaces() = listOf(
    Place(1, "رحیم‌آباد", "گیلان", "ایران", 36.80, 50.27),
    Place(2, "نشتارود", "مازندران", "ایران", 36.80, 51.05)
)

@Composable
fun WeatherXApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weatherx", 0) }
    val places = remember { mutableStateListOf<Place>().apply { addAll(loadPlaces(prefs)) } }
    var selected by remember { mutableStateOf(places.firstOrNull() ?: defaultPlaces().first()) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var showPlaces by remember { mutableStateOf(false) }
    var showExtended by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var selectedActivity by remember { mutableStateOf("باغ کیوی") }
    val scope = rememberCoroutineScope()

    fun persistPlaces() {
        val array = JSONArray()
        places.forEach { p -> array.put(JSONObject().put("id", p.id).put("name", p.name).put("admin", p.admin).put("country", p.country).put("lat", p.lat).put("lon", p.lon)) }
        prefs.edit().putString("places", array.toString()).apply()
    }
    fun refresh(place: Place) {
        scope.launch {
            loading = true
            error = ""
            try {
                val fresh = fetchWeather(place)
                weather = fresh
                prefs.edit().putString("weather_${place.id}", weatherToJson(fresh)).apply()
            } catch (_: Exception) {
                val cached = prefs.getString("weather_${place.id}", null)
                if (cached != null) {
                    weather = weatherFromJson(cached)
                    error = "اتصال برقرار نشد؛ آخرین اطلاعات ذخیره‌شده نمایش داده می‌شود."
                } else {
                    error = "دریافت آب‌وهوا ناموفق بود. اینترنت را بررسی کن."
                }
            } finally { loading = false }
        }
    }

    LaunchedEffect(selected.id) {
        weather = prefs.getString("weather_${selected.id}", null)?.let { runCatching { weatherFromJson(it) }.getOrNull() }
        refresh(selected)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            try {
                val lm = context.getSystemService(LocationManager::class.java)
                val loc: Location? = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc != null) selected = Place(-99, "موقعیت من", "", "مکان فعلی", loc.latitude, loc.longitude)
            else error = "موقعیت فعلی در دسترس نیست؛ مکان‌یابی گوشی را روشن کن و دوباره بزن."
            } catch (_: Exception) { error = "گرفتن موقعیت ناموفق بود. مکان‌یابی گوشی را بررسی کن." }
        } else error = "برای استفاده از موقعیت من باید مجوز مکان‌یابی را بدهی."
    }

    val w = weather
    val isRain = w?.code?.let { it in listOf(51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99) } == true
    val isCloud = w?.code?.let { it in listOf(2,3,45,48) } == true
    val gradient = when {
        isRain == true -> listOf(Color(0xFF174D75), Color(0xFF6599BA))
        isCloud == true -> listOf(Color(0xFF536E86), Color(0xFFA8BAC8))
        else -> listOf(Color(0xFF087CC1), Color(0xFF76C9EC))
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1266A5), background = Color(0xFFF1F7FB), surface = Color.White)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(Color(0xFFF1F7FB))) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    item {
                        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(gradient)).padding(20.dp)) {
                            if (isRain == true) RainDrops(Modifier.matchParentSize())
                            if (isCloud == true) CloudDecor(Modifier.matchParentSize())
                            if (w != null && isRain != true && isCloud != true) SunDecor(Modifier.matchParentSize())
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("WeatherX", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { showPlaces = true }) { Icon(Icons.Default.LocationOn, null, tint = Color.White) }
                                    IconButton(onClick = { refresh(selected) }) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
                                }
                                Text("آب‌وهوا، هر جا که بخواهی", color = Color.White.copy(alpha = .92f))
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(selected.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                        Text(selected.subtitle, color = Color.White.copy(alpha = .86f), fontSize = 12.sp)
                                    }
                                    Text(weatherIcon(w?.code), fontSize = 42.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (w != null) "${w.temp}°" else "--°", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                                }
                                Text(weatherDescription(w?.code), color = Color.White, fontSize = 16.sp)
                                if (w != null) Text("احساس‌شده ${w.feels}°  •  بیشینه ${w.days.firstOrNull()?.max ?: "--"}°  •  کمینه ${w.days.firstOrNull()?.min ?: "--"}°", color = Color.White.copy(alpha = .92f), fontSize = 12.sp)
                                if (loading) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = .25f))
                                }
                                if (error.isNotBlank()) Text(error, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    item {
                        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("رطوبت", "${w?.humidity ?: "--"}٪", "💧", Modifier.weight(1f))
                            MetricCard("باد", "${w?.wind ?: "--"} km/h", "💨", Modifier.weight(1f))
                            MetricCard("بارش", "${w?.rain ?: "--"}٪", "🌧️", Modifier.weight(1f))
                        }
                    }
                    item { MetricCard("شاخص فرابنفش UV", w?.uv?.let { String.format("%.1f", it) } ?: "--", "☀️", Modifier.padding(horizontal = 14.dp).fillMaxWidth()) }
                    item {
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            Text("پیش‌بینی چهار روز آینده", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173E5B))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("امروز", "فردا", "پس‌فردا", "۳ روز بعد").forEachIndexed { index, label ->
                                    FilterChip(selected = selectedDay == index, onClick = { selectedDay = index }, label = { Text(label) })
                                }
                            }
                            w?.days?.getOrNull(selectedDay)?.let { ForecastCard(it) }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("پیش‌بینی ۱۶ روزه", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173E5B))
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showExtended = !showExtended }) { Text(if (showExtended) "بستن" else "نمایش همه") }
                            }
                            if (showExtended) w?.days?.forEach { ForecastCard(it) }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            Text("ساعت‌های آینده", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173E5B))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                w?.hourly?.forEach { (time, temp) ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(time, fontSize = 11.sp)
                                            Text("🌤️", fontSize = 20.sp)
                                            Text("${temp}°", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            Text("هشدارهای آب‌وهوا", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173E5B))
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                                Text(w?.alerts?.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "در حال حاضر هشدار ویژه‌ای بر اساس داده‌های هواشناسی دریافت‌شده ثبت نشده است.", Modifier.padding(14.dp), color = Color(0xFF496477), fontSize = 13.sp)
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            Text("WeatherX Activities", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173E5B))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("باغ کیوی", "گلف", "فوتبال", "کمپینگ").forEach { name ->
                                    FilterChip(selected = selectedActivity == name, onClick = { selectedActivity = name }, label = { Text(name) })
                                }
                            }
                            val advice = activityAdvice(selectedActivity, w)
                            Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(advice.first, color = advice.second, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(advice.third, color = Color(0xFF526B7C), fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                                }
                            }
                        }
                    }
                    item {
                        Text("منبع داده: Open-Meteo • توصیه فعالیت‌ها فقط بر اساس وضعیت هواست و جایگزین هشدارهای رسمی یا نظر متخصص نیست.", Modifier.fillMaxWidth().padding(horizontal = 14.dp), color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    if (showPlaces) AlertDialog(
        onDismissRequest = { showPlaces = false },
        title = { Text("مدیریت مکان‌ها") },
        text = {
            Column(Modifier.heightIn(max = 560.dp)) {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام شهر") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                Button(onClick = {
                    if (search.isNotBlank()) scope.launch {
                        loading = true
                        try { results = searchCities(search); if (results.isEmpty()) error = "شهری پیدا نشد." }
                        catch (_: Exception) { error = "جست‌وجوی شهر ناموفق بود." }
                        finally { loading = false }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Icon(Icons.Default.Search, null); Text("جست‌وجو") }
                results.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.Bold); Text(p.subtitle, fontSize = 11.sp, color = Color.Gray) }
                        TextButton(onClick = {
                            if (places.none { it.id == p.id || (it.name == p.name && abs(it.lat - p.lat) < .05) }) {
                                places.add(p); persistPlaces(); selected = p; results = emptyList(); search = ""
                            } else error = "این مکان قبلاً اضافه شده است."
                        }) { Text("افزودن") }
                    }
                }
                TextButton(onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) {
                        try {
                            val lm = context.getSystemService(LocationManager::class.java)
                            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            if (loc != null) { selected = Place(-99, "موقعیت من", "", "مکان فعلی", loc.latitude, loc.longitude); showPlaces = false }
                            else error = "موقعیت در دسترس نیست؛ مکان‌یابی گوشی را روشن کن."
                        } catch (_: Exception) { error = "دریافت موقعیت ناموفق بود." }
                    } else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Icon(Icons.Default.MyLocation, null); Text("استفاده از موقعیت من") }
                HorizontalDivider()
                Text("مکان‌های ذخیره‌شده", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                places.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selected = p; showPlaces = false }, modifier = Modifier.weight(1f)) { Text(p.name) }
                        IconButton(onClick = {
                            places.remove(p); persistPlaces()
                            if (selected.id == p.id) selected = places.firstOrNull() ?: defaultPlaces().first()
                        }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { showPlaces = false }) { Text("بستن") } }
    )
}

@Composable
private fun MetricCard(title: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(emoji, fontSize = 20.sp)
            Text(value, fontWeight = FontWeight.Bold, color = Color(0xFF164D76), fontSize = 17.sp)
            Text(title, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ForecastCard(day: ForecastDay) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day.date, Modifier.weight(1f), fontSize = 12.sp)
            Text(weatherIcon(day.code), fontSize = 20.sp)
            Text("  ${day.max}° / ${day.min}°", fontWeight = FontWeight.Bold)
            Text("  🌧 ${day.rain}٪", fontSize = 12.sp, color = Color(0xFF397BA5))
        }
    }
}

@Composable
private fun RainDrops(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rain")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        for (i in 0..45) {
            val x = (i * 73f) % size.width
            val y = (i * 97f + phase * size.height * 1.6f) % size.height
            drawLine(Color.White.copy(alpha = .30f), Offset(x, y), Offset(x - 5, y + 17), strokeWidth = 2f)
        }
    }
}

@Composable
private fun CloudDecor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "clouds")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "cloudPhase")
    Canvas(modifier) {
        for (i in 0..3) {
            val x = ((i * size.width / 3f) + phase * size.width * .22f) % (size.width + 100f) - 50f
            val y = size.height * (.22f + i * .13f)
            val cloud = Color.White.copy(alpha = .13f)
            drawCircle(cloud, 23f, Offset(x, y))
            drawCircle(cloud, 30f, Offset(x + 25f, y - 8f))
            drawCircle(cloud, 22f, Offset(x + 52f, y + 2f))
        }
    }
}

@Composable
private fun SunDecor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sun")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sunPulse"
    )
    Canvas(modifier) {
        val center = Offset(size.width * 0.86f, size.height * 0.18f)
        val radius = 27f * pulse
        drawCircle(Color(0xFFFFD76A).copy(alpha = 0.16f), radius = radius * 1.9f, center = center)
        drawCircle(Color(0xFFFFD76A).copy(alpha = 0.30f), radius = radius * 1.35f, center = center)
        drawCircle(Color(0xFFFFD76A), radius = radius, center = center)
        for (i in 0 until 8) {
            val angle = Math.PI * i / 4.0
            val inner = radius + 7f
            val outer = radius + 15f
            val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner)
            val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer)
            drawLine(Color(0xFFFFE7A3).copy(alpha = 0.8f), start, end, strokeWidth = 3f)
        }
    }
}

private fun weatherIcon(code: Int?): String = when (code) {
    0 -> "☀️"; 1 -> "🌤️"; 2 -> "⛅"; 3 -> "☁️"; 45,48 -> "🌫️"
    51,53,55,56,57,61,63,65,66,67,80,81,82 -> "🌧️"
    71,73,75,77,85,86 -> "❄️"; 95,96,99 -> "⛈️"; else -> "🌤️"
}
private fun weatherDescription(code: Int?): String = when (code) {
    0 -> "آسمان صاف"; 1 -> "عمدتاً صاف"; 2 -> "کمی ابری"; 3 -> "ابری"
    45,48 -> "مه‌آلود"; 51,53,55 -> "نم‌نم باران"; 56,57 -> "باران یخ‌زن"
    61,63,65,80,81,82 -> "بارانی"; 66,67 -> "باران یخ‌زن"
    71,73,75,77,85,86 -> "برفی"; 95,96,99 -> "رعدوبرق"; else -> "وضعیت هوا"
}

private fun activityAdvice(name: String, w: Weather?): Triple<String, Color, String> {
    if (w == null) return Triple("در انتظار اطلاعات هواشناسی", Color.Gray, "پس از دریافت وضعیت هوا، پیشنهاد مناسب نمایش داده می‌شود.")
    val badRain = w.rain >= 45 || w.code in listOf(95,96,99,65,67,82)
    val strongWind = w.wind >= 35
    val tooHot = w.temp >= 36
    val cold = w.temp <= 3
    val caution = w.rain >= 20 || w.wind >= 22 || w.uv >= 8 || tooHot || cold
    if (badRain || strongWind) return Triple("نامناسب در حال حاضر", Color(0xFFB3261E), "به دلیل بارش یا باد شدید، فعلاً ${name} را به تعویق بینداز. وضعیت ساعتی و هشدارهای رسمی را دوباره بررسی کن.")
    if (caution) return Triple("با احتیاط", Color(0xFFB26A00), "شرایط کاملاً ایده‌آل نیست. زمان کم‌بادتر و خنک‌تر را انتخاب کن؛ احتمال بارش ${w.rain}٪، باد ${w.wind} کیلومتر بر ساعت و UV برابر ${w.uv} است.")
    val reason = when (name) {
        "باغ کیوی" -> "بر اساس داده فعلی، بارش و باد قابل‌توجهی گزارش نشده؛ پیش از کار، خاک و پیش‌بینی ساعت‌های بعد را هم بررسی کن."
        "گلف" -> "هوای فعلی برای یک فعالیت بیرونی مناسب به نظر می‌رسد؛ UV و نوشیدن آب را فراموش نکن."
        "فوتبال" -> "شرایط جوی فعلی برای فوتبال مناسب به نظر می‌رسد؛ وضعیت زمین را هم بررسی کن."
        else -> "شرایط جوی فعلی برای کمپینگ مناسب به نظر می‌رسد؛ پیش از حرکت هشدارهای رسمی و وضعیت شب را بررسی کن."
    }
    return Triple("مناسب به نظر می‌رسد", Color(0xFF237A4B), reason)
}

private suspend fun searchCities(name: String): List<Place> = withContext(Dispatchers.IO) {
    val q = URLEncoder.encode(name, "UTF-8")
    val json = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=fa&format=json")
    val arr = json.optJSONArray("results") ?: return@withContext emptyList()
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Place(o.optLong("id"), o.optString("name"), o.optString("admin1"), o.optString("country"), o.getDouble("latitude"), o.getDouble("longitude"))
    }
}

private suspend fun fetchWeather(place: Place): Weather = withContext(Dispatchers.IO) {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m" +
        "&hourly=temperature_2m,precipitation_probability,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max" +
        "&forecast_days=16&timezone=auto"
    val root = getJson(url)
    val current = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val hourly = root.getJSONObject("hourly")
    val times = daily.getJSONArray("time")
    val max = daily.getJSONArray("temperature_2m_max")
    val min = daily.getJSONArray("temperature_2m_min")
    val rain = daily.getJSONArray("precipitation_probability_max")
    val codes = daily.getJSONArray("weather_code")
    val days = (0 until times.length()).map { i ->
        ForecastDay(times.getString(i), max.optDouble(i).toInt(), min.optDouble(i).toInt(), rain.optDouble(i).toInt(), codes.optInt(i))
    }
    val hTimes = hourly.getJSONArray("time")
    val hTemps = hourly.getJSONArray("temperature_2m")
    val hRain = hourly.getJSONArray("precipitation_probability")
    val now = current.optString("time")
    val start = (0 until hTimes.length()).firstOrNull { hTimes.optString(it) >= now } ?: 0
    val hours = (start until minOf(start + 8, hTimes.length())).map { i ->
        hTimes.getString(i).takeLast(5) to hTemps.optDouble(i).toInt()
    }
    val uv = daily.getJSONArray("uv_index_max").optDouble(0)
    val alerts = mutableListOf<String>()
    if ((days.firstOrNull()?.rain ?: 0) >= 60) alerts.add("احتمال بارش زیاد است؛ برای برنامه بیرونی آماده باش.")
    if (current.optDouble("wind_speed_10m") >= 35) alerts.add("باد شدید گزارش شده است؛ مراقب اشیای سبک و فعالیت بیرونی باش.")
    if (uv >= 8) alerts.add("شاخص UV بالاست؛ در ساعات اوج تابش احتیاط کن.")
    if (current.optInt("weather_code") in listOf(95,96,99)) alerts.add("شرایط رعدوبرقی گزارش شده؛ به فضای سرپوشیده امن برو و هشدارهای رسمی را دنبال کن.")
    Weather(
        current.optDouble("temperature_2m").toInt(),
        current.optDouble("apparent_temperature").toInt(),
        current.optInt("relative_humidity_2m"),
        current.optDouble("wind_speed_10m").toInt(),
        hRain.optDouble(start).toInt(),
        uv,
        current.optInt("weather_code"),
        days,
        hours,
        alerts
    )
}

private fun getJson(address: String): JSONObject {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 15000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "WeatherX-Android/1.0")
    }
    try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $body")
        return JSONObject(body)
    } finally { connection.disconnect() }
}

private fun loadPlaces(prefs: android.content.SharedPreferences): List<Place> {
    val raw = prefs.getString("places", null) ?: return defaultPlaces()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(o.getLong("id"), o.getString("name"), o.optString("admin"), o.optString("country"), o.getDouble("lat"), o.getDouble("lon"))
        }.ifEmpty { defaultPlaces() }
    } catch (_: Exception) { defaultPlaces() }
}

private fun weatherToJson(w: Weather): String {
    val days = JSONArray()
    w.days.forEach { days.put(JSONObject().put("date", it.date).put("max", it.max).put("min", it.min).put("rain", it.rain).put("code", it.code)) }
    val hours = JSONArray()
    w.hourly.forEach { hours.put(JSONObject().put("time", it.first).put("temp", it.second)) }
    val alerts = JSONArray()
    w.alerts.forEach { alerts.put(it) }
    return JSONObject().put("temp",w.temp).put("feels",w.feels).put("humidity",w.humidity).put("wind",w.wind).put("rain",w.rain).put("uv",w.uv).put("code",w.code).put("days",days).put("hours",hours).put("alerts",alerts).toString()
}
private fun weatherFromJson(raw: String): Weather {
    val o = JSONObject(raw)
    val d = o.getJSONArray("days")
    val h = o.getJSONArray("hours")
    val a = o.getJSONArray("alerts")
    return Weather(
        o.getInt("temp"),o.getInt("feels"),o.getInt("humidity"),o.getInt("wind"),o.getInt("rain"),o.getDouble("uv"),o.getInt("code"),
        (0 until d.length()).map { val x=d.getJSONObject(it); ForecastDay(x.getString("date"),x.getInt("max"),x.getInt("min"),x.getInt("rain"),x.getInt("code")) },
        (0 until h.length()).map { val x=h.getJSONObject(it); x.getString("time") to x.getInt("temp") },
        (0 until a.length()).map { a.getString(it) }
    )
}
