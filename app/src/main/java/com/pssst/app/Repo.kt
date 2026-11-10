package com.pssst.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val SOCIALS = listOf("Instagram", "Snapchat", "TikTok", "Facebook", "WhatsApp", "LinkedIn")

const val UNREADABLE = "🔒 Message chiffré illisible"

/** État d'un message envoyé. */
const val ST_WAITING = 0   // en attente : personne à portée
const val ST_SENT = 1      // parti (direct ou par relais)
const val ST_DELIVERED = 2 // reçu par l'ami (accusé de réception)

data class Profile(
    val uid: String,
    val name: String,
    val bio: String,
    val avatar: String,
    val phone: String,
    val socials: Map<String, String>
)

data class Peer(
    val endpointId: String,
    val uid: String,
    val name: String,
    val bio: String,
    val avatar: String,
    val pk: String = ""
)

data class Friend(
    val uid: String,
    val name: String,
    val bio: String,
    val avatar: String,
    val phone: String,
    val socials: Map<String, String>,
    val since: Long,
    val pk: String = ""
)

data class Message(
    val id: String,
    val convId: String,
    val fromUid: String,
    val fromName: String,
    val text: String,
    val ts: Long,
    val mine: Boolean,
    val status: Int = ST_DELIVERED,
    val secure: Boolean = false
)

data class Group(val gid: String, val name: String, val members: List<String>, val key: String = "")

fun mapToJson(m: Map<String, String>): JSONObject {
    val o = JSONObject()
    for ((k, v) in m) o.put(k, v)
    return o
}

fun jsonToMap(o: JSONObject?): Map<String, String> {
    if (o == null) return emptyMap()
    val r = mutableMapOf<String, String>()
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        r[k] = o.optString(k)
    }
    return r
}

fun jsonToList(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val r = mutableListOf<String>()
    for (i in 0 until a.length()) r.add(a.optString(i))
    return r
}

class Repo(context: Context) {
    private val sp = context.getSharedPreferences("pssst", Context.MODE_PRIVATE)

    var profile by mutableStateOf<Profile?>(null)
    var visible by mutableStateOf(true)
    var status by mutableStateOf("")
    var activeConv by mutableStateOf<String?>(null)

    /** Écran à ouvrir (depuis une notification). */
    var openTarget by mutableStateOf<String?>(null)

    // Clés de chiffrement (la clé privée ne quitte jamais le téléphone)
    var keyPriv = ""
        private set
    var keyPub = ""
        private set

    val friends = mutableStateListOf<Friend>()
    val groups = mutableStateListOf<Group>()
    val messages = mutableStateListOf<Message>()
    val unread = mutableStateMapOf<String, Int>()

    /** Messages directs pas encore confirmés reçus : renvoyés quand le destinataire revient. */
    val outbox = mutableListOf<JSONObject>()
    val leftGroups = mutableSetOf<String>()

    // Données en direct (non enregistrées)
    val peers = mutableStateListOf<Peer>()
    val incoming = mutableStateListOf<String>()
    val incomingNames = mutableStateMapOf<String, String>()
    val outgoing = mutableStateListOf<String>()

    init {
        load()
        if (profile != null) ensureKeys()
    }

    private fun load() {
        visible = sp.getBoolean("visible", true)
        keyPriv = sp.getString("keyPriv", "") ?: ""
        keyPub = sp.getString("keyPub", "") ?: ""
        sp.getString("profile", null)?.let { s ->
            val o = JSONObject(s)
            profile = Profile(
                o.getString("uid"), o.optString("name"), o.optString("bio"),
                o.optString("avatar"), o.optString("phone"), jsonToMap(o.optJSONObject("socials"))
            )
        }
        sp.getString("friends", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                friends.add(
                    Friend(
                        o.getString("uid"), o.optString("name"), o.optString("bio"), o.optString("avatar"),
                        o.optString("phone"), jsonToMap(o.optJSONObject("socials")),
                        o.optLong("since", System.currentTimeMillis()), o.optString("pk")
                    )
                )
            }
        }
        sp.getString("groups", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                groups.add(
                    Group(o.getString("gid"), o.optString("name"), jsonToList(o.optJSONArray("members")), o.optString("key"))
                )
            }
        }
        sp.getString("messages", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                messages.add(
                    Message(
                        o.getString("id"), o.getString("conv"), o.optString("from"), o.optString("fromName"),
                        o.optString("text"), o.optLong("ts"), o.optBoolean("mine"),
                        o.optInt("status", ST_DELIVERED), o.optBoolean("secure")
                    )
                )
            }
        }
        sp.getString("unread", null)?.let { s -> jsonToMap(JSONObject(s)).forEach { (k, v) -> unread[k] = v.toIntOrNull() ?: 0 } }
        sp.getString("outbox", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) outbox.add(a.getJSONObject(i))
        }
        sp.getString("leftGroups", null)?.let { s -> leftGroups.addAll(jsonToList(JSONArray(s))) }
    }

    private fun ensureKeys() {
        if (keyPriv.isNotEmpty() && keyPub.isNotEmpty()) return
        val (priv, pub) = Crypto.newKeyPair()
        keyPriv = priv
        keyPub = pub
        sp.edit().putString("keyPriv", priv).putString("keyPub", pub).apply()
    }

    private fun saveFriends() {
        val a = JSONArray()
        for (f in friends) {
            a.put(
                JSONObject().put("uid", f.uid).put("name", f.name).put("bio", f.bio).put("avatar", f.avatar)
                    .put("phone", f.phone).put("socials", mapToJson(f.socials)).put("since", f.since).put("pk", f.pk)
            )
        }
        sp.edit().putString("friends", a.toString()).apply()
    }

    private fun saveGroups() {
        val a = JSONArray()
        for (g in groups) {
            a.put(
                JSONObject().put("gid", g.gid).put("name", g.name).put("members", JSONArray(g.members)).put("key", g.key)
            )
        }
        sp.edit().putString("groups", a.toString())
            .putString("leftGroups", JSONArray(leftGroups.toList()).toString()).apply()
    }

    private fun saveMessages() {
        val a = JSONArray()
        for (m in messages.takeLast(800)) {
            a.put(
                JSONObject().put("id", m.id).put("conv", m.convId).put("from", m.fromUid).put("fromName", m.fromName)
                    .put("text", m.text).put("ts", m.ts).put("mine", m.mine)
                    .put("status", m.status).put("secure", m.secure)
            )
        }
        sp.edit().putString("messages", a.toString()).apply()
    }

    private fun saveUnread() {
        sp.edit().putString("unread", mapToJson(unread.mapValues { it.value.toString() }).toString()).apply()
    }

    private fun saveOutbox() {
        val a = JSONArray()
        for (o in outbox) a.put(o)
        sp.edit().putString("outbox", a.toString()).apply()
    }

    fun saveProfile(name: String, bio: String, avatar: String, phone: String, socials: Map<String, String>) {
        ensureKeys()
        val uid = profile?.uid ?: UUID.randomUUID().toString().replace("-", "")
        profile = Profile(uid, name, bio, avatar, phone, socials)
        val o = JSONObject().put("uid", uid).put("name", name).put("bio", bio).put("avatar", avatar)
            .put("phone", phone).put("socials", mapToJson(socials))
        sp.edit().putString("profile", o.toString()).apply()
    }

    fun setVisibleFlag(v: Boolean) {
        visible = v
        sp.edit().putBoolean("visible", v).apply()
    }

    fun isFriend(uid: String) = friends.any { it.uid == uid }

    fun friend(uid: String) = friends.firstOrNull { it.uid == uid }

    fun upsertFriend(f: Friend) {
        val i = friends.indexOfFirst { it.uid == f.uid }
        if (i >= 0) {
            val old = friends[i]
            friends[i] = f.copy(since = old.since, pk = f.pk.ifEmpty { old.pk })
        } else friends.add(f)
        saveFriends()
    }

    fun removeFriend(uid: String) {
        friends.removeAll { it.uid == uid }
        outbox.removeAll { it.optString("to") == uid }
        saveFriends()
        saveOutbox()
    }

    fun refreshFriendPublic(uid: String, name: String, bio: String, avatar: String, pk: String) {
        val i = friends.indexOfFirst { it.uid == uid }
        if (i >= 0) {
            val f = friends[i]
            friends[i] = f.copy(name = name, bio = bio, avatar = avatar, pk = pk.ifEmpty { f.pk })
            saveFriends()
        }
    }

    fun upsertGroup(g: Group) {
        val i = groups.indexOfFirst { it.gid == g.gid }
        if (i >= 0) groups[i] = g.copy(key = g.key.ifEmpty { groups[i].key }) else groups.add(g)
        saveGroups()
    }

    fun leaveGroup(gid: String) {
        groups.removeAll { it.gid == gid }
        leftGroups.add(gid)
        messages.removeAll { it.convId == "g:$gid" }
        unread.remove("g:$gid")
        saveGroups()
        saveMessages()
        saveUnread()
    }

    /** Ajoute un message ; remplace une version illisible si la clé est arrivée entre-temps. */
    fun addMessage(m: Message): Boolean {
        val i = messages.indexOfFirst { it.id == m.id }
        if (i >= 0) {
            if (messages[i].text.startsWith(UNREADABLE) && !m.text.startsWith(UNREADABLE)) {
                messages[i] = m
                saveMessages()
            }
            return false
        }
        messages.add(m)
        val looking = Holder.foreground && activeConv == m.convId
        if (!m.mine && !looking) {
            unread[m.convId] = (unread[m.convId] ?: 0) + 1
            saveUnread()
        }
        saveMessages()
        return true
    }

    fun markRead(conv: String) {
        if (unread.remove(conv) != null) saveUnread()
    }

    /** Fait seulement progresser l'état (attente → envoyé → reçu). */
    fun markStatus(id: String, s: Int) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0 && messages[i].status < s) {
            messages[i] = messages[i].copy(status = s)
            saveMessages()
        }
    }

    fun addOutbox(o: JSONObject) {
        outbox.add(o)
        saveOutbox()
    }

    fun removeOutbox(id: String) {
        if (outbox.removeAll { it.optString("id") == id }) saveOutbox()
    }

    /** Abandonne les messages non remis après 7 jours. */
    fun pruneOutbox() {
        val limit = System.currentTimeMillis() - 7L * 86_400_000L
        if (outbox.removeAll { it.optLong("ts") < limit }) saveOutbox()
    }

    fun clearAll() {
        sp.edit().clear().apply()
        profile = null
        keyPriv = ""
        keyPub = ""
        friends.clear()
        groups.clear()
        messages.clear()
        unread.clear()
        outbox.clear()
        leftGroups.clear()
        peers.clear()
        incoming.clear()
        incomingNames.clear()
        outgoing.clear()
        status = ""
        visible = true
    }
}
