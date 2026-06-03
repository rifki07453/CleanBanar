package com.example.cleanbanar.core.data

data class PinConfig(
    val trigOrganik: Int = 12,
    val echoOrganik: Int = 13,
    val trigNonOrganik: Int = 14,
    val echoNonOrganik: Int = 15
)

data class DeviceConfig(
    val pins: PinConfig = PinConfig()
)

data class DeviceModel(
    val id: String = "",
    val nama: String = "",
    val statusKoneksi: String = "OFFLINE",
    val terakhirTerlihat: Long = 0L,
    val tipeJaringan: String = "WIFI",
    val config: DeviceConfig = DeviceConfig()
)
