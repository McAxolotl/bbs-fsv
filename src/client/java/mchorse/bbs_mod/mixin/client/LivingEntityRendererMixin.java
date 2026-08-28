package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The legacy field-based MobForm pose overlay (onSetAngles / onRenderEnd) was removed —
 * bone editing now goes through {@link ModelPartMixin} (matrix-stack takeover), and the
 * per-part pose state lives in {@code MobRenderContext}, so this class carries only the
 * {@link LivingEntityRendererAccessor} contract. The mixins.json entry is kept for
 * stability; the accessor lets the bone discovery walk feature layers.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin implements LivingEntityRendererAccessor
{
}
