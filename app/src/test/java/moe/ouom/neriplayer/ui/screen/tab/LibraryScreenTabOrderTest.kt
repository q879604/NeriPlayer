package moe.ouom.neriplayer.ui.screen.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScreenTabOrderTest {

    private val availableTabs = listOf(
        LibraryTab.LOCAL,
        LibraryTab.FAVORITE,
        LibraryTab.NETEASE,
        LibraryTab.BILI,
        LibraryTab.QQMUSIC
    )

    @Test
    fun `blank storage keeps the default order`() {
        assertEquals(availableTabs, resolveLibraryTabOrder(null, availableTabs))
        assertEquals(availableTabs, resolveLibraryTabOrder("   ", availableTabs))
    }

    @Test
    fun `stored order is applied and appended with newly available tabs`() {
        val resolved = resolveLibraryTabOrder(
            storageValue = "FAVORITE,BILI",
            availableTabs = availableTabs
        )
        assertEquals(
            listOf(
                LibraryTab.FAVORITE,
                LibraryTab.BILI,
                LibraryTab.LOCAL,
                LibraryTab.NETEASE,
                LibraryTab.QQMUSIC
            ),
            resolved
        )
    }

    @Test
    fun `order drops tabs that are no longer available and duplicates`() {
        val resolved = resolveLibraryTabOrder(
            storageValue = "YTMUSIC,BILI,BILI,LOCAL",
            availableTabs = availableTabs
        )
        assertEquals(
            listOf(
                LibraryTab.BILI,
                LibraryTab.LOCAL,
                LibraryTab.FAVORITE,
                LibraryTab.NETEASE,
                LibraryTab.QQMUSIC
            ),
            resolved
        )
    }

    @Test
    fun `order storage round trips`() {
        val order = listOf(LibraryTab.BILI, LibraryTab.LOCAL, LibraryTab.FAVORITE)
        assertEquals(
            order,
            resolveLibraryTabOrder(libraryTabOrderStorageValue(order), order)
        )
    }

    @Test
    fun `move reorders within bounds only`() {
        assertEquals(
            listOf(LibraryTab.FAVORITE, LibraryTab.LOCAL, LibraryTab.NETEASE),
            replaceLibraryTabOrder(
                listOf(LibraryTab.LOCAL, LibraryTab.FAVORITE, LibraryTab.NETEASE),
                fromIndex = 0,
                toIndex = 1
            )
        )
        assertEquals(
            availableTabs,
            replaceLibraryTabOrder(availableTabs, fromIndex = -1, toIndex = 2)
        )
        assertEquals(
            availableTabs,
            replaceLibraryTabOrder(availableTabs, fromIndex = 1, toIndex = 99)
        )
    }

    @Test
    fun `default tab resolves only when available`() {
        assertNull(resolveLibraryDefaultTab(null, availableTabs))
        assertNull(resolveLibraryDefaultTab("BILI", availableTabs = emptyList()))
        assertEquals(
            LibraryTab.BILI,
            resolveLibraryDefaultTab("BILI", availableTabs)
        )
    }

    @Test
    fun `visible tab mapping folds album tab into netease`() {
        assertEquals(LibraryTab.NETEASE, LibraryTab.NETEASEALBUM.asVisibleLibraryTab())
        assertEquals(LibraryTab.BILI, LibraryTab.BILI.asVisibleLibraryTab())
        assertEquals(LibraryTab.LOCAL, LibraryTab.LOCAL.asVisibleLibraryTab())
    }

    @Test
    fun `refreshable tabs follow the visible mapping`() {
        assertTrue(LibraryTab.NETEASEALBUM.asVisibleLibraryTab().isRefreshable())
        assertTrue(LibraryTab.BILI.isRefreshable())
        assertFalse(LibraryTab.LOCAL.isRefreshable())
        val noTab: LibraryTab? = null
        assertFalse(noTab.isRefreshable())
    }
}
