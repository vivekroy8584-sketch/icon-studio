package com.example.iconstudio

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class AppItem(val label: String, val pkg: String, val icon: Bitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = loadApps(this)
        setContent { MaterialTheme { IconStudio(apps) } }
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm).toBitmap(96, 96)) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun decodeSquare(ctx: Context, uri: Uri, size: Int = 192): Bitmap? =
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { src ->
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        Bitmap.createScaledBitmap(square, size, size, true)
    }

fun pinShortcut(ctx: Context, app: AppItem, name: String, icon: Bitmap) {
    val sm = ctx.getSystemService(ShortcutManager::class.java)
    if (!sm.isRequestPinShortcutSupported) {
        Toast.makeText(ctx, "Your launcher doesn't support pinned shortcuts", Toast.LENGTH_LONG).show()
        return
    }
    val launch = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return
    launch.action = Intent.ACTION_MAIN
    val info = ShortcutInfo.Builder(ctx, "${app.pkg}-${System.currentTimeMillis()}")
        .setShortLabel(name.ifBlank { app.label })
        .setIcon(Icon.createWithBitmap(icon))
        .setIntent(launch)
        .build()
    sm.requestPinShortcut(info, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconStudio(apps: List<AppItem>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppItem?>(null) }
    var name by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) custom = decodeSquare(ctx, uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Icon Studio") }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(apps.filter { it.label.contains(query, ignoreCase = true) }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = app; name = app.label; custom = null
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(app.icon.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label)
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Customize ${app.label}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        (custom ?: app.icon).asImageBitmap(), null,
                        Modifier.size(96.dp).clip(RoundedCornerShape(22.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choose image") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Label") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = custom != null, onClick = {
                    pinShortcut(ctx, app, name, custom!!)
                    selected = null
                }) { Text("Add to home screen") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}
