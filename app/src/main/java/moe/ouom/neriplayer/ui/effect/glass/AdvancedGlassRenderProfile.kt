package moe.ouom.neriplayer.ui.effect.glass

import moe.ouom.neriplayer.data.settings.AdvancedBlurQuality

internal enum class AdvancedGlassBlurAlgorithm {
    Native
}

internal enum class AdvancedGlassRenderPipeline {
    FullscreenMask,
    RegionLocal
}

internal data class AdvancedGlassRenderProfile(
    val algorithm: AdvancedGlassBlurAlgorithm,
    val pipeline: AdvancedGlassRenderPipeline = AdvancedGlassRenderPipeline.FullscreenMask,
    val maximumMergedInputAreaRatio: Float = 1f,
    val maximumDownscaleFactor: Int = 1
) {
    init {
        require(maximumMergedInputAreaRatio >= 1f)
        require(maximumDownscaleFactor in SupportedDownscaleFactors)
    }

    val usesRegionLocalRendering: Boolean
        get() = pipeline == AdvancedGlassRenderPipeline.RegionLocal

    fun downscaleFactorFor(radiusPx: Float): Int {
        if (!usesRegionLocalRendering || !radiusPx.isFinite()) return 1
        return when {
            maximumDownscaleFactor >= 4 && radiusPx >= UltraLowFourXThresholdPx -> 4
            maximumDownscaleFactor >= 2 && radiusPx >= LowTwoXThresholdPx -> 2
            else -> 1
        }
    }

    companion object {
        private val SupportedDownscaleFactors = setOf(1, 2, 4)
        private const val LowTwoXThresholdPx = 18f
        private const val UltraLowFourXThresholdPx = 48f

        val Native = AdvancedGlassRenderProfile(AdvancedGlassBlurAlgorithm.Native)
        val UltraLow = AdvancedGlassRenderProfile(
            algorithm = AdvancedGlassBlurAlgorithm.Native,
            pipeline = AdvancedGlassRenderPipeline.RegionLocal,
            maximumMergedInputAreaRatio = 1.20f,
            maximumDownscaleFactor = 4
        )
        val Low = AdvancedGlassRenderProfile(
            algorithm = AdvancedGlassBlurAlgorithm.Native,
            pipeline = AdvancedGlassRenderPipeline.RegionLocal,
            maximumMergedInputAreaRatio = 1.08f,
            maximumDownscaleFactor = 2
        )

        /**
         * API 31-32 无可用的 RuntimeShader 区域遮罩，回退到局部区域高斯模糊。
         * 使用与 UltraLow 一致的合并容忍度，允许跨区域合并输入以控制离屏节点数量。
         */
        val RuntimeShaderFallback = AdvancedGlassRenderProfile(
            algorithm = AdvancedGlassBlurAlgorithm.Native,
            pipeline = AdvancedGlassRenderPipeline.RegionLocal,
            maximumMergedInputAreaRatio = 1.20f,
            maximumDownscaleFactor = 4
        )
    }
}

internal fun AdvancedBlurQuality.renderProfile(): AdvancedGlassRenderProfile = when (this) {
    AdvancedBlurQuality.UltraLow -> AdvancedGlassRenderProfile.UltraLow
    AdvancedBlurQuality.Low -> AdvancedGlassRenderProfile.Low
    AdvancedBlurQuality.Default,
    AdvancedBlurQuality.High -> AdvancedGlassRenderProfile.Native
}

/**
 * 依据当前系统版本收敛渲染管线：
 * - Android 13+ 保持原配置（全屏区域遮罩或局部渲染）
 * - API 31-32 上全屏遮罩依赖 RuntimeShader，回退到局部高斯模糊管线
 */
internal fun AdvancedBlurQuality.renderProfileForSdk(sdkInt: Int): AdvancedGlassRenderProfile {
    val profile = renderProfile()
    if (supportsAdvancedGlassRuntimeShader(sdkInt) || profile.usesRegionLocalRendering) {
        return profile
    }
    return AdvancedGlassRenderProfile.RuntimeShaderFallback
}
