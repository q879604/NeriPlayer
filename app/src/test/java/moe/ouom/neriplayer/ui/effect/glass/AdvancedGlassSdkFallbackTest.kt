package moe.ouom.neriplayer.ui.effect.glass

import android.os.Build
import moe.ouom.neriplayer.data.settings.AdvancedBlurQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGlassSdkFallbackTest {

    @Test
    fun `backend support starts at android 12`() {
        assertFalse(isAdvancedGlassBackendSupported(Build.VERSION_CODES.R))
        assertTrue(isAdvancedGlassBackendSupported(Build.VERSION_CODES.S))
        assertTrue(isAdvancedGlassBackendSupported(Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun `runtime shader support starts at android 13`() {
        assertFalse(supportsAdvancedGlassRuntimeShader(Build.VERSION_CODES.S))
        assertFalse(supportsAdvancedGlassRuntimeShader(Build.VERSION_CODES.S_V2))
        assertTrue(supportsAdvancedGlassRuntimeShader(Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun `fullscreen mask profile degrades to region local before android 13`() {
        val profile = AdvancedBlurQuality.High.renderProfileForSdk(Build.VERSION_CODES.S)
        assertTrue(profile.usesRegionLocalRendering)
        assertEquals(AdvancedGlassBlurAlgorithm.Native, profile.algorithm)
    }

    @Test
    fun `region local profiles are preserved on every supported sdk`() {
        assertEquals(
            AdvancedGlassRenderProfile.Low,
            AdvancedBlurQuality.Low.renderProfileForSdk(Build.VERSION_CODES.S)
        )
        assertEquals(
            AdvancedGlassRenderProfile.Native,
            AdvancedBlurQuality.High.renderProfileForSdk(Build.VERSION_CODES.TIRAMISU)
        )
    }

    @Test
    fun `fallback profile downscales aggressively to bound offscreen cost`() {
        val fallback = AdvancedGlassRenderProfile.RuntimeShaderFallback
        assertEquals(1, fallback.downscaleFactorFor(4f))
        assertEquals(2, fallback.downscaleFactorFor(24f))
        assertEquals(4, fallback.downscaleFactorFor(64f))
    }
}