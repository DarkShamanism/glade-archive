package com.darkshamanism.lotcscholar.services

object StaticBooksData {

    val WORLDS = listOf("Aegis", "Anthos", "Athera", "Vailor", "Atlas", "Axios/Tahn", "Axios/Asul", "Axios/Ceru")

    fun booksForWorld(world: String): List<StaticBook> = when (world) {
        "Aegis" -> StaticBooksData_AEGIS.AEGIS
        "Anthos" -> StaticBooksData_ANTHOS.ANTHOS
        "Athera" -> StaticBooksData_ATHERA.ATHERA
        "Vailor" -> StaticBooksData_VAILOR.VAILOR
        "Atlas" -> StaticBooksData_ATLAS.ATLAS
        "Axios/Tahn" -> StaticBooksData_AXIOS_TAHN.AXIOS_TAHN
        "Axios/Asul" -> StaticBooksData_AXIOS_ASUL.AXIOS_ASUL
        "Axios/Ceru" -> StaticBooksData_AXIOS_CERU.AXIOS_CERU
        else -> emptyList()
    }
}