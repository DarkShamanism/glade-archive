package com.darkshamanism.lotcscholar.services

object StaticSignsData {

    val WORLDS = listOf("Aegis", "Anthos", "Athera", "Vailor", "Atlas", "Axios/Tahn", "Axios/Asul", "Axios/Ceru")

    fun signsForWorld(world: String): List<StaticSign> = when (world) {
        "Aegis" -> StaticSignsData_AEGIS.AEGIS
        "Anthos" -> StaticSignsData_ANTHOS.ANTHOS
        "Athera" -> StaticSignsData_ATHERA.ATHERA
        "Vailor" -> StaticSignsData_VAILOR.VAILOR
        "Atlas" -> StaticSignsData_ATLAS.ATLAS
        "Axios/Tahn" -> StaticSignsData_AXIOS_TAHN.AXIOS_TAHN
        "Axios/Asul" -> StaticSignsData_AXIOS_ASUL.AXIOS_ASUL
        "Axios/Ceru" -> StaticSignsData_AXIOS_CERU.AXIOS_CERU
        else -> emptyList()
    }
}
