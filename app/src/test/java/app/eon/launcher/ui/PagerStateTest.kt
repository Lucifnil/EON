package app.eon.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PagerStateTest {
    @Test
    fun `indicator follows pager offset continuously`() {
        assertEquals(1.35f, pagerIndicatorPosition(currentPage = 1, offsetFraction = 0.35f, pageCount = 3), 0.0001f)
        assertEquals(0.75f, pagerIndicatorPosition(currentPage = 1, offsetFraction = -0.25f, pageCount = 3), 0.0001f)
    }

    @Test
    fun `indicator stays inside tab range`() {
        assertEquals(0f, pagerIndicatorPosition(0, -0.8f, 3), 0f)
        assertEquals(2f, pagerIndicatorPosition(2, 0.8f, 3), 0f)
    }
}
