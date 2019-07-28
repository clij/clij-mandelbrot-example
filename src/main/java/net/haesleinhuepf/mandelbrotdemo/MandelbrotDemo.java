package net.haesleinhuepf.mandelbrotdemo;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.MandelbrotDrawer;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class MandelbrotDemo {


    final static ImagePlus cpuImageMemory = NewImage.createShortImage("cpum", 400, 400, 1, NewImage.FILL_BLACK);
    final static ImagePlus gpuImageMemory = NewImage.createShortImage("gpum", 400, 400, 1, NewImage.FILL_BLACK);

    static long cpuCount = 0;
    static long clijCount = 0;

    static Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        new ImageJ();

        // config
        final float deltaScale = 1.001f;
        final float centerOffsetX = 1.402f;
        final int maxIterations = 255;
        final int[] dimensions = new int[] { 400, 400 };

        // allocate memory for imglib2
        final Img<UnsignedByteType> img = new ArrayImgFactory<>( new UnsignedByteType() ).create( dimensions );
        net.haesleinhuepf.imglib2.MandelbrotDrawer mandelbrotDrawer = new net.haesleinhuepf.imglib2.MandelbrotDrawer(new UnsignedByteType(), maxIterations);

        // allocate memory for CLIJ
        final CLIJ clij = CLIJ.getInstance();
        final ClearCLBuffer clMandelbrot = clij.create(new long[]{dimensions[0], dimensions[1]}, NativeTypeEnum.UnsignedByte);


        new Thread() {
            @Override
            public void run() {
                float scale = 0.005f;
                float offsetX = -1f;
                float offsetY = -1f;
                while(true) {
                    ImagePlus imglibMandelbrot = mandelbrotDrawer.drawMandelbrot(img, scale, offsetX - centerOffsetX, offsetY);

                    synchronized (lock) {
                        cpuImageMemory.setProcessor(imglibMandelbrot.getProcessor());
                    }
                    cpuCount++;

                    scale = scale / deltaScale;
                    offsetX = offsetX / deltaScale;
                    offsetY = offsetY / deltaScale;
                }
            }
        }.start();

        new Thread() {
            @Override
            public void run() {
                float scale = 0.005f;
                float offsetX = -1f;
                float offsetY = -1f;
                while(true) {
                    MandelbrotDrawer.mandelbrot(clij, clMandelbrot, scale, offsetX - centerOffsetX, offsetY, maxIterations);
                    ImagePlus clImpMandelbrot = clij.pull(clMandelbrot);

                    synchronized (lock) {
                        gpuImageMemory.setProcessor(clImpMandelbrot.getProcessor());
                    }
                    clijCount++;

                    scale = scale / deltaScale;
                    offsetX = offsetX / deltaScale;
                    offsetY = offsetY / deltaScale;
                }
            }
        }.start();

        // visualisation
        final ImagePlus cpuImageDisplay = NewImage.createByteImage("CPU", 400, 400, 1, NewImage.FILL_BLACK);
        cpuImageDisplay.show();
        final ImagePlus gpuImageDisplay = NewImage.createByteImage("GPU", 400, 400, 1, NewImage.FILL_BLACK);
        gpuImageDisplay.show();

        while(true) {
            cpuImageDisplay.setTitle("CPU: " + cpuCount);
            gpuImageDisplay.setTitle("GPU: " + clijCount);

            synchronized (lock) { // ensure that images are not overwritten while GUI refresh
                cpuImageDisplay.setProcessor(cpuImageMemory.getProcessor());
                IJ.run(cpuImageDisplay, "Fire", "");
                cpuImageDisplay.setDisplayRange(0, maxIterations);

                gpuImageDisplay.setProcessor(gpuImageMemory.getProcessor());
                IJ.run(gpuImageDisplay, "Fire", "");
                gpuImageDisplay.setDisplayRange(0, maxIterations);

            }
            Thread.sleep(50);
        }

    }
}
