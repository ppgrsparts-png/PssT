package com.pssst.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Découverte et échanges entre téléphones Pssst proches, sans Internet,
 * grâce à Google Nearby Connections (Bluetooth, BLE et Wi-Fi direct).
 */
class NearbyManager(private val ctx: Context, private val repo: Repo) {

    private val client = Nearby.getConnectionsClient(ctx)
    private val serviceId = "com.pssst.app.v1"
    private val strategy = Strategy.P2P_CLUSTER
    private val handler = Handler(Looper.getMainLooper())

    private val epUid = mutableMapOf<String, String>()
    private val connected = mutableSetOf<String>()
    private val requesting = mutableSetOf<String>()
    private val acceptedByMe = mutableSetOf<String>()
    private val seen = LinkedHashSet<String>()
    private val pending = mutableListOf<JSONObject>()
    private var running = false

    fun isNear(uid: String) = repo.peers.any { it.uid == uid }

    fun start() {
        val p = repo.profile ?: return
        if (running || !repo.visible) return
        running = true
        repo.status = "Recherche des personnes autour…"
        client.startAdvertising(
            epName(p), serviceId, lifecycle,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnFailureListener { e ->
            repo.status = "Visibilité impossible : ${e.message}. Activez le Bluetooth et la localisation."
        }
        client.startDiscovery(
            serviceId, discovery,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            repo.status = ""
        }.addOnFailureListener { e ->
            repo.status = "Recherche impossible : ${e.message}. Activez le Bluetooth et la localisation."
        }
    }

    fun stop() {
        running = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
        requesting.clear()
        epUid.clear()
        repo.peers.clear()
    }

    fun restart() {
        stop()
        start()
    }

    private fun epName(p: Profile) = p.uid + "|" + p.name.take(30)

    private fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    // ---------- Découverte et connexion ----------

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            val p = repo.profile ?: return
            val other = info.endpointName.substringBefore("|")
            if (other == p.uid) return
            // Un seul des deux téléphones demande la connexion (l'autre réessaie après 5 s si besoin)
            val delay = if (p.uid < other) 0L else 5000L
            handler.postDelayed({ requestIfNeeded(id) }, delay)
        }

        override fun onEndpointLost(id: String) {}
    }

    private fun requestIfNeeded(id: String) {
        val p = repo.profile ?: return
        if (!running || id in connected || id in requesting) return
        requesting.add(id)
        client.requestConnection(epName(p), id, lifecycle)
            .addOnFailureListener { requesting.remove(id) }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            epUid[id] = info.endpointName.substringBefore("|")
            client.acceptConnection(id, payloadCb)
        }

        override fun onConnectionResult(id: String, res: ConnectionResolution) {
            requesting.remove(id)
            if (res.status.isSuccess) {
                connected.add(id)
                sendHello(id)
            } else {
                epUid.remove(id)
            }
        }

        override fun onDisconnected(id: String) {
            connected.remove(id)
            epUid.remove(id)
            repo.peers.removeAll { it.endpointId == id }
        }
    }

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            try {
                handle(id, JSONObject(String(bytes, Charsets.UTF_8)))
            } catch (e: Exception) {
                // message mal formé : ignoré
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
    }

    private fun send(ep: String, o: JSONObject) {
        client.sendPayload(ep, Payload.fromBytes(o.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun endpointOf(uid: String): String? =
        epUid.entries.firstOrNull { it.value == uid && it.key in connected }?.key

    private fun sendToUid(uid: String, o: JSONObject): Boolean {
        val ep = endpointOf(uid) ?: return false
        send(ep, o)
        return true
    }

    // ---------- Messages du protocole ----------

    private fun sendHello(ep: String) {
        val p = repo.profile ?: return
        send(
            ep, JSONObject().put("t", "hello").put("uid", p.uid).put("name", p.name)
                .put("bio", p.bio).put("avatar", p.avatar)
        )
    }

    /** Carte privée : numéro et réseaux, envoyée seulement après acceptation. */
    private fun card(type: String): JSONObject {
        val p = repo.profile!!
        return JSONObject().put("t", type).put("uid", p.uid).put("name", p.name).put("bio", p.bio)
            .put("avatar", p.avatar).put("phone", p.phone).put("socials", mapToJson(p.socials))
    }

    private fun addFriend(o: JSONObject) {
        val uid = o.optString("uid")
        val isNew = !repo.isFriend(uid)
        repo.upsertFriend(
            Friend(
                uid, o.optString("name"), o.optString("bio"), o.optString("avatar"),
                o.optString("phone"), jsonToMap(o.optJSONObject("socials")), System.currentTimeMillis()
            )
        )
        repo.incoming.remove(uid)
        repo.outgoing.remove(uid)
        acceptedByMe.remove(uid)
        if (isNew) toast("Vous êtes maintenant amis avec ${o.optString("name")} !")
    }

    private fun groupInvite(g: Group): JSONObject {
        val id = UUID.randomUUID().toString()
        seen.add(id)
        return JSONObject().put("t", "ginvite").put("id", id).put("gid", g.gid).put("name", g.name)
            .put("members", JSONArray(g.members)).put("hops", 0)
    }

    private fun handle(ep: String, o: JSONObject) {
        val me = repo.profile ?: return
        when (o.optString("t")) {
            "hello" -> {
                val uid = o.optString("uid")
                if (uid.isEmpty() || uid == me.uid) return
                epUid[ep] = uid
                repo.peers.removeAll { it.uid == uid || it.endpointId == ep }
                repo.peers.add(Peer(ep, uid, o.optString("name"), o.optString("bio"), o.optString("avatar")))
                if (repo.isFriend(uid)) {
                    repo.refreshFriendPublic(uid, o.optString("name"), o.optString("bio"), o.optString("avatar"))
                    send(ep, card("card")) // synchronise le profil entre amis
                    for (g in repo.groups) if (uid in g.members) send(ep, groupInvite(g))
                }
                val waiting = pending.filter { it.optString("to") == uid }
                for (m in waiting) send(ep, m)
                pending.removeAll(waiting)
            }

            "pssst" -> {
                val uid = o.optString("uid")
                if (repo.isFriend(uid)) {
                    send(ep, card("card"))
                } else if (uid in repo.outgoing) {
                    // Les deux se sont envoyé un pssst : c'est accepté
                    acceptedByMe.add(uid)
                    send(ep, card("accept"))
                } else if (uid !in repo.incoming) {
                    repo.incoming.add(uid)
                    repo.incomingNames[uid] = o.optString("name")
                    toast("${o.optString("name")} vous a envoyé un pssst !")
                }
            }

            "accept" -> {
                val uid = o.optString("uid")
                if (uid in repo.outgoing || uid in acceptedByMe || repo.isFriend(uid)) {
                    addFriend(o)
                    send(ep, card("card"))
                }
            }

            "card" -> {
                val uid = o.optString("uid")
                if (uid in acceptedByMe || uid in repo.outgoing || repo.isFriend(uid)) addFriend(o)
            }

            "msg" -> onMsg(ep, o)

            "ginvite" -> {
                if (!seen.add(o.optString("id"))) return
                val members = jsonToList(o.optJSONArray("members"))
                val gid = o.optString("gid")
                if (me.uid in members && gid.isNotEmpty()) {
                    val isNew = repo.groups.none { it.gid == gid }
                    repo.upsertGroup(Group(gid, o.optString("name"), members))
                    if (isNew) toast("Vous avez été ajouté au groupe « ${o.optString("name")} »")
                }
                relay(ep, o, 2)
            }
        }
    }

    private fun onMsg(ep: String, o: JSONObject) {
        val id = o.optString("id")
        if (id.isEmpty() || !seen.add(id)) return
        trimSeen()
        val me = repo.profile ?: return
        val from = o.optString("from")
        val gid = o.optString("gid")
        val to = o.optString("to")
        fun build(conv: String) = Message(id, conv, from, o.optString("fromName"), o.optString("text"), o.optLong("ts"), false)

        if (gid.isNotEmpty()) {
            val g = repo.groups.firstOrNull { it.gid == gid }
            if (g != null && me.uid in g.members && from in g.members) repo.addMessage(build("g:$gid"))
            relay(ep, o, 3)
        } else if (to == me.uid) {
            if (repo.isFriend(from)) repo.addMessage(build(from))
        } else {
            relay(ep, o, 3)
        }
    }

    /** Relais simple : chaque téléphone Pssst retransmet aux autres (nombre de sauts limité). */
    private fun relay(fromEp: String, o: JSONObject, maxHops: Int) {
        val h = o.optInt("hops", 0)
        if (h >= maxHops) return
        o.put("hops", h + 1)
        for (e in connected) if (e != fromEp) send(e, o)
    }

    private fun trimSeen() {
        while (seen.size > 3000) seen.remove(seen.first())
    }

    // ---------- Actions de l'utilisateur ----------

    fun sendPssst(peer: Peer) {
        val me = repo.profile ?: return
        if (peer.uid !in repo.outgoing) repo.outgoing.add(peer.uid)
        send(peer.endpointId, JSONObject().put("t", "pssst").put("uid", me.uid).put("name", me.name))
        toast("Pssst envoyé à ${peer.name}")
    }

    fun accept(uid: String) {
        acceptedByMe.add(uid)
        repo.incoming.remove(uid)
        if (!sendToUid(uid, card("accept"))) toast("Cette personne n’est plus à portée")
    }

    fun decline(uid: String) {
        repo.incoming.remove(uid)
    }

    fun sendDirect(to: String, text: String) {
        val me = repo.profile ?: return
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val o = JSONObject().put("t", "msg").put("id", id).put("from", me.uid).put("fromName", me.name)
            .put("to", to).put("gid", "").put("text", text).put("ts", ts).put("hops", 0)
        seen.add(id)
        repo.addMessage(Message(id, to, me.uid, me.name, text, ts, true))
        if (!sendToUid(to, o)) {
            // Pas en contact direct : relais par les téléphones proches + envoi dès qu'il revient
            for (e in connected) send(e, o)
            pending.add(o)
        }
    }

    fun sendGroup(gid: String, text: String) {
        val me = repo.profile ?: return
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val o = JSONObject().put("t", "msg").put("id", id).put("from", me.uid).put("fromName", me.name)
            .put("to", "").put("gid", gid).put("text", text).put("ts", ts).put("hops", 0)
        seen.add(id)
        repo.addMessage(Message(id, "g:$gid", me.uid, me.name, text, ts, true))
        for (e in connected) send(e, o)
    }

    fun createGroup(name: String, members: List<String>) {
        val me = repo.profile ?: return
        val g = Group(UUID.randomUUID().toString().replace("-", ""), name, (members + me.uid).distinct())
        repo.upsertGroup(g)
        val inv = groupInvite(g)
        for (e in connected) send(e, inv)
        toast("Groupe « $name » créé")
    }
}
