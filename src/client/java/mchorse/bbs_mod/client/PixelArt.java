package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.gl.ShaderProgram;

/**
 * Switchboard for the pixel art seam smoothing.
 *
 * BBS's ui_scale is a float, and at a fractional value nearest sampling
 * duplicates some rows of texels twice and some once, which is what makes text
 * and icons look ragged. The shaders behind this (see the comment in
 * assets/bbs/shaders/include/bbs_pixelart.glsl) spread the seam between texels
 * over one screen pixel and collapse back into plain nearest sampling at an
 * integer scale.
 */
public class PixelArt
{
    private static boolean drawingUI;

    public static boolean isEnabled()
    {
        return BBSSettings.pixelArtSmoothing.get();
    }

    /**
     * Vanilla's text programs are shared with the text drawn in the world, so
     * they may only be swapped while BBS's own UI is the thing on screen.
     */
    public static void setDrawingUI(boolean drawing)
    {
        drawingUI = drawing;
    }

    /**
     * Program to draw text with, or null to leave vanilla's in charge (also
     * when the shader failed to compile).
     */
    public static ShaderProgram getTextProgram(boolean intensity)
    {
        if (!drawingUI || !isEnabled())
        {
            return null;
        }

        return intensity ? BBSShaders.getPixelArtTextIntensityProgram() : BBSShaders.getPixelArtTextProgram();
    }
}
