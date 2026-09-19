package com.almeer.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

private data class Track(val title:String,val artist:String,val uri:android.net.Uri)

class MainActivity: ComponentActivity() {
 private lateinit var player: ExoPlayer
 private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  player=ExoPlayer.Builder(this).build()
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.READ_MEDIA_AUDIO)
  setContent { App(player, scan()) }
 }
 private fun scan():List<Track>{
  val out=mutableListOf<Track>(); val uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val p=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST)
  contentResolver.query(uri,p,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{c->
   val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val a=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
   while(c.moveToNext()){val i=c.getLong(id);out+=Track(c.getString(t)?:"Unknown",c.getString(a)?:"Unknown artist",android.content.ContentUris.withAppendedId(uri,i))}
  }; return out
 }
 override fun onDestroy(){player.release();super.onDestroy()}
}

@Composable private fun App(player:ExoPlayer,tracks:List<Track>){
 var search by remember{mutableStateOf("")}; var dj by remember{mutableStateOf(false)}; var current by remember{mutableStateOf<Track?>(null)}
 val filtered=tracks.filter{it.title.contains(search,true)||it.artist.contains(search,true)}
 MaterialTheme(colorScheme=darkColorScheme(background=Color(0xFF08090D),surface=Color(0xFF11131A),primary=Color(0xFFB56CFF),secondary=Color(0xFF00D9FF))){
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
   Text("ALMEER MUSIC",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(20.dp))
   OutlinedTextField(search,{search=it},label={Text("Search your music")},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){TextButton({dj=false}){Text("MY MUSIC")};TextButton({dj=true}){Text("DJ STUDIO")}}
   if(!dj) LazyColumn(Modifier.weight(1f)){items(filtered){track->Row(Modifier.fillMaxWidth().clickable{current=track;player.setMediaItem(MediaItem.fromUri(track.uri));player.prepare();player.play()}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(track.title);Text(track.artist,color=Color.Gray)};Text("▶")}}}
   else DjStudio(tracks,Modifier.weight(1f))
   current?.let{Card(Modifier.fillMaxWidth().padding(12.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(it.title);Text(it.artist,color=Color.Gray)};Button({if(player.isPlaying)player.pause()else player.play()}){Text(if(player.isPlaying)"PAUSE" else "PLAY")}}}}
  }
 }
}

@Composable private fun DjStudio(tracks:List<Track>,modifier:Modifier){var cross by remember{mutableFloatStateOf(.5f)};var pitchA by remember{mutableFloatStateOf(0f)};var pitchB by remember{mutableFloatStateOf(0f)};Column(modifier.padding(16.dp)){Text("DJ STUDIO",style=MaterialTheme.typography.headlineSmall);Text("OFFLINE WORKSPACE",color=MaterialTheme.colorScheme.secondary);Row(Modifier.fillMaxWidth().padding(vertical=12.dp)){Deck("DECK A",pitchA){pitchA=it};Spacer(Modifier.width(8.dp));Deck("DECK B",pitchB){pitchB=it}};Text("CROSSFADER");Slider(cross,{cross=it});Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){OutlinedButton({}){Text("CUE A")};OutlinedButton({}){Text("SYNC")};OutlinedButton({}){Text("CUE B")}};Spacer(Modifier.height(12.dp));Text("SCRATCH PAD");Surface(Modifier.fillMaxWidth().height(120.dp)){Box(contentAlignment=Alignment.Center){Text("Touch / drag to scratch")}};Spacer(Modifier.height(12.dp));Text("LOCAL TRACKS");LazyColumn{items(tracks.take(30)){Text(it.title,Modifier.fillMaxWidth().padding(8.dp))}}}}

@Composable private fun Deck(name:String,pitch:Float,onPitch:(Float)->Unit){Card(Modifier.weight(1f)){Column(Modifier.padding(10.dp)){Text(name);Text("BPM 128",color=MaterialTheme.colorScheme.secondary);Text("PITCH ${"%.1f".format(pitch)}%");Slider(pitch,onPitch,valueRange=-16f..16f);Row{TextButton({}){Text("PLAY")};TextButton({}){Text("LOOP")}}}}}
