package com.darkshamanism.lotcscholar.services

object StaticItemsData {

    val WORLDS = listOf("Aegis", "Anthos", "Athera", "Vailor", "Atlas", "Axios/Tahn", "Axios/Asul", "Axios/Ceru")

    fun itemsForWorld(world: String): List<StaticItem> = when (world) {
        "Aegis" -> StaticItemsData_AEGIS.AEGIS
        "Anthos" -> StaticItemsData_ANTHOS.ANTHOS
        "Athera" -> StaticItemsData_ATHERA.ATHERA
        "Vailor" -> StaticItemsData_VAILOR.VAILOR
        "Atlas" -> StaticItemsData_ATLAS.ATLAS
        "Axios/Tahn" -> StaticItemsData_AXIOS_TAHN.AXIOS_TAHN
        "Axios/Asul" -> StaticItemsData_AXIOS_ASUL.AXIOS_ASUL
        "Axios/Ceru" -> StaticItemsData_AXIOS_CERU.AXIOS_CERU
        else -> emptyList()
    }
}
