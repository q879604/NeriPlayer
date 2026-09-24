package moe.ouom.neriplayer.ui.effect.glass

import android.graphics.BlendMode
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

// 基础高斯模糊依赖 RenderEffect.createBlurEffect / RenderNode.setRenderEffect，Android 12 (API 31) 起可用
internal const val ADVANCED_GLASS_BACKEND_MIN_SDK = Build.VERSION_CODES.S
// 区域遮罩需要 RuntimeShader，仅 Android 13 (API 33)+ 可用；低版本回退到局部高斯模糊
internal const val ADVANCED_GLASS_RUNTIME_SHADER_MIN_SDK = Build.VERSION_CODES.TIRAMISU
internal const val ADVANCED_GLASS_MAX_REGIONS = 32

internal data class AdvancedGlassRenderRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadiiPx: AdvancedGlassCornerRadii
)

/**
 * 基础高斯模糊（[AndroidRenderEffect.createBlurEffect] + 离屏 RenderNode）自 Android 12 起可用。
 * 这是"高级玻璃"的最低支持版本。
 */
internal fun isAdvancedGlassBackendSupported(sdkInt: Int): Boolean =
    sdkInt >= ADVANCED_GLASS_BACKEND_MIN_SDK

/**
 * 全屏区域遮罩渲染依赖 [android.graphics.RuntimeShader]，仅 Android 13+ 可用。
 * 不满足时调用方应回退到 [AdvancedGlassRenderPipeline.RegionLocal] 局部高斯模糊。
 */
internal fun supportsAdvancedGlassRuntimeShader(sdkInt: Int): Boolean =
    sdkInt >= ADVANCED_GLASS_RUNTIME_SHADER_MIN_SDK

internal fun createAdvancedGlassRenderEffect(
    shaderSource: AdvancedGlassShaderSource,
    sdkInt: Int,
    radiusPx: Float,
    renderProfile: AdvancedGlassRenderProfile = AdvancedGlassRenderProfile.Native,
    regions: List<AdvancedGlassRenderRegion>
): RenderEffect? {
    if (Build.VERSION.SDK_INT < ADVANCED_GLASS_RUNTIME_SHADER_MIN_SDK ||
        !supportsAdvancedGlassRuntimeShader(sdkInt) ||
        !radiusPx.isFinite() ||
        radiusPx <= 0f ||
        regions.isEmpty()
    ) {
        return null
    }
    require(regions.size <= ADVANCED_GLASS_MAX_REGIONS) {
        "Advanced glass supports at most $ADVANCED_GLASS_MAX_REGIONS visible regions"
    }
    require(!renderProfile.usesRegionLocalRendering) {
        "Region-local profiles must render through AdvancedGlassLocalBlurRenderer"
    }
    return createAdvancedGlassRenderEffectSession(shaderSource, sdkInt)
        .update(radiusPx, regions)
}

internal interface AdvancedGlassRenderEffectSession {
    fun update(
        radiusPx: Float,
        regions: List<AdvancedGlassRenderRegion>
    ): RenderEffect?
}

internal fun createAdvancedGlassRenderEffectSession(
    shaderSource: AdvancedGlassShaderSource,
    sdkInt: Int
): AdvancedGlassRenderEffectSession {
    // RuntimeShader 区域遮罩仅在 Android 13+ 可用；API 31-32 由调用方改用局部高斯模糊渲染
    if (Build.VERSION.SDK_INT < ADVANCED_GLASS_RUNTIME_SHADER_MIN_SDK ||
        !supportsAdvancedGlassRuntimeShader(sdkInt)
    ) {
        return UnsupportedAdvancedGlassRenderEffectSession
    }
    return AdvancedGlassRuntimeShaderSession(shaderSource)
}

private object UnsupportedAdvancedGlassRenderEffectSession : AdvancedGlassRenderEffectSession {
    override fun update(
        radiusPx: Float,
        regions: List<AdvancedGlassRenderRegion>
    ): RenderEffect? = null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AdvancedGlassRuntimeShaderSession(
    private val shaderSource: AdvancedGlassShaderSource
) : AdvancedGlassRenderEffectSession {
    private var backend: AdvancedGlassRuntimeShaderBackend.Session? = null

    override fun update(
        radiusPx: Float,
        regions: List<AdvancedGlassRenderRegion>
    ): RenderEffect? {
        if (!radiusPx.isFinite() || radiusPx <= 0f || regions.isEmpty()) {
            return null
        }
        require(regions.size <= ADVANCED_GLASS_MAX_REGIONS) {
            "Advanced glass supports at most $ADVANCED_GLASS_MAX_REGIONS visible regions"
        }
        val activeBackend = backend ?: AdvancedGlassRuntimeShaderBackend.Session(
            maskShaderSource = shaderSource.load()
        ).also { backend = it }
        return activeBackend.update(radiusPx, regions)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AdvancedGlassRuntimeShaderBackend {
    class Session(
        private val maskShaderSource: String
    ) {
        private var cachedBlurRadiusPx = Float.NaN
        private var cachedBlurEffect: AndroidRenderEffect? = null

        fun update(
            radiusPx: Float,
            regions: List<AdvancedGlassRenderRegion>
        ): RenderEffect {
            val regionUniforms = regionUniforms(regions)
            return resolveNativeEffect(
                regionUniforms = regionUniforms,
                radiusPx = radiusPx
            )
        }

        private fun regionUniforms(
            regions: List<AdvancedGlassRenderRegion>
        ): AdvancedGlassRegionUniforms {
            val regionBounds = FloatArray(ADVANCED_GLASS_MAX_REGIONS * RegionComponentCount)
            val cornerRadii = FloatArray(ADVANCED_GLASS_MAX_REGIONS * RegionComponentCount)
            regions.forEachIndexed { index, region ->
                val offset = index * RegionComponentCount
                regionBounds[offset] = region.left
                regionBounds[offset + 1] = region.top
                regionBounds[offset + 2] = region.right
                regionBounds[offset + 3] = region.bottom
                cornerRadii[offset] = region.cornerRadiiPx.topLeft.coerceAtLeast(0f)
                cornerRadii[offset + 1] = region.cornerRadiiPx.topRight.coerceAtLeast(0f)
                cornerRadii[offset + 2] = region.cornerRadiiPx.bottomRight.coerceAtLeast(0f)
                cornerRadii[offset + 3] = region.cornerRadiiPx.bottomLeft.coerceAtLeast(0f)
            }
            return AdvancedGlassRegionUniforms(
                count = regions.size,
                bounds = regionBounds,
                cornerRadii = cornerRadii
            )
        }

        private fun resolveNativeEffect(
            regionUniforms: AdvancedGlassRegionUniforms,
            radiusPx: Float
        ): RenderEffect {
            val blurEffect = cachedBlurEffect?.takeIf { cachedBlurRadiusPx == radiusPx }
                ?: AndroidRenderEffect.createBlurEffect(
                    radiusPx,
                    radiusPx,
                    Shader.TileMode.CLAMP
                ).also { effect ->
                    cachedBlurRadiusPx = radiusPx
                    cachedBlurEffect = effect
                }
            return applyRegionMask(blurEffect, regionUniforms)
        }

        private fun applyRegionMask(
            blurEffect: AndroidRenderEffect,
            regionUniforms: AdvancedGlassRegionUniforms
        ): RenderEffect {
            // some GPU backends capture uniforms on the first effect bind, so each
            // mask shader begins with the current region data rather than an empty mask
            val maskEffect = AndroidRenderEffect.createRuntimeShaderEffect(
                createMaskShader(regionUniforms, invertMask = false),
                ChildShaderUniform
            )
            val outsideOriginalEffect = AndroidRenderEffect.createRuntimeShaderEffect(
                createMaskShader(regionUniforms, invertMask = true),
                ChildShaderUniform
            )
            val maskedBlurEffect = AndroidRenderEffect.createChainEffect(maskEffect, blurEffect)
            return AndroidRenderEffect.createBlendModeEffect(
                outsideOriginalEffect,
                maskedBlurEffect,
                BlendMode.SRC_OVER
            ).asComposeRenderEffect()
        }

        private fun createMaskShader(
            regionUniforms: AdvancedGlassRegionUniforms,
            invertMask: Boolean
        ) = RuntimeShader(maskShaderSource).apply {
            setFloatUniform(RegionCountUniform, regionUniforms.count.toFloat())
            setFloatUniform(RegionBoundsUniform, regionUniforms.bounds)
            setFloatUniform(CornerRadiiUniform, regionUniforms.cornerRadii)
            setFloatUniform(InvertMaskUniform, if (invertMask) 1f else 0f)
        }

        private data class AdvancedGlassRegionUniforms(
            val count: Int,
            val bounds: FloatArray,
            val cornerRadii: FloatArray
        )
    }

    private const val RegionComponentCount = 4
    private const val ChildShaderUniform = "child"
    private const val RegionCountUniform = "regionCount"
    private const val RegionBoundsUniform = "regionBounds"
    private const val CornerRadiiUniform = "cornerRadii"
    private const val InvertMaskUniform = "invertMask"
}
