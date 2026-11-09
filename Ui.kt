package com.pssst.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Couleurs et thème (identiques au design Figma) ----------
val Bg = Color(0xFF0E0B1F)
val Surf = Color(0xFF1A1633)
val Surf2 = Color(0xFF241F45)
val Violet = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF4D8D)
val Muted = Color(0xFFA49FC4)
val Green = Color(0xFF2BD98A)
val Amber = Color(0xFFFFB547)
val Blue = Color(0xFF36C2FF)

@Composable
fun PssstTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, onPrimary = Color.White, secondary = Pink,
            background = Bg, onBackground = Color.White, surface = Surf, onSurface = Color.White,
            surfaceVariant = Surf2, onSurfaceVariant = Muted
        )
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) { content() }
    }
}

// ---------- Outils ----------
fun decodeAvatar(b64: String): ImageBitmap? = try {
    if (b64.isEmpty()) null else {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

fun encodeAvatar(ctx: Context, uri: Uri): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 320) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(sq, 160, 160, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

fun waNumber(p: String): String {
    var d = p.filter { it.isDigit() }
    if (d.startsWith("0")) d = "213" + d.drop(1)
    return d
}

fun socialUrl(net: String, handle: String): String {
    val v = handle.trim()
    if (v.startsWith("http")) return v
    val u = v.removePrefix("@")
    return when (net) {
        "Instagram" -> "https://instagram.com/$u"
        "Snapchat" -> "https://www.snapchat.com/add/$u"
        "TikTok" -> "https://www.tiktok.com/@$u"
        "Facebook" -> "https://www.facebook.com/$u"
        "WhatsApp" -> "https://wa.me/" + waNumber(u)
        "LinkedIn" -> "https://www.linkedin.com/in/$u"
        else -> "https://www.google.com/search?q=" + Uri.encode(v)
    }
}

fun openLink(ctx: Context, url: String) {
    try {
        val action = if (url.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW
        ctx.startActivity(Intent(action, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Impossible d’ouvrir ce lien", Toast.LENGTH_SHORT).show()
    }
}

// ---------- Composants ----------
@Composable
fun Avatar(b64: String, name: String, size: Dp) {
    val bmp = remember(b64) { decodeAvatar(b64) }
    if (bmp != null) {
        Image(
            bitmap = bmp, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Pink))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

@Composable
fun PButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) Surf2 else Violet, contentColor = Color.White
        )
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content
    )
}

@Composable
fun SocialBadge(net: String) {
    val (ab, c) = when (net) {
        "Instagram" -> "IG" to Color(0xFFE1306C)
        "Snapchat" -> "SC" to Color(0xFFFFFC00)
        "TikTok" -> "TT" to Color(0xFF25F4EE)
        "Facebook" -> "FB" to Color(0xFF1877F2)
        "WhatsApp" -> "WA" to Color(0xFF25D366)
        else -> "in" to Color(0xFF0A66C2)
    }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c), contentAlignment = Alignment.Center) {
        Text(
            ab, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = if (net == "Snapchat" || net == "TikTok") Color.Black else Color.White
        )
    }
}

// ---------- Navigation ----------
sealed class Screen {
    object Tabs : Screen()
    data class Chat(val conv: String) : Screen()
    data class FriendView(val uid: String) : Screen()
    object NewGroup : Screen()
}

@Composable
fun PssstApp(repo: Repo, nearby: NearbyManager, startNearby: () -> Unit) {
    if (repo.profile == null) {
        ProfileEditor(repo, "Créer mon profil Pssst", "Votre photo, prénom et bio sont visibles par les personnes autour. Votre numéro et vos réseaux ne sont partagés qu’avec ceux que vous acceptez.", onSaved = {})
        return
    }
    LaunchedEffect(Unit) { startNearby() }

    var tab by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }

    BackHandler(enabled = screen != Screen.Tabs) {
        repo.activeConv = null
        screen = Screen.Tabs
    }

    when (val s = screen) {
        is Screen.Chat -> ChatScreen(repo, nearby, s.conv) {
            repo.activeConv = null
            screen = Screen.Tabs
        }
        is Screen.FriendView -> FriendScreen(repo, nearby, s.uid,
            onBack = { screen = Screen.Tabs },
            onChat = { screen = Screen.Chat(s.uid) })
        Screen.NewGroup -> NewGroupScreen(repo, nearby) { screen = Screen.Tabs }
        Screen.Tabs -> {
            val unreadTotal = repo.unread.values.sum()
            val items: List<Pair<String, ImageVector>> = listOf(
                "Autour" to Icons.Filled.LocationOn,
                "Amis" to Icons.Filled.Favorite,
                (if (unreadTotal > 0) "Messages ($unreadTotal)" else "Messages") to Icons.Filled.Email,
                "Profil" to Icons.Filled.AccountCircle
            )
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(containerColor = Surf) {
                        items.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White, selectedTextColor = Color.White,
                                    indicatorColor = Violet, unselectedIconColor = Muted, unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> NearbyScreen(repo, nearby) { screen = Screen.FriendView(it) }
                        1 -> FriendsScreen(repo, nearby) { screen = Screen.FriendView(it) }
                        2 -> MessagesScreen(repo, nearby, onOpen = { screen = Screen.Chat(it) }, onNewGroup = { screen = Screen.NewGroup })
                        else -> ProfileTab(repo, nearby)
                    }
                }
            }
        }
    }
}

// ---------- Profil ----------
@Composable
fun ProfileEditor(
    repo: Repo,
    title: String,
    intro: String?,
    onSaved: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val ctx = LocalContext.current
    val p = repo.profile
    var name by remember { mutableStateOf(p?.name ?: "") }
    var bio by remember { mutableStateOf(p?.bio ?: "") }
    var phone by remember { mutableStateOf(p?.phone ?: "") }
    var avatar by remember { mutableStateOf(p?.avatar ?: "") }
    val socials = remember {
        mutableStateMapOf<String, String>().apply { SOCIALS.forEach { put(it, p?.socials?.get(it) ?: "") } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) encodeAvatar(ctx, uri)?.let { avatar = it }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (intro != null) Text(intro, color = Muted, fontSize = 13.sp)
        Box(
            Modifier.align(Alignment.CenterHorizontally).clickable {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        ) { Avatar(avatar, name.ifEmpty { "?" }, 104.dp) }
        Text(
            "Touchez la photo pour la changer", color = Muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Field("Prénom (visible par tous)", name) { name = it }
        Field("Courte bio (visible par tous)", bio) { bio = it }
        Text("Partagé seulement avec vos amis acceptés", color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Field("Numéro de téléphone", phone, KeyboardType.Phone) { phone = it }
        SOCIALS.forEach { s ->
            Field("$s (vide = non partagé)", socials[s] ?: "") { socials[s] = it }
        }
        PButton("Enregistrer", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
            repo.saveProfile(
                name.trim(), bio.trim(), avatar, phone.trim(),
                socials.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }
            )
            onSaved()
        }
        extra()
    }
}

@Composable
fun ProfileTab(repo: Repo, nearby: NearbyManager) {
    var saved by remember { mutableStateOf(false) }
    ProfileEditor(repo, "Mon profil", null, onSaved = {
        nearby.restart()
        saved = true
    }) {
        if (saved) Text("Profil enregistré. Vos amis proches reçoivent la mise à jour.", color = Green, fontSize = 13.sp)
        Text(
            "Le numéro et les réseaux sont envoyés uniquement aux personnes que vous acceptez, et resynchronisés quand elles sont à proximité.",
            color = Muted, fontSize = 12.sp
        )
        TextButton(onClick = {
            nearby.stop()
            repo.clearAll()
        }) { Text("Effacer toutes mes données", color = Pink) }
    }
}

// ---------- Autour de moi ----------
@Composable
fun NearbyScreen(repo: Repo, nearby: NearbyManager, openFriend: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("pssst", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.weight(1f))
                Text(if (repo.visible) "Visible" else "En pause", color = if (repo.visible) Green else Muted, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Switch(checked = repo.visible, onCheckedChange = {
                    repo.setVisibleFlag(it)
                    if (it) nearby.start() else {
                        nearby.stop()
                        repo.status = "En pause : vous n’êtes plus visible et ne voyez plus personne."
                    }
                })
            }
        }
        item {
            InfoCard {
                Text(
                    if (repo.peers.isEmpty()) "Personne autour pour l’instant" else "${repo.peers.size} personne(s) autour de vous",
                    fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp
                )
                Text(
                    if (repo.status.isNotEmpty()) repo.status
                    else "Bluetooth actif. Vos amis doivent avoir Pssst ouvert, à quelques dizaines de mètres.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
        val reqs = repo.incoming.toList()
        items(reqs, key = { "req_$it" }) { uid ->
            val name = repo.peers.firstOrNull { it.uid == uid }?.name ?: repo.incomingNames[uid] ?: "Quelqu’un"
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf2).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("$name vous a envoyé un pssst !", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text("Si vous acceptez, vous échangez vos numéros et réseaux sociaux.", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("Refuser", Modifier.weight(1f), secondary = true) { nearby.decline(uid) }
                    PButton("Accepter", Modifier.weight(1f)) { nearby.accept(uid) }
                }
            }
        }
        items(repo.peers.toList(), key = { it.uid }) { p -> PeerRow(p, repo, nearby, openFriend) }
    }
}

@Composable
fun PeerRow(p: Peer, repo: Repo, nearby: NearbyManager, openFriend: (String) -> Unit) {
    val isFriend = repo.isFriend(p.uid)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf)
            .clickable(enabled = isFriend) { openFriend(p.uid) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p.avatar, p.name, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            if (p.bio.isNotEmpty()) Text(p.bio, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("À proximité", color = Blue, fontSize = 11.sp)
        }
        when {
            isFriend -> Text("Ami", color = Green, fontWeight = FontWeight.Bold)
            p.uid in repo.incoming -> PButton("Accepter") { nearby.accept(p.uid) }
            p.uid in repo.outgoing -> Text("Envoyé", color = Muted)
            else -> PButton("Pssst") { nearby.sendPssst(p) }
        }
    }
}

// ---------- Amis ----------
@Composable
fun FriendsScreen(repo: Repo, nearby: NearbyManager, onOpen: (String) -> Unit) {
    val list = repo.friends.sortedByDescending { it.since }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { Text("Mes amis (${list.size})", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (list.isEmpty()) item {
            Text("Aucun ami pour l’instant. Envoyez un pssst à quelqu’un dans l’onglet Autour.", color = Muted)
        }
        items(list, key = { it.uid }) { f ->
            val near = nearby.isNear(f.uid)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf)
                    .clickable { onOpen(f.uid) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(f.avatar, f.name, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(
                        if (near) "À proximité" else if (f.phone.isNotEmpty()) f.phone else "Profil partagé",
                        color = if (near) Green else Muted, fontSize = 12.sp
                    )
                }
                Text(f.socials.keys.joinToString(" · ") { it.take(2) }, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun FriendScreen(repo: Repo, nearby: NearbyManager, uid: String, onBack: () -> Unit, onChat: () -> Unit) {
    val ctx = LocalContext.current
    val f = repo.friend(uid)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
        if (f == null) {
            Text("Cet ami n’existe plus.", color = Muted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(f.avatar, f.name, 84.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(f.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (f.bio.isNotEmpty()) Text(f.bio, color = Muted)
                Text(
                    "Ami depuis le " + SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(f.since)),
                    color = Muted, fontSize = 12.sp
                )
                if (nearby.isNear(f.uid)) Text("À proximité", color = Green, fontSize = 12.sp)
            }
        }
        if (f.phone.isNotEmpty()) {
            InfoCard {
                Text("Téléphone", color = Muted, fontSize = 12.sp)
                Text(f.phone, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton("Appeler", Modifier.weight(1f), secondary = true) {
                        openLink(ctx, "tel:" + f.phone.filter { it.isDigit() || it == '+' })
                    }
                    PButton("WhatsApp", Modifier.weight(1f), secondary = true) {
                        openLink(ctx, "https://wa.me/" + waNumber(f.phone))
                    }
                }
            }
        }
        if (f.socials.isNotEmpty()) {
            Text("Réseaux sociaux", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
            f.socials.forEach { (net, h) ->
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SocialBadge(net)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(net, color = Muted, fontSize = 12.sp)
                            Text(h, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        TextButton(onClick = { openLink(ctx, socialUrl(net, h)) }) { Text("Ouvrir", color = Pink) }
                    }
                }
            }
        }
        PButton("Envoyer un message", Modifier.fillMaxWidth()) { onChat() }
        TextButton(onClick = {
            repo.removeFriend(uid)
            onBack()
        }) { Text("Retirer de mes amis", color = Pink) }
    }
}

// ---------- Messages ----------
@Composable
fun MessagesScreen(repo: Repo, nearby: NearbyManager, onOpen: (String) -> Unit, onNewGroup: () -> Unit) {
    val convs = (repo.groups.map { Triple("g:" + it.gid, it.name, true) } +
            repo.friends.map { Triple(it.uid, it.name, false) })
        .sortedByDescending { c -> repo.messages.lastOrNull { it.convId == c.first }?.ts ?: 0L }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Messages", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                PButton("Nouveau groupe") { onNewGroup() }
            }
        }
        item {
            InfoCard {
                Text("Mode Bluetooth", fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Les messages partent sans Internet vers vos amis proches, et peuvent être relayés par les autres téléphones Pssst autour.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
        if (convs.isEmpty()) item {
            Text("Aucune discussion. Ajoutez des amis avec un pssst pour discuter.", color = Muted)
        }
        items(convs, key = { it.first }) { (conv, title, isGroup) ->
            val last = repo.messages.lastOrNull { it.convId == conv }
            val un = repo.unread[conv] ?: 0
            val friend = if (isGroup) null else repo.friend(conv)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf)
                    .clickable { onOpen(conv) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(friend?.avatar ?: "", title, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        if (isGroup) Text("  groupe", color = Amber, fontSize = 11.sp)
                        else if (nearby.isNear(conv)) Text("  à proximité", color = Blue, fontSize = 11.sp)
                    }
                    Text(
                        last?.let { (if (it.mine) "Vous : " else if (isGroup) it.fromName + " : " else "") + it.text } ?: "Aucun message",
                        color = if (un > 0) Color.White else Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (un > 0) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Pink), contentAlignment = Alignment.Center) {
                        Text("$un", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun Bubble(m: Message, showSender: Boolean) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp))
                .background(if (m.mine) Brush.linearGradient(listOf(Violet, Pink)) else Brush.linearGradient(listOf(Surf2, Surf2)))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (showSender && !m.mine) Text(m.fromName, color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(m.text, color = Color.White, fontSize = 15.sp)
            Text(
                fmt.format(Date(m.ts)), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun ChatScreen(repo: Repo, nearby: NearbyManager, conv: String, onBack: () -> Unit) {
    val isGroup = conv.startsWith("g:")
    val group = if (isGroup) repo.groups.firstOrNull { "g:" + it.gid == conv } else null
    val friend = if (!isGroup) repo.friend(conv) else null
    val title = group?.name ?: friend?.name ?: "Discussion"
    val msgs = repo.messages.filter { it.convId == conv }
    val state = rememberLazyListState()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(conv) {
        repo.activeConv = conv
        repo.unread.remove(conv)
    }
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) state.animateScrollToItem(msgs.size - 1)
        repo.unread.remove(conv)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Surf).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
            Avatar(friend?.avatar ?: "", title, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                val sub = when {
                    group != null -> "${group.members.size} membres · ${group.members.count { nearby.isNear(it) }} à proximité"
                    friend != null && nearby.isNear(friend.uid) -> "À proximité · Bluetooth"
                    else -> "Hors de portée · relais par les téléphones Pssst proches"
                }
                Text(sub, color = if (sub.startsWith("À")) Blue else Muted, fontSize = 12.sp)
            }
        }
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (msgs.isEmpty()) item {
                Text(
                    "Dites bonjour ! Les messages passent par Bluetooth quand vous êtes proches.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
            items(msgs, key = { it.id }) { m -> Bubble(m, isGroup) }
        }
        Row(Modifier.fillMaxWidth().background(Surf).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, placeholder = { Text("Message…") },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        if (group != null) nearby.sendGroup(group.gid, t) else nearby.sendDirect(conv, t)
                        text = ""
                    }
                },
                modifier = Modifier.clip(CircleShape).background(Violet)
            ) { Icon(Icons.Filled.Send, contentDescription = "Envoyer", tint = Color.White) }
        }
    }
}

@Composable
fun NewGroupScreen(repo: Repo, nearby: NearbyManager, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
            Text("Nouveau groupe", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Field("Nom du groupe", name) { name = it }
        Text("Choisissez les membres parmi vos amis", color = Muted)
        if (repo.friends.isEmpty()) Text("Ajoutez d’abord des amis avec un pssst.", color = Muted)
        LazyColumn(Modifier.weight(1f)) {
            items(repo.friends.toList(), key = { it.uid }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (f.uid in selected) selected.remove(f.uid) else selected.add(f.uid)
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(f.avatar, f.name, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.Bold, color = Color.White)
                        val near = nearby.isNear(f.uid)
                        Text(if (near) "À proximité" else "Hors de portée", color = if (near) Blue else Muted, fontSize = 12.sp)
                    }
                    Checkbox(checked = f.uid in selected, onCheckedChange = {
                        if (it) selected.add(f.uid) else selected.remove(f.uid)
                    })
                }
            }
        }
        Text(
            "Les membres hors de portée recevront le groupe dès qu’ils seront près d’un membre.",
            color = Muted, fontSize = 12.sp
        )
        PButton("Créer le groupe", Modifier.fillMaxWidth(), enabled = name.isNotBlank() && selected.isNotEmpty()) {
            nearby.createGroup(name.trim(), selected.toList())
            onDone()
        }
    }
}
