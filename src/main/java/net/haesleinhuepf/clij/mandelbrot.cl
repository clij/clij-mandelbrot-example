__constant sampler_t sampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;

// Adapted from https://github.com/imglib/imglib2-tutorials/blob/master/src/main/java/net/imglib2/tutorial/t02/MandelbrotRealRandomAccess.java
//
// Author: Robert Haase, rhaase@mpi-cbg.de
// July 2019
//
inline int mandelbrot_at_position ( float re0, float im0, int maxIterations )
{
    float re = re0;
    float im = im0;
    int i = 0;
    for ( ; i < maxIterations; ++i )
    {
        float squre = re * re;
        float squim = im * im;
        if ( squre + squim > 4 )
            break;
        im = 2 * re * im + im0;
        re = squre - squim + re0;
    }
    return i;
}

__kernel void mandelbrot(DTYPE_IMAGE_OUT_2D  dst, float scale, float offsetX, float offsetY, int maxIterations)
{
  const int x = get_global_id(0);
  const int y = get_global_id(1);
  const int2 pos = (int2){x,y};

  const DTYPE_OUT value = mandelbrot_at_position(scale * x + offsetX, scale * y + offsetY, maxIterations);

  WRITE_IMAGE_2D (dst, pos, value);
}

