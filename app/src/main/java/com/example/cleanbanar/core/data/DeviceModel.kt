package com.example.cleanbanar.core.data

data class PinConfig(
    val trigOrganik: Int = 5,
    val echoOrganik: Int = 18,
    val trigNonOrganik: Int = 16,
    val echoNonOrganik: Int = 17,
    val trigLuarOrganik: Int = 22,
    val echoLuarOrganik: Int = 23,
    val trigLuarNonOrganik: Int = 19,
    val echoLuarNonOrganik: Int = 21,
    val servoOrganik: Int = 4,
    val servoNonOrganik: Int = 15
)

data class DeviceConfig(
    val pins: PinConfig = PinConfig(),
    val tinggiTong: Double = 50.0,
    val batasPenuh: Double = 5.0,
    val batasJarakTangan: Double = 15.0
)

data class DeviceModel(
    val id: String = "",
    val nama: String = "",
    val statusKoneksi: String = "OFFLINE",
    val terakhirTerlihat: Long = 0L,
    val tipeJaringan: String = "WIFI",
    val ipAddress: String = "-",
    val kekuatanSinyal: Int = 0,
    val config: DeviceConfig = DeviceConfig()
)
