package net.haesleinhuepf.clij;

import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.macro.AbstractCLIJPlugin;
import net.haesleinhuepf.clij.macro.CLIJMacroPlugin;
import net.haesleinhuepf.clij.macro.CLIJOpenCLProcessor;
import net.haesleinhuepf.clij.macro.documentation.OffersDocumentation;
import org.scijava.plugin.Plugin;

import java.util.HashMap;

/**
 * The MandelbrotDrawer
 *
 * Author: @haesleinhuepf
 * July 2019
 */
@Plugin(type = CLIJMacroPlugin.class, name = "CLIJ_mandelbrotDrawer")
public class MandelbrotDrawer extends AbstractCLIJPlugin implements CLIJMacroPlugin, CLIJOpenCLProcessor, OffersDocumentation {

    @Override
    public boolean executeCL() {
        boolean result = mandelbrot(clij, (ClearCLBuffer)( args[0]), asFloat(args[1]), asFloat(args[2]), asFloat(args[3]), asInteger(args[4]));

        releaseBuffers(args);
        return result;
    }

    public static boolean mandelbrot(CLIJ clij, ClearCLBuffer dst, Float scale, Float offsetX, Float offsetY, Integer maxIterations) {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("dst", dst);
        parameters.put("scale", scale);
        parameters.put("offsetX", offsetX);
        parameters.put("offsetY", offsetY);
        parameters.put("maxIterations", maxIterations);

        return clij.execute(MandelbrotDrawer.class, "mandelbrot.cl", "mandelbrot", parameters);
    }

    @Override
    public String getParameterHelpText() {
        return "Image destination, Number scale, Number offsetX, Number offsetY, Number maxIterations";
    }

    @Override
    public String getDescription() {
        return "Draws a MandelbrotDrawer image";
    }

    @Override
    public String getAvailableForDimensions() {
        return "2D";
    }
}