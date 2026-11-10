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
 *
 * Protocole (JSON) :
 *  hello   : profil public + clé publique, envoyé à chaque connexion
 *  pssst   : demande d'amitié
 *  accept / card : carte privée (numéro, réseaux, clé publique) après acceptation
 *  msg     : message direct (to) ou de groupe (gid), chiffré si possible (enc)
 *  ack     : accusé de réception d'un message direct
 *  ginvite : invitation / synchronisation d'un groupe (+ clé du groupe chiffrée par membre)
 *  gleave  : un membre quitte le groupe
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
    private val keyCache = mutableMapOf<String, ByteArray>()
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
            running = false
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

    /** Après « Effacer toutes mes données ». */
    fun reset() {
        stop()
        seen.clear()
        keyCache.clear()
        acceptedByMe.clear()
    }

    private fun epName(p: Profile) = p.uid + "|" + p.name.take(30)

    private fun toast(t: String) {
        if (Holder.foreground) Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    }

    // ---------- Chiffrement ----------

    private fun shared(pk: String): ByteArray? {
        if (pk.isEmpty() || repo.keyPriv.isEmpty()) return null
        keyCache[pk]?.let { return it }
        val k = Crypto.sharedKey(repo.keyPriv, pk) ?: return null
        keyCache[pk] = k
        return k
    }

    private fun pkOf(uid: String): String =
        repo.friend(uid)?.pk?.takeIf { it.isNotEmpty() }
            ?: repo.peers.firstOrNull { it.uid == uid }?.pk ?: ""

    private fun aad(id: String, from: String, dest: String) = "$id|$from|$dest"

    private fun groupKey(g: Group): ByteArray? =
        if (g.key.isEmpty()) null else try { Crypto.unb64(g.key) } catch (e: Exception) { null }

    /** Texte en clair + indicateur « chiffré ». */
    private fun readText(o: JSONObject, key: ByteArray?, aad: String): Pair<String, Boolean> {
        val enc = o.optString("enc")
        if (enc.isEmpty()) return o.optString("text") to false
        if (key == null) return "$UNREADABLE (clé pas encore reçue)" to true
        return (Crypto.decrypt(key, enc, aad) ?: UNREADABLE) to true
    }

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

    private fun broadcast(o: JSONObject, except: String? = null) {
        for (e in connected.toList()) if (e != except) send(e, o)
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
                .put("bio", p.bio).put("avatar", p.avatar).put("pk", repo.keyPub)
        )
    }

    /** Carte privée : numéro et réseaux, envoyée seulement après acceptation. */
    private fun card(type: String): JSONObject {
        val p = repo.profile!!
        return JSONObject().put("t", type).put("uid", p.uid).put("name", p.name).put("bio", p.bio)
            .put("avatar", p.avatar).put("phone", p.phone).put("socials", mapToJson(p.socials))
            .put("pk", repo.keyPub)
    }

    private fun addFriend(o: JSONObject) {
        val uid = o.optString("uid")
        if (uid.isEmpty()) return
        val isNew = !repo.isFriend(uid)
        val name = o.optString("name")
        repo.upsertFriend(
            Friend(
                uid, name, o.optString("bio"), o.optString("avatar"),
                o.optString("phone"), jsonToMap(o.optJSONObject("socials")), System.currentTimeMillis(),
                o.optString("pk")
            )
        )
        repo.incoming.remove(uid)
        repo.outgoing.remove(uid)
        acceptedByMe.remove(uid)
        Notifs.cancel(ctx, "pssst:$uid")
        if (isNew) {
            toast("Vous êtes maintenant amis avec $name !")
            if (!Holder.foreground) {
                Notifs.social(
                    ctx, "friend:$uid", "Nouvel ami 🎉",
                    "Vous êtes maintenant amis avec $name : numéro et réseaux échangés.",
                    Notifs.OPEN_FRIEND + uid
                )
            }
        }
    }

    private fun groupInvite(g: Group): JSONObject {
        val me = repo.profile!!
        val id = UUID.randomUUID().toString()
        seen.add(id)
        // Clé du groupe chiffrée pour chaque membre dont on connaît la clé publique
        val keys = JSONObject()
        if (g.key.isNotEmpty()) {
            for (m in g.members) {
                if (m == me.uid) continue
                val sk = shared(pkOf(m)) ?: continue
                keys.put(m, Crypto.encrypt(sk, g.key, "gk:" + g.gid))
            }
        }
        return JSONObject().put("t", "ginvite").put("id", id).put("gid", g.gid).put("name", g.name)
            .put("members", JSONArray(g.members)).put("from", me.uid).put("fpk", repo.keyPub)
            .put("keys", keys).put("hops", 0)
    }

    private fun groupMsg(g: Group, m: Message): JSONObject {
        val o = JSONObject().put("t", "msg").put("id", m.id).put("from", m.fromUid).put("fromName", m.fromName)
            .put("to", "").put("gid", g.gid).put("ts", m.ts).put("hops", 0)
        val k = groupKey(g)
        if (k != null) o.put("enc", Crypto.encrypt(k, m.text, aad(m.id, m.fromUid, g.gid))).put("text", "")
        else o.put("text", m.text)
        return o
    }

    /** À chaque rencontre d'un membre : invitation à jour + messages récents du groupe. */
    private fun syncGroups(ep: String, uid: String) {
        val since = System.currentTimeMillis() - 3L * 86_400_000L
        for (g in repo.groups.toList()) {
            if (uid !in g.members) continue
            send(ep, groupInvite(g))
            val conv = "g:" + g.gid
            repo.messages.filter { it.convId == conv && it.ts > since && !it.text.startsWith(UNREADABLE) }
                .takeLast(30).forEach { m ->
                    send(ep, groupMsg(g, m).put("hops", 3)) // 3 = pas de relais supplémentaire
                    if (m.mine) repo.markStatus(m.id, ST_SENT)
                }
        }
    }

    /** Renvoie les messages directs en attente (au destinataire, ou via ce téléphone en relais). */
    private fun flushOutbox(ep: String, uid: String) {
        repo.pruneOutbox()
        val dayAgo = System.currentTimeMillis() - 86_400_000L
        for (m in repo.outbox.toList()) {
            val to = m.optString("to")
            if (to == uid || m.optLong("ts") > dayAgo) {
                send(ep, JSONObject(m.toString()).put("hops", 0))
                if (to == uid) repo.markStatus(m.optString("id"), ST_SENT)
            }
        }
    }

    private fun handle(ep: String, o: JSONObject) {
        val me = repo.profile ?: return
        when (o.optString("t")) {
            "hello" -> {
                val uid = o.optString("uid")
                if (uid.isEmpty() || uid == me.uid) return
                val pk = o.optString("pk")
                epUid[ep] = uid
                repo.peers.removeAll { it.uid == uid || it.endpointId == ep }
                repo.peers.add(Peer(ep, uid, o.optString("name"), o.optString("bio"), o.optString("avatar"), pk))
                if (repo.isFriend(uid)) {
                    repo.refreshFriendPublic(uid, o.optString("name"), o.optString("bio"), o.optString("avatar"), pk)
                    send(ep, card("card")) // synchronise le profil entre amis
                }
                syncGroups(ep, uid)
                flushOutbox(ep, uid)
            }

            "pssst" -> {
                val uid = o.optString("uid")
                val name = o.optString("name")
                if (repo.isFriend(uid)) {
                    send(ep, card("card"))
                } else if (uid in repo.outgoing) {
                    // Les deux se sont envoyé un pssst : c'est accepté
                    acceptedByMe.add(uid)
                    send(ep, card("accept"))
                } else if (uid !in repo.incoming) {
                    repo.incoming.add(uid)
                    repo.incomingNames[uid] = name
                    toast("$name vous a envoyé un pssst !")
                    if (!Holder.foreground) {
                        Notifs.social(
                            ctx, "pssst:$uid", "Pssst ! 👋",
                            "$name vous a envoyé un pssst. Touchez pour accepter ou refuser.",
                            Notifs.OPEN_NEARBY
                        )
                    }
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

            "ack" -> {
                val id = o.optString("id")
                if (id.isEmpty() || !seen.add(id)) return
                if (o.optString("to") == me.uid) {
                    val ref = o.optString("ref")
                    repo.removeOutbox(ref)
                    repo.markStatus(ref, ST_DELIVERED)
                } else relay(ep, o, 3)
            }

            "ginvite" -> {
                if (!seen.add(o.optString("id"))) return
                trimSeen()
                val members = jsonToList(o.optJSONArray("members"))
                val gid = o.optString("gid")
                val name = o.optString("name")
                if (me.uid in members && gid.isNotEmpty() && gid !in repo.leftGroups) {
                    val existing = repo.groups.firstOrNull { it.gid == gid }
                    var key = existing?.key ?: ""
                    if (key.isEmpty()) {
                        val enc = o.optJSONObject("keys")?.optString(me.uid) ?: ""
                        if (enc.isNotEmpty()) {
                            val from = o.optString("from")
                            val pk = repo.friend(from)?.pk?.takeIf { it.isNotEmpty() } ?: o.optString("fpk")
                            shared(pk)?.let { sk -> Crypto.decrypt(sk, enc, "gk:$gid")?.let { key = it } }
                        }
                    }
                    repo.upsertGroup(Group(gid, name, members, key))
                    if (existing == null) {
                        toast("Vous avez été ajouté au groupe « $name »")
                        if (!Holder.foreground) {
                            Notifs.social(ctx, "g:$gid", "Nouveau groupe", "Vous avez été ajouté au groupe « $name »", "g:$gid")
                        }
                    }
                }
                relay(ep, o, 2)
            }

            "gleave" -> {
                if (!seen.add(o.optString("id"))) return
                val gid = o.optString("gid")
                val uid = o.optString("uid")
                repo.groups.firstOrNull { it.gid == gid }?.let { g ->
                    if (uid in g.members) repo.upsertGroup(g.copy(members = g.members - uid))
                }
                relay(ep, o, 3)
            }
        }
    }

    private fun onMsg(ep: String, o: JSONObject) {
        val id = o.optString("id")
        if (id.isEmpty()) return
        val me = repo.profile ?: return
        val first = seen.add(id)
        trimSeen()
        val from = o.optString("from")
        val gid = o.optString("gid")
        val to = o.optString("to")

        if (gid.isNotEmpty()) {
            if (!first) return
            val g = repo.groups.firstOrNull { it.gid == gid }
            if (g != null && me.uid in g.members && from in g.members) {
                val (text, secure) = readText(o, groupKey(g), aad(id, from, gid))
                val m = Message(id, "g:$gid", from, o.optString("fromName"), text, o.optLong("ts"), from == me.uid, ST_SENT, secure)
                if (repo.addMessage(m) && !m.mine) notifyMsg(m, g.name)
            }
            relay(ep, o, 3)
        } else if (to == me.uid) {
            val f = repo.friend(from) ?: return
            if (first) {
                val (text, secure) = readText(o, shared(f.pk), aad(id, from, me.uid))
                val m = Message(id, from, from, o.optString("fromName"), text, o.optLong("ts"), false, ST_DELIVERED, secure)
                if (repo.addMessage(m)) notifyMsg(m, f.name)
            }
            // Accusé de réception (aussi pour un renvoi direct, au cas où le premier accusé s'est perdu)
            if (first || epUid[ep] == from) sendAck(id, from)
        } else if (first) {
            relay(ep, o, 3)
        }
    }

    private fun notifyMsg(m: Message, title: String) {
        if (Holder.foreground && repo.activeConv == m.convId) return
        val isGroup = m.convId.startsWith("g:")
        Notifs.social(ctx, m.convId, title, (if (isGroup) m.fromName + " : " else "") + m.text, m.convId)
    }

    private fun sendAck(ref: String, to: String) {
        val me = repo.profile ?: return
        val id = UUID.randomUUID().toString()
        seen.add(id)
        val a = JSONObject().put("t", "ack").put("id", id).put("ref", ref).put("from", me.uid)
            .put("to", to).put("hops", 0)
        if (!sendToUid(to, a)) broadcast(a)
    }

    /** Relais simple : chaque téléphone Pssst retransmet aux autres (nombre de sauts limité). */
    private fun relay(fromEp: String, o: JSONObject, maxHops: Int) {
        val h = o.optInt("hops", 0)
        if (h >= maxHops) return
        o.put("hops", h + 1)
        broadcast(o, except = fromEp)
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
        Notifs.cancel(ctx, "pssst:$uid")
        if (!sendToUid(uid, card("accept"))) toast("Cette personne n’est plus à portée")
    }

    fun decline(uid: String) {
        repo.incoming.remove(uid)
        Notifs.cancel(ctx, "pssst:$uid")
    }

    fun sendDirect(to: String, text: String) {
        val me = repo.profile ?: return
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val o = JSONObject().put("t", "msg").put("id", id).put("from", me.uid).put("fromName", me.name)
            .put("to", to).put("gid", "").put("ts", ts).put("hops", 0)
        val k = shared(repo.friend(to)?.pk ?: "")
        if (k != null) o.put("enc", Crypto.encrypt(k, text, aad(id, me.uid, to))).put("text", "")
        else o.put("text", text)
        seen.add(id)
        val direct = sendToUid(to, o)
        // Pas en contact direct : relais par les téléphones proches
        if (!direct) broadcast(o)
        val status = if (direct || connected.isNotEmpty()) ST_SENT else ST_WAITING
        repo.addMessage(Message(id, to, me.uid, me.name, text, ts, true, status, k != null))
        // Gardé jusqu'à l'accusé de réception, renvoyé dès que l'ami revient à portée
        repo.addOutbox(o)
    }

    fun sendGroup(gid: String, text: String) {
        val me = repo.profile ?: return
        val g = repo.groups.firstOrNull { it.gid == gid } ?: return
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val m = Message(
            id, "g:$gid", me.uid, me.name, text, ts, true,
            if (connected.isEmpty()) ST_WAITING else ST_SENT, groupKey(g) != null
        )
        seen.add(id)
        repo.addMessage(m)
        broadcast(groupMsg(g, m))
    }

    fun createGroup(name: String, members: List<String>) {
        val me = repo.profile ?: return
        val g = Group(UUID.randomUUID().toString().replace("-", ""), name, (members + me.uid).distinct(), Crypto.randomKey())
        repo.upsertGroup(g)
        broadcast(groupInvite(g))
        toast("Groupe « $name » créé")
    }

    fun leaveGroup(gid: String) {
        val me = repo.profile ?: return
        val id = UUID.randomUUID().toString()
        seen.add(id)
        broadcast(JSONObject().put("t", "gleave").put("id", id).put("gid", gid).put("uid", me.uid).put("hops", 0))
        repo.leaveGroup(gid)
        Notifs.cancel(ctx, "g:$gid")
    }
}
