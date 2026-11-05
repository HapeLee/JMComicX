package io.kazusa.jmcomic.effect

import io.kazusa.jmcomic.effect.liquid.TopBarGlassScrollState
import io.kazusa.jmcomic.effect.liquid.topBarGlassFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarGlassScrollTest {
    @Test
    fun returningToTopRemovesGlassButScrollingUpBelowTopKeepsIt() {
        assertEquals(0f, topBarGlassFraction(0, 0f, 96f), 0f)
        assertEquals(0.5f, topBarGlassFraction(0, 48f, 96f), 0f)
        assertEquals(1f, topBarGlassFraction(4, 0f, 96f), 0f)
        assertEquals(1f, topBarGlassFraction(0, 200f, 96f), 0f)
        assertEquals(0f, topBarGlassFraction(0, -12f, 96f), 0f)
    }

    @Test
    fun preloadedOrDisposedPageCannotOverwriteTheCurrentPagesScroll() {
        val state = TopBarGlassScrollState()
        val previousPage = Any()
        val currentPage = Any()
        state.attach(previousPage)
        state.update(previousPage, 1f)
        state.attach(currentPage)
        state.update(currentPage, 0.5f)
        state.update(previousPage, 0f)
        state.detach(previousPage)
        assertEquals(0.5f, state.targetAlpha, 0f)
        state.detach(currentPage)
        assertEquals(0f, state.targetAlpha, 0f)
    }

    @Test
    fun switchingToAnUnscrolledPageResetsGlassWithoutKeepingTheOldOffset() {
        val state = TopBarGlassScrollState()
        val scrolled = Any()
        state.attach(scrolled)
        state.update(scrolled, 1f)
        state.attach(Any())
        assertEquals(0f, state.targetAlpha, 0f)
    }
}
