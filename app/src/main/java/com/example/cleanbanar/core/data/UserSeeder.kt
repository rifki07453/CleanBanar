package com.example.cleanbanar.core.data

object UserSeeder {

    fun getSeededUsers(): List<User> {
        return listOf(
            User(
                id = 1,
                name = "Administrator",
                email = "admin@cleanbanar.com",
                password = "admin123",
                role = "Admin"
            ),
            User(
                id = 2,
                name = "Petugas Lapangan",
                email = "petugas@cleanbanar.com",
                password = "petugas123",
                role = "Petugas"
            )
        )
    }
}
