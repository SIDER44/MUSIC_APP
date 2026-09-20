package com.almeer.music

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val JAMENDO_CLIENT_ID = "709fa152" // Jamendo's documented test read-only client ID. Replace with your own registered ID for production.
private const val JAMENDO_API = "https://api.jamendo.com/v3.0/tracks/"

private val Bg = Color(0xFF07080D)
private val Panel = Color(0xFF11131C)
private val Panel2 = Color(0xFF181B27)
private val Purple = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF22D3EE)
private val Green = Color(0xFF39E58C)
private val Muted = Color(0xFF9CA3AF)

private data class LocalTrack(val title: String, val artist: String, val uri: android.net.Uri, val file: File? = null)
private data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val image: String,
    val streamUrl: String,
    val downloadUrl: String,
    val downloadAllowed: Boolean,
    val bpm: Float = 128f
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var deckA: ExoPlayer
    private lateinit var deckB: ExoPlayer
    private var localTracks = mutableStateListOf<LocalTrack>()
    private var downloaded = mutableStateListOf<LocalTrack>()
    private var eqA: Equalizer? = null
    private var eqB: Equalizer? = null
    private var bassA: BassBoost? = null
    private var bassB: BassBoost? = null
    private val scope by lazy { kotlinx.coroutines.CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob()) }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadLocalMusic()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        deckA = ExoPlayer.Builder(this).build()
        deckB = ExoPlayer.Builder(this).build()
        loadDownloaded()
        loadLocalMusic()
        setContent { App() }
    }

    private fun requestMusicPermission() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) loadLocalMusic() else permission.launch(p)
    }

    private fun loadLocalMusic() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return
        val list = mutableListOf<LocalTrack>()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                list += LocalTrack(
                    c.getString(title), c.getString(artist),
                    android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(id))
                )
            }
        }
        localTracks.clear(); localTracks.addAll(list)
    }

    private fun loadDownloaded() {
        val dir = File(getExternalFilesDir(null), "music")
        if (!dir.exists()) dir.mkdirs()
        val list = dir.listFiles()?.filter { it.extension.lowercase() == "mp3" || it.extension.lowercase() == "ogg" }?.map {
            val file = it
            val parts = file.nameWithoutExtension.split("___", limit = 2)
            LocalTrack(parts.getOrElse(1) { file.nameWithoutExtension }, "ALMEER DOWNLOAD", android.net.Uri.fromFile(file), file)
        } ?: emptyList()
        downloaded.clear(); downloaded.addAll(list)
    }

    private fun play(uri: android.net.Uri) {
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.play()
    }

    private fun playOnline(track: OnlineTrack) {
        player.setMediaItem(MediaItem.fromUri(track.streamUrl)); player.prepare(); player.play()
    }

    private fun download(track: OnlineTrack) {
        if (!track.downloadAllowed || track.downloadUrl.isBlank()) {
            Toast.makeText(this, "This artist has disabled downloads for this track.", Toast.LENGTH_LONG).show(); return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(getExternalFilesDir(null), "music").apply { mkdirs() }
                val safe = track.title.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(80)
                val file = File(dir, "${track.id}___$safe.mp3")
                if (!file.exists()) URL(track.downloadUrl).openStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                launch(Dispatchers.Main) {
                    loadDownloaded()
                    Toast.makeText(this@MainActivity, "Downloaded: ${track.title}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private suspend fun searchJamendo(query: String): List<OnlineTrack> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val url = "$JAMENDO_API?client_id=$JAMENDO_CLIENT_ID&format=json&limit=20&audioformat=mp32&audiodlformat=mp32&search=${java.net.URLEncoder.encode(query, "UTF-8")}&imagesize=300"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 12000; conn.requestMethod = "GET"
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Jamendo HTTP ${conn.responseCode}")
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(json).optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    add(OnlineTrack(
                        id = o.optString("id"), title = o.optString("name", "Unknown"), artist = o.optString("artist_name", "Unknown artist"),
                        image = o.optString("image"), streamUrl = o.optString("audio"), downloadUrl = o.optString("audiodownload"),
                        downloadAllowed = o.optBoolean("audiodownload_allowed", false)
                    ))
                }
            }
        } finally { conn.disconnect() }
    }

    private fun setDeck(deck: ExoPlayer, track: LocalTrack) { deck.setMediaItem(MediaItem.fromUri(track.uri)); deck.prepare() }
    private fun seek(deck: ExoPlayer, deltaMs: Long) { deck.seekTo((deck.currentPosition + deltaMs).coerceAtLeast(0L)) }

    override fun onDestroy() {
        eqA?.release(); eqB?.release(); bassA?.release(); bassB?.release(); player.release(); deckA.release(); deckB.release(); scope.cancel(); super.onDestroy()
    }

    @Composable
    private fun App() {
        var tab by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }
        var online by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var current by remember { mutableStateOf<LocalTrack?>(null) }
        var currentOnline by remember { mutableStateOf<OnlineTrack?>(null) }
        var isPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(query) {
            if (query.trim().length < 2) { online = emptyList(); searching = false; return@LaunchedEffect }
            delay(450)
            searching = true
            try { online = searchJamendo(query.trim()) } catch (_: Exception) { online = emptyList() }
            searching = false
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Panel)) {
            Scaffold(containerColor = Bg, bottomBar = {
                if (current != null || currentOnline != null) MiniPlayer(
                    title = current?.title ?: currentOnline?.title ?: "",
                    artist = current?.artist ?: currentOnline?.artist ?: "",
                    playing = isPlaying,
                    onPlay = { if (player.isPlaying) { player.pause(); isPlaying = false } else { player.play(); isPlaying = true } }
                )
            }) { pad ->
                Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(18.dp)); Header()
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                                placeholder = { Text("Search songs, artists or albums online", color = Muted) }, singleLine = true,
                        shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF303442))
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HOME", "MY MUSIC", "DJ STUDIO").forEachIndexed { i, s -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(s) }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    when (tab) {
                        2 -> DjStudio()
                        else -> HomeLibrary(query, online, searching, current, currentOnline, isPlaying,
                            onLocal = { current = it; currentOnline = null; play(it.uri); isPlaying = true },
                            onOnline = { currentOnline = it; current = null; playOnline(it); isPlaying = true },
                            onDownload = ::download)
                    }
                }
            }
        }
    }

    @Composable private fun Header() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Purple, Cyan))), contentAlignment = Alignment.Center) {
                Text("A", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp)); Column { Text("ALMEER MUSIC", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("MUSIC • DISCOVER • DJ", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }

    @Composable
    private fun HomeLibrary(query: String, online: List<OnlineTrack>, searching: Boolean, current: LocalTrack?, currentOnline: OnlineTrack?, playing: Boolean,
                            onLocal: (LocalTrack) -> Unit, onOnline: (OnlineTrack) -> Unit, onDownload: (OnlineTrack) -> Unit) {
        val localFiltered = (localTracks + downloaded).distinctBy { it.uri.toString() }.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            if (query.isBlank()) {
                item { SectionTitle("YOUR LIBRARY", "Songs stored on this phone") }
                items(localFiltered.take(30)) { TrackCard(it, onLocal) }
                if (localFiltered.isEmpty()) item { EmptyState("Your library is empty", "Put music on your phone and ALMEER MUSIC will find it automatically.") }
            } else {
                item { SectionTitle("ONLINE RESULTS", "Jamendo independent-music catalog") }
                if (searching) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) } }
                if (!searching && online.isEmpty()) item { EmptyState("No online results", "Try another artist, song or genre.") }
                items(online) { OnlineCard(it, onOnline, onDownload) }
                item { SectionTitle("ON YOUR PHONE", "Matching local tracks") }
                if (localFiltered.isEmpty()) item { EmptyState("No local match", "Online search is active above.") }
                items(localFiltered) { TrackCard(it, onLocal) }
            }
        }
    }

    @Composable private fun SectionTitle(title: String, sub: String) { Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp); Text(sub, color = Muted, fontSize = 12.sp) } }
    @Composable private fun EmptyState(title: String, sub: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("♫", color = Purple, fontSize = 42.sp); Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(sub, color = Muted, fontSize = 12.sp) } } }

    @Composable private fun TrackCard(t: LocalTrack, onClick: (LocalTrack) -> Unit) {
        Card(Modifier.fillMaxWidth().clickable { onClick(t) }, colors = CardDefaults.cardColors(containerColor = Panel)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF241A3C), Color(0xFF102B34)))), contentAlignment = Alignment.Center) { Text("♪", color = Cyan, fontSize = 28.sp) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text(t.artist, color = Muted, maxLines = 1) }
            Text("▶", color = Cyan)
        } }
    }

    @Composable private fun OnlineCard(t: OnlineTrack, onPlay: (OnlineTrack) -> Unit, onDownload: (OnlineTrack) -> Unit) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = t.image, contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text(t.artist, color = Muted, maxLines = 1); Text("JAMENDO • STREAM", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            IconButton(onClick = { onPlay(t) }) { Text("▶", color = Green) }
            TextButton(onClick = { onDownload(t) }, enabled = t.downloadAllowed) { Text("↓", color = if (t.downloadAllowed) Purple else Color.DarkGray, fontSize = 20.sp) }
        } }
    }

    @Composable private fun MiniPlayer(title: String, artist: String, playing: Boolean, onPlay: () -> Unit) {
        Surface(color = Panel2, tonalElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Purple), contentAlignment = Alignment.Center) { Text("♪", color = Color.White, fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text(artist, color = Muted, fontSize = 11.sp, maxLines = 1) }
            IconButton(onClick = onPlay) { Text(if (playing) "❚❚" else "▶", color = Cyan, fontSize = 18.sp) }
        } }
    }

    @Composable
    private fun DjStudio() {
        var cross by remember { mutableFloatStateOf(.5f) }
        var bpmA by remember { mutableFloatStateOf(128f) }
        var bpmB by remember { mutableFloatStateOf(128f) }
        var pitchA by remember { mutableFloatStateOf(1f) }
        var pitchB by remember { mutableFloatStateOf(1f) }
        var fxA by remember { mutableStateOf(false) }
        var fxB by remember { mutableStateOf(false) }

        LaunchedEffect(cross) { deckA.volume = (1f - cross).coerceIn(0f, 1f); deckB.volume = cross.coerceIn(0f, 1f) }
        LaunchedEffect(pitchA) { deckA.setPlaybackParameters(PlaybackParameters(pitchA)) }
        LaunchedEffect(pitchB) { deckB.setPlaybackParameters(PlaybackParameters(pitchB)) }
        LaunchedEffect(fxA) { applyFx(deckA, fxA) }
        LaunchedEffect(fxB) { applyFx(deckB, fxB) }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("DJ STUDIO", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black); Text("REAL-TIME TWO-DECK MIXER", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Text("A • B", color = Purple, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Deck("A", deckA, bpmA, { bpmA = it }, pitchA, { pitchA = it }, fxA, { fxA = !fxA }, Purple, { deckA.loadFirstTrack() })
                Deck("B", deckB, bpmB, { bpmB = it }, pitchB, { pitchB = it }, fxB, { fxB = !fxB }, Cyan, { deckB.loadFirstTrack() })
            }
            Spacer(Modifier.height(12.dp))
            Text("CROSSFADER", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Slider(value = cross, onValueChange = { cross = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("DECK A", color = Purple); Text("CENTER", color = Muted); Text("DECK B", color = Cyan) }
            Spacer(Modifier.height(10.dp))
            Text("SCRATCH / JOG", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Jog("A", deckA, Purple); Jog("B", deckB, Cyan)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { syncBpm(deckA, deckB, bpmA, bpmB); bpmB = bpmA }, Modifier.weight(1f)) { Text("SYNC B → A") }
                OutlinedButton(onClick = { deckA.seekTo(0); deckA.pause() }, Modifier.weight(1f)) { Text("CUE A") }
                OutlinedButton(onClick = { deckB.seekTo(0); deckB.pause() }, Modifier.weight(1f)) { Text("CUE B") }
            }
            Spacer(Modifier.height(8.dp)); Text("Use LOAD A / LOAD B to load the first downloaded/local tracks. Drag the jog wheels to scratch by moving the playhead.", color = Muted, fontSize = 11.sp)
        }
    }

    @Composable private fun RowScope.Deck(label: String, deck: ExoPlayer, bpm: Float, setBpm: (Float) -> Unit, pitch: Float, setPitch: (Float) -> Unit, fx: Boolean, toggleFx: () -> Unit, accent: Color, loadFirst: () -> Unit) {
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("DECK $label", color = Color.White, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text("${bpm.toInt()} BPM", color = accent, fontWeight = FontWeight.Bold) }
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF252A3A), Color(0xFF0B0D14)))), contentAlignment = Alignment.Center) { Text("◎", color = accent, fontSize = 64.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = loadFirst, Modifier.weight(1f)) { Text("LOAD") }
                Button(onClick = { if (deck.isPlaying) deck.pause() else deck.play() }, Modifier.weight(1f)) { Text(if (deck.isPlaying) "PAUSE" else "PLAY") }
            }
            Text("PITCH", color = Muted, fontSize = 10.sp); Slider(value = pitch, onValueChange = setPitch, valueRange = .85f..1.15f)
            Text("BPM", color = Muted, fontSize = 10.sp); Slider(value = bpm, onValueChange = setBpm, valueRange = 80f..180f)
            OutlinedButton(onClick = { toggleFx() }, Modifier.fillMaxWidth()) { Text(if (fx) "FX ON • BASS + EQ" else "FX OFF") }
        } }
    }

    @Composable private fun RowScope.Jog(label: String, deck: ExoPlayer, accent: Color) {
        Box(Modifier.weight(1f).height(120.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF2A2E3C), Color(0xFF0D0F17)))).pointerInput(Unit) {
            detectDragGestures { _, drag -> seek(deck, (drag.x * 25).toLong()) }
        }, contentAlignment = Alignment.Center) { Text("$label\n◉", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp) }
    }

    private fun ExoPlayer.loadFirstTrack() {
        val source = (localTracks + downloaded).distinctBy { it.uri.toString() }.firstOrNull() ?: return
        setMediaItem(MediaItem.fromUri(source.uri))
        prepare()
        Toast.makeText(this@MainActivity, "Loaded: ${source.title}", Toast.LENGTH_SHORT).show()
    }

    private fun syncBpm(a: ExoPlayer, b: ExoPlayer, bpmA: Float, bpmB: Float) {
        if (bpmB > 0f) b.setPlaybackParameters(PlaybackParameters((bpmA / bpmB).coerceIn(.5f, 2f)))
    }

    private fun applyFx(deck: ExoPlayer, enabled: Boolean) {
        try {
            val session = deck.audioSessionId
            if (session <= 0) return
            val isA = deck === deckA
            if (enabled) {
                val eq = Equalizer(0, session).apply {
                    setEnabled(true)
                    for (band in 0 until numberOfBands) setBandLevel(band.toShort(), 700.toShort())
                }
                val bass = BassBoost(0, session).apply { enabled = true; setStrength(700.toShort()) }
                if (isA) { eqA?.release(); bassA?.release(); eqA = eq; bassA = bass }
                else { eqB?.release(); bassB?.release(); eqB = eq; bassB = bass }
            } else {
                if (isA) { eqA?.release(); bassA?.release(); eqA = null; bassA = null }
                else { eqB?.release(); bassB?.release(); eqB = null; bassB = null }
            }
        } catch (_: Exception) { }
    }
}
