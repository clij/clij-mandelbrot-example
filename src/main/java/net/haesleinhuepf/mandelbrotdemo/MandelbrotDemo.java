package net.haesleinhuepf.mandelbrotdemo;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.MandelbrotDrawer;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.imglib2.ParallelMandelbrotDrawer;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class MandelbrotDemo {

    final static int width = 600;
    final static int height = 400;

    final static ImagePlus cpuSingleThreadedImageMemory = NewImage.createShortImage("cpum", width, height, 1, NewImage.FILL_BLACK);
    final static ImagePlus cpuMultiThreadedImageMemory = NewImage.createShortImage("cpumm", width, height, 1, NewImage.FILL_BLACK);
    final static ImagePlus gpuImageMemory = NewImage.createShortImage("gpum", width, height, 1, NewImage.FILL_BLACK);

    static long cpuCount = 0;
    static long cpuMultiThreadedCount = 0;
    static long clijCount = 0;

    static Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        new ImageJ();

        // config
        final float deltaScale = 1.001f;
        final float centerOffsetX = 1.402f;
        final int maxIterations = 255;
        final int[] dimensions = new int[] { width, height };

        int numberOfMultThreadingCores = Runtime.getRuntime().availableProcessors() - 1;

        // allocate memory for single threaded CPU version
        final Img<UnsignedByteType> img = new ArrayImgFactory<>( new UnsignedByteType() ).create( dimensions );
        net.haesleinhuepf.imglib2.MandelbrotDrawer mandelbrotDrawer = new net.haesleinhuepf.imglib2.MandelbrotDrawer(new UnsignedByteType(), maxIterations);

        // allocate memory for single threaded CPU version
        final Img<UnsignedByteType> img2 = new ArrayImgFactory<>( new UnsignedByteType() ).create( dimensions );
        ParallelMandelbrotDrawer[] parallelMandelbrotDrawers = new ParallelMandelbrotDrawer[numberOfMultThreadingCores];
        for (int i = 0; i < parallelMandelbrotDrawers.length; i++) {
            parallelMandelbrotDrawers[i] = new ParallelMandelbrotDrawer(new UnsignedByteType(), maxIterations, i, parallelMandelbrotDrawers.length);
        }

        // allocate memory for GPU version using CLIJ
        final CLIJ clij = CLIJ.getInstance();
        final ClearCLBuffer clMandelbrot = clij.create(new long[]{dimensions[0], dimensions[1]}, NativeTypeEnum.UnsignedByte);

        // single thread
        new Thread() {
            @Override
            public void run() {
                float scale = 0.005f;
                float offsetX = -1f;
                float offsetY = -1f;
                while(true) {
                    ImagePlus imglibMandelbrot = mandelbrotDrawer.drawMandelbrot(img, scale, offsetX - centerOffsetX, offsetY);

                    synchronized (lock) {
                        cpuSingleThreadedImageMemory.setProcessor(imglibMandelbrot.getProcessor());
                    }
                    cpuCount++;

                    scale = scale / deltaScale;
                    offsetX = offsetX / deltaScale;
                    offsetY = offsetY / deltaScale;
                }
            }
        }.start();

        // multi thread
        new Thread() {
            @Override
            public void run() {
                float[] scale = {0.005f};
                float[] offsetX = {-1f};
                float[] offsetY = {-1f};
                while(true) {
                    Thread[] threads = new Thread[parallelMandelbrotDrawers.length];
                    for (int i = 0; i < parallelMandelbrotDrawers.length; i++) {
                        final int ii = i;
                        threads[i] = new Thread() {
                            @Override
                            public void run() {
                                parallelMandelbrotDrawers[ii].drawMandelbrot(img2, scale[0], offsetX[0] - centerOffsetX, offsetY[0]);
                            }
                        };
                        threads[i].start();
                    }
                    for (int i = 0; i < parallelMandelbrotDrawers.length; i++) {
                        try {
                            threads[i].join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    ImagePlus imglibMandelbrot = ImageJFunctions.wrap(img, "img");

                    synchronized (lock) {
                        cpuMultiThreadedImageMemory.setProcessor(imglibMandelbrot.getProcessor());
                    }
                    cpuMultiThreadedCount++;

                    scale[0] = scale[0] / deltaScale;
                    offsetX[0] = offsetX[0] / deltaScale;
                    offsetY[0] = offsetY[0] / deltaScale;
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
        final ImagePlus cpuSingleThreadedImageDisplay = NewImage.createByteImage("CPU single threaded", width, height, 1, NewImage.FILL_BLACK);
        cpuSingleThreadedImageDisplay.show();
        final ImagePlus cpuMultiThreadedImageDisplay = NewImage.createByteImage("CPU multi threaded", width, height, 1, NewImage.FILL_BLACK);
        cpuMultiThreadedImageDisplay.show();
        final ImagePlus gpuImageDisplay = NewImage.createByteImage("GPU", width, height, 1, NewImage.FILL_BLACK);
        gpuImageDisplay.show();

        while(true) {
            cpuSingleThreadedImageDisplay.setTitle("CPU (1 core): " + cpuCount);
            cpuMultiThreadedImageDisplay.setTitle("CPU (" + numberOfMultThreadingCores + " cores): " + cpuMultiThreadedCount);
            gpuImageDisplay.setTitle("GPU (" + clij.getClearCLContext().getDevice().getNumberOfComputeUnits() + " cores): " + clijCount);

            synchronized (lock) { // ensure that images are not overwritten while GUI refresh
                cpuSingleThreadedImageDisplay.setProcessor(cpuSingleThreadedImageMemory.getProcessor());
                IJ.run(cpuSingleThreadedImageDisplay, "Fire", "");
                cpuSingleThreadedImageDisplay.setDisplayRange(0, maxIterations);

                cpuMultiThreadedImageDisplay.setProcessor(cpuMultiThreadedImageMemory.getProcessor());
                IJ.run(cpuMultiThreadedImageDisplay, "Fire", "");
                cpuMultiThreadedImageDisplay.setDisplayRange(0, maxIterations);

                gpuImageDisplay.setProcessor(gpuImageMemory.getProcessor());
                IJ.run(gpuImageDisplay, "Fire", "");
                gpuImageDisplay.setDisplayRange(0, maxIterations);

            }
            Thread.sleep(50);
        }

    }
}
