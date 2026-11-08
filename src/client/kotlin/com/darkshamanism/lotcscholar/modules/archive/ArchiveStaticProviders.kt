package com.darkshamanism.lotcscholar.modules.archive

import com.darkshamanism.lotcscholar.services.StaticBooks
import com.darkshamanism.lotcscholar.services.StaticBooksData
import com.darkshamanism.lotcscholar.services.StaticBooksProvider
import com.darkshamanism.lotcscholar.services.StaticItems
import com.darkshamanism.lotcscholar.services.StaticItemsData
import com.darkshamanism.lotcscholar.services.StaticItemsProvider
import com.darkshamanism.lotcscholar.services.StaticSigns
import com.darkshamanism.lotcscholar.services.StaticSignsData
import com.darkshamanism.lotcscholar.services.StaticSignsProvider

internal object ArchiveStaticProviders {

    private object BooksProvider : StaticBooksProvider {
        override val worlds: List<String>
            get() = StaticBooksData.WORLDS

        override fun booksForWorld(world: String) = StaticBooksData.booksForWorld(world)
    }

    private object ItemsProvider : StaticItemsProvider {
        override val worlds: List<String>
            get() = StaticItemsData.WORLDS

        override fun itemsForWorld(world: String) = StaticItemsData.itemsForWorld(world)
    }

    private object SignsProvider : StaticSignsProvider {
        override val worlds: List<String>
            get() = StaticSignsData.WORLDS

        override fun signsForWorld(world: String) = StaticSignsData.signsForWorld(world)
    }

    fun register() {
        StaticBooks.registerProvider(BooksProvider)
        StaticItems.registerProvider(ItemsProvider)
        StaticSigns.registerProvider(SignsProvider)
    }
}

