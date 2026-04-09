package com.example.cleanbanar.core.data

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val password: String,
    val role: String, // "Admin" or "Petugas"
    val assignedAreaId: String = ""
)
