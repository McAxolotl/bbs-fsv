package mchorse.bbs_mod.particles.vanilla;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.mixin.client.CameraInvoker;
import mchorse.bbs_mod.mixin.client.ParticleManagerInvoker;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A pocket of vanilla particles that lives inside a UI viewport.
 *
 * <p>Vanilla's own {@link net.minecraft.client.particle.ParticleManager} cannot
 * be used for this: everything handed to it is drawn by the world pass, so a
 * preview would spray particles into the game behind the interface instead of
 * into the panel. What this class borrows is only the factory &mdash; the
 * particle is created through {@link ParticleManagerInvoker}, then owned,
 * ticked and drawn here.
 *
 * <p>A particle still reads the world for collisions and physics, so the scene
 * is anchored above the build limit ({@link #ORIGIN_HEIGHT}), where the chunk is
 * loaded but no block can get in the way. Light is forced to full by
 * {@code ParticleMixin} while {@link #render} runs, matching the rest of the
 * preview.
 */
public class VanillaParticleScene
{
    /**
     * Vanilla's own draw order (see {@code ParticleManager.PARTICLE_TEXTURE_SHEETS}).
     * {@link ParticleTextureSheet#NO_RENDER} is absent for the obvious reason, and
     * {@link ParticleTextureSheet#CUSTOM} because those particles draw themselves
     * through world machinery that a UI viewport does not have.
     */
    private static final ParticleTextureSheet[] SHEETS = {
        ParticleTextureSheet.TERRAIN_SHEET,
        ParticleTextureSheet.PARTICLE_SHEET_OPAQUE,
        ParticleTextureSheet.PARTICLE_SHEET_LIT,
        ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
    };

    /** How far above the world's ceiling the scene is parked. */
    private static final int ORIGIN_HEIGHT = 32;

    /** A runaway emitter (count 100 at frequency 1) must not eat the client. */
    private static final int MAX_PARTICLES = 4096;

    private static boolean rendering;

    private final List<Particle> particles = new ArrayList<>();
    private final Camera camera = new Camera();
    private final Vector3d origin = new Vector3d();

    /**
     * Whether a preview scene is drawing right now &mdash; the one thing the
     * particles themselves need to know, since their light comes from a world
     * they are only nominally in.
     */
    public static boolean isRendering()
    {
        return rendering;
    }

    public void clear()
    {
        this.particles.clear();
    }

    /**
     * Spawn a particle at a point given in the preview's own space.
     */
    public void spawn(ParticleEffect effect, double x, double y, double z, double velocityX, double velocityY, double velocityZ)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null || this.particles.size() >= MAX_PARTICLES)
        {
            return;
        }

        /* Re-anchoring while particles are alive would leave them behind the
         * camera, so the origin only follows the player between bursts */
        if (this.particles.isEmpty())
        {
            this.updateOrigin(mc, world);
        }

        Particle particle = ((ParticleManagerInvoker) mc.particleManager).bbs$createParticle(effect,
            this.origin.x + x, this.origin.y + y, this.origin.z + z,
            velocityX, velocityY, velocityZ
        );

        if (particle != null)
        {
            this.particles.add(particle);
        }
    }

    private void updateOrigin(MinecraftClient mc, ClientWorld world)
    {
        Entity anchor = mc.getCameraEntity();
        double x = anchor == null ? 0D : anchor.getX();
        double z = anchor == null ? 0D : anchor.getZ();

        /* Above the ceiling there are no blocks to collide with, while the
         * chunk underneath is still loaded, so the particles tick normally */
        this.origin.set(x, world.getTopY() + ORIGIN_HEIGHT, z);
    }

    public void tick()
    {
        Iterator<Particle> it = this.particles.iterator();

        while (it.hasNext())
        {
            Particle particle = it.next();

            try
            {
                particle.tick();
            }
            catch (Exception e)
            {
                /* A particle that throws is dropped rather than taken to the
                 * whole editor. The effect is visible (it disappears), so this
                 * is not a silent failure */
                particle.markDead();
            }

            if (!particle.isAlive())
            {
                it.remove();
            }
        }
    }

    /**
     * Draw the scene through the preview's camera.
     *
     * <p>Particles build their geometry camera-relative and take no matrix of
     * their own &mdash; the shader reads the global model-view, so the view
     * rotation goes there for the duration of the pass (see
     * {@link #applyModelView}), exactly as the world renderer does around
     * vanilla's own particle pass. The camera translation is not part of it: the
     * stand-in camera's position is already subtracted out by the geometry.
     */
    public void render(mchorse.bbs_mod.camera.Camera previewCamera, float transition)
    {
        if (this.particles.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        CameraInvoker standIn = (CameraInvoker) this.camera;

        standIn.bbs$setPos(
            this.origin.x + previewCamera.position.x,
            this.origin.y + previewCamera.position.y,
            this.origin.z + previewCamera.position.z
        );
        standIn.bbs$setRotation(MathUtils.toDeg(previewCamera.rotation.y), -MathUtils.toDeg(previewCamera.rotation.x));

        Matrix4f previousModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        applyModelView(previewCamera.view);

        LightmapTextureManager lightmap = mc.gameRenderer.getLightmapTextureManager();

        lightmap.enable();

        /* Guard, not a fix for anything observed: the particle shader discards
         * below 0.1 alpha AFTER folding in ColorModulator, so a faded modulator
         * left behind by the interface would erase every particle without a
         * trace. Start neutral, hand back whatever was there */
        float[] color = RenderSystem.getShaderColor();
        float previousR = color[0];
        float previousG = color[1];
        float previousB = color[2];
        float previousA = color[3];

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        rendering = true;

        /* The global model-view must come back even if a particle throws, or
         * every later draw this frame inherits the preview's camera */
        try
        {
            this.renderSheets(transition);
        }
        finally
        {
            rendering = false;

            RenderSystem.setShaderColor(previousR, previousG, previousB, previousA);

            lightmap.disable();

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();

            applyModelView(previousModelView);
        }
    }

    /**
     * Put a matrix where the shaders actually read it.
     *
     * <p>Pushing it onto {@link RenderSystem#getModelViewStack()} is not enough:
     * shaders read the APPLIED matrix, and the stack's top is not it. The
     * preview runs with an identity applied over an interface matrix left on the
     * stack (see {@link MatrixStackUtils#cacheMatrices()}), so a plain pop and
     * re-apply would hand everything drawn afterwards &mdash; the gizmo first of
     * all &mdash; that interface matrix and sweep it off screen. Hence the
     * push/load/apply/pop the cache itself uses: change what is applied, leave
     * the stack as it was.
     */
    private static void applyModelView(Matrix4f matrix)
    {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();

        modelView.pushMatrix();
        modelView.identity();
        modelView.mul(matrix);
        RenderSystem.applyModelViewMatrix();
        modelView.popMatrix();
    }

    private void renderSheets(float transition)
    {
        Tessellator tessellator = Tessellator.getInstance();

        /* A billboard is a flat quad spun to face the camera, so which way its
         * vertices wind depends on where that camera is. Vanilla gets away with
         * back-face culling because particles always face ITS camera; ours is a
         * stand-in that the user orbits freely, and a quad turned the wrong way
         * is dropped by the GPU while looking perfectly correct in the buffer.
         *
         * Restored rather than switched back on: the gizmo's rings and bars are
         * flat too, and they inherit whatever culling state the pass before them
         * left behind. Forcing it on here made them vanish. */
        boolean culling = GlStateManager.CULL.capState.state;

        RenderSystem.disableCull();

        for (ParticleTextureSheet sheet : SHEETS)
        {
            if (!this.hasSheet(sheet))
            {
                continue;
            }

            /* Deliberately not sheet.begin()/draw(): those configure the pass
             * for the world renderer. Setting it up here keeps the preview
             * honest about what it needs, and survives 1.21.11, where vanilla
             * drops the sheets for a different particle renderer entirely. */
            RenderSystem.setShader(GameRenderer::getParticleProgram);
            RenderSystem.setShaderTexture(0, sheet == ParticleTextureSheet.TERRAIN_SHEET
                ? SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE
                : SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);

            BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);

            for (Particle particle : this.particles)
            {
                if (particle.getType() != sheet)
                {
                    continue;
                }

                try
                {
                    particle.buildGeometry(builder, this.camera, transition);
                }
                catch (Exception e)
                {
                    particle.markDead();
                }
            }

            /* endNullable rather than end: a sheet gets here because it has
             * particles, but every one of them may have died in buildGeometry
             * above, and 1.21.1 throws on ending an empty buffer */
            BuiltBuffer built = builder.endNullable();

            if (built != null)
            {
                BufferRenderer.drawWithGlobalProgram(built);
            }
        }

        if (culling)
        {
            RenderSystem.enableCull();
        }
    }

    private boolean hasSheet(ParticleTextureSheet sheet)
    {
        for (Particle particle : this.particles)
        {
            if (particle.getType() == sheet)
            {
                return true;
            }
        }

        return false;
    }
}
