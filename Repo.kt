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

data class Profile(
    val uid: String,
    val name: String,
    val bio: String,
    val avatar: String,
    val phone: String,
    val socials: Map<String, String>
)

data class Peer(val endpointId: String, val uid: String, val name: String, val bio: String, val avatar: String)

data class Friend(
    val uid: String,
    val name: String,
    val bio: String,
    val avatar: String,
    val phone: String,
    val socials: Map<String, String>,
    val since: Long
)

data class Message(
    val id: String,
    val convId: String,
    val fromUid: String,
    val fromName: String,
    val text: String,
    val ts: Long,
    val mine: Boolean
)

data class Group(val gid: String, val name: String, val members: List<String>)

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

    val friends = mutableStateListOf<Friend>()
    val groups = mutableStateListOf<Group>()
    val messages = mutableStateListOf<Message>()
    val unread = mutableStateMapOf<String, Int>()

    // Données en direct (non enregistrées)
    val peers = mutableStateListOf<Peer>()
    val incoming = mutableStateListOf<String>()
    val incomingNames = mutableStateMapOf<String, String>()
    val outgoing = mutableStateListOf<String>()

    init {
        load()
    }

    private fun load() {
        visible = sp.getBoolean("visible", true)
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
                        o.optLong("since", System.currentTimeMillis())
                    )
                )
            }
        }
        sp.getString("groups", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                groups.add(Group(o.getString("gid"), o.optString("name"), jsonToList(o.optJSONArray("members"))))
            }
        }
        sp.getString("messages", null)?.let { s ->
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                messages.add(
                    Message(
                        o.getString("id"), o.getString("conv"), o.optString("from"), o.optString("fromName"),
                        o.optString("text"), o.optLong("ts"), o.optBoolean("mine")
                    )
                )
            }
        }
    }

    private fun saveFriends() {
        val a = JSONArray()
        for (f in friends) {
            a.put(
                JSONObject().put("uid", f.uid).put("name", f.name).put("bio", f.bio).put("avatar", f.avatar)
                    .put("phone", f.phone).put("socials", mapToJson(f.socials)).put("since", f.since)
            )
        }
        sp.edit().putString("friends", a.toString()).apply()
    }

    private fun saveGroups() {
        val a = JSONArray()
        for (g in groups) {
            a.put(JSONObject().put("gid", g.gid).put("name", g.name).put("members", JSONArray(g.members)))
        }
        sp.edit().putString("groups", a.toString()).apply()
    }

    private fun saveMessages() {
        val a = JSONArray()
        for (m in messages.takeLast(800)) {
            a.put(
                JSONObject().put("id", m.id).put("conv", m.convId).put("from", m.fromUid).put("fromName", m.fromName)
                    .put("text", m.text).put("ts", m.ts).put("mine", m.mine)
            )
        }
        sp.edit().putString("messages", a.toString()).apply()
    }

    fun saveProfile(name: String, bio: String, avatar: String, phone: String, socials: Map<String, String>) {
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
        if (i >= 0) friends[i] = f.copy(since = friends[i].since) else friends.add(f)
        saveFriends()
    }

    fun removeFriend(uid: String) {
        friends.removeAll { it.uid == uid }
        saveFriends()
    }

    fun refreshFriendPublic(uid: String, name: String, bio: String, avatar: String) {
        val i = friends.indexOfFirst { it.uid == uid }
        if (i >= 0) {
            friends[i] = friends[i].copy(name = name, bio = bio, avatar = avatar)
            saveFriends()
        }
    }

    fun upsertGroup(g: Group) {
        val i = groups.indexOfFirst { it.gid == g.gid }
        if (i >= 0) groups[i] = g else groups.add(g)
        saveGroups()
    }

    fun addMessage(m: Message): Boolean {
        if (messages.any { it.id == m.id }) return false
        messages.add(m)
        if (!m.mine && activeConv != m.convId) unread[m.convId] = (unread[m.convId] ?: 0) + 1
        saveMessages()
        return true
    }

    fun clearAll() {
        sp.edit().clear().apply()
        profile = null
        friends.clear()
        groups.clear()
        messages.clear()
        unread.clear()
        peers.clear()
        incoming.clear()
        outgoing.clear()
    }
}
