package com.almeer.music

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

private data class Track(
    val title: String,
    val artist: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        setContent { App(player, scanMusic()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            setContent { App(player, scanMusic()) }
        }
    }

    private fun scanMusic(): List<Track> {
        val result = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown title"
                val artist = cursor.getString(artistColumn) ?: "Unknown artist"
                val uri = ContentUris.withAppendedId(collection, id)
                result.add(Track(title, artist, uri))
            }
        }

        return result
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun App(player: ExoPlayer, tracks: List<Track>) {
    var search by remember { mutableStateOf("") }
    var djMode by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Track?>(null) }

    val filtered = tracks.filter {
        it.title.contains(search, ignoreCase = true) ||
            it.artist.contains(search, ignoreCase = true)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF08090D),
            surface = Color(0xFF11131A),
            primary = Color(0xFFB56CFF),
            secondary = Color(0xFF00D9FF)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = "ALMEER MUSIC",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(20.dp)
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search your music") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { djMode = false }) { Text("MY MUSIC") }
                TextButton(onClick = { djMode = true }) { Text("DJ STUDIO") }
            }

            if (!djMode) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    current = track
                                    player.setMediaItem(MediaItem.fromUri(track.uri))
                                    player.prepare()
                                    player.play()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title)
                                Text(track.artist, color = Color.Gray)
                            }
                            Text("▶")
                        }
                    }
                }
            } else {
                DjStudio(tracks, Modifier.weight(1f))
            }

            current?.let { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title)
                            Text(track.artist, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        ) {
                            Text(if (player.isPlaying) "PAUSE" else "PLAY")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DjStudio(tracks: List<Track>, modifier: Modifier) {
    var crossfader by remember { mutableFloatStateOf(0.5f) }
    var pitchA by remember { mutableFloatStateOf(0f) }
    var pitchB by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.padding(16.dp)) {
        Text("DJ STUDIO", style = MaterialTheme.typography.headlineSmall)
        Text("OFFLINE WORKSPACE", color = MaterialTheme.colorScheme.secondary)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Deck("DECK A", pitchA) { pitchA = it }
            Spacer(modifier = Modifier.width(8.dp))
            Deck("DECK B", pitchB) { pitchB = it }
        }

        Text("CROSSFADER")
        Slider(
            value = crossfader,
            onValueChange = { crossfader = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {}) { Text("CUE A") }
            OutlinedButton(onClick = {}) { Text("SYNC") }
            OutlinedButton(onClick = {}) { Text("CUE B") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("SCRATCH PAD")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Touch / drag to scratch")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("LOCAL TRACKS")
        LazyColumn {
            items(tracks.take(30)) { track ->
                Text(
                    text = track.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.Deck(
    name: String,
    pitch: Float,
    onPitchChange: (Float) -> Unit
) {
    Card(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(name)
            Text("BPM 128", color = MaterialTheme.colorScheme.secondary)
            Text("PITCH ${"%.1f".format(pitch)}%")
            Slider(
                value = pitch,
                onValueChange = onPitchChange,
                valueRange = -16f..16f
            )
            Row {
                TextButton(onClick = {}) { Text("PLAY") }
                TextButton(onClick = {}) { Text("LOOP") }
            }
        }
    }
}
