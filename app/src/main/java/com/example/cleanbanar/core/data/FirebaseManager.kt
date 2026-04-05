package com.example.cleanbanar.core.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Realtime Database manager for CleanBanar.
 *
 * Database structure:
 * cleanbanar/
 *   bins/
 *     organik/   { percentage: Int, status: String, lastUpdate: Long }
 *     nonOrganik/{ percentage: Int, status: String, lastUpdate: Long }
 *   device/
 *     connectionStatus: String ("ONLINE" / "OFFLINE")
 *     lastSeen: Long
 *   notifications/
 *     {id}/ { title: String, message: String, type: String, timestamp: Long, read: Boolean }
 *   history/
 *     {id}/ { action: String, bin: String, actor: String, timestamp: Long }
 *   users/
 *     {id}/ { name: String, email: String, role: String }
 *   statistics/
 *     daily/
 *       {date}/ { organik: Int, nonOrganik: Int }
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"

    private val database: FirebaseDatabase? by lazy {
        try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured: ${e.message}")
            null
        }
    }

    private val rootRef: DatabaseReference? by lazy {
        database?.getReference("cleanbanar")
    }

    private fun isAvailable(): Boolean {
        return rootRef != null
    }

    // --- Bin Status ---
    fun listenBinStatus(binType: String, callback: (percentage: Int, status: String, lastUpdate: Long) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("bins")?.child(binType) ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val percentage = snapshot.child("percentage").getValue(Int::class.java) ?: 0
                val status = snapshot.child("status").getValue(String::class.java) ?: "TERSEDIA"
                val lastUpdate = snapshot.child("lastUpdate").getValue(Long::class.java) ?: 0L
                callback(percentage, status, lastUpdate)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenBinStatus cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeBinListener(binType: String, listener: ValueEventListener) {
        rootRef?.child("bins")?.child(binType)?.removeEventListener(listener)
    }

    fun updateBinStatus(binType: String, percentage: Int, status: String) {
        val ref = rootRef?.child("bins")?.child(binType) ?: return
        ref.child("percentage").setValue(percentage)
        ref.child("status").setValue(status)
        ref.child("lastUpdate").setValue(System.currentTimeMillis())
    }

    // --- Device Status ---
    fun listenDeviceStatus(callback: (connectionStatus: String, lastSeen: Long) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("device") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("connectionStatus").getValue(String::class.java) ?: "OFFLINE"
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                callback(status, lastSeen)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenDeviceStatus cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeDeviceListener(listener: ValueEventListener) {
        rootRef?.child("device")?.removeEventListener(listener)
    }

    // --- Notifications ---
    fun listenNotifications(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("notifications") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifications = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["title"] = child.child("title").getValue(String::class.java) ?: ""
                    map["message"] = child.child("message").getValue(String::class.java) ?: ""
                    map["type"] = child.child("type").getValue(String::class.java) ?: "info"
                    map["timestamp"] = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    map["read"] = child.child("read").getValue(Boolean::class.java) ?: false
                    notifications.add(map)
                }
                callback(notifications)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenNotifications cancelled: ${error.message}")
            }
        }
        ref.orderByChild("timestamp").addValueEventListener(listener)
        return listener
    }

    fun removeNotificationListener(listener: ValueEventListener) {
        rootRef?.child("notifications")?.removeEventListener(listener)
    }

    // --- History ---
    fun listenHistory(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("history") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val history = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["action"] = child.child("action").getValue(String::class.java) ?: ""
                    map["bin"] = child.child("bin").getValue(String::class.java) ?: ""
                    map["actor"] = child.child("actor").getValue(String::class.java) ?: ""
                    map["timestamp"] = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    history.add(map)
                }
                callback(history)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenHistory cancelled: ${error.message}")
            }
        }
        ref.orderByChild("timestamp").addValueEventListener(listener)
        return listener
    }

    fun removeHistoryListener(listener: ValueEventListener) {
        rootRef?.child("history")?.removeEventListener(listener)
    }

    fun addHistoryEntry(action: String, bin: String, actor: String) {
        val ref = rootRef?.child("history")?.push() ?: return
        ref.child("action").setValue(action)
        ref.child("bin").setValue(bin)
        ref.child("actor").setValue(actor)
        ref.child("timestamp").setValue(System.currentTimeMillis())
    }

    // --- Users (Staff Management) ---
    fun listenUsers(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("users") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["name"] = child.child("name").getValue(String::class.java) ?: ""
                    map["email"] = child.child("email").getValue(String::class.java) ?: ""
                    map["role"] = child.child("role").getValue(String::class.java) ?: ""
                    users.add(map)
                }
                callback(users)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenUsers cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeUsersListener(listener: ValueEventListener) {
        rootRef?.child("users")?.removeEventListener(listener)
    }

    fun addUser(name: String, email: String, role: String) {
        val ref = rootRef?.child("users")?.push() ?: return
        ref.child("name").setValue(name)
        ref.child("email").setValue(email)
        ref.child("role").setValue(role)
    }

    fun updateUser(userId: String, name: String, email: String) {
        val ref = rootRef?.child("users")?.child(userId) ?: return
        ref.child("name").setValue(name)
        ref.child("email").setValue(email)
    }

    fun deleteUser(userId: String) {
        rootRef?.child("users")?.child(userId)?.removeValue()
    }

    // --- Statistics ---
    fun listenDailyStats(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("statistics")?.child("daily") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val stats = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val map = mutableMapOf<String, Any>()
                    map["date"] = child.key ?: ""
                    map["organik"] = child.child("organik").getValue(Int::class.java) ?: 0
                    map["nonOrganik"] = child.child("nonOrganik").getValue(Int::class.java) ?: 0
                    stats.add(map)
                }
                callback(stats)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenDailyStats cancelled: ${error.message}")
            }
        }
        ref.orderByKey().limitToLast(7).addValueEventListener(listener)
        return listener
    }

    fun removeStatsListener(listener: ValueEventListener) {
        rootRef?.child("statistics")?.child("daily")?.removeEventListener(listener)
    }
}
