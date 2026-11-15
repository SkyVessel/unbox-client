package dev.unbox.unpvp;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bulk raster reads reuse the colour-model scratch buffer across each row. */
final class TexturePixels {
    static ByteBuffer rgba(BufferedImage image,boolean flip,boolean whiteTransparent){
        int w=image.getWidth(),h=image.getHeight();int[] row=new int[w];
        ByteBuffer bytes=ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.BIG_ENDIAN);
        for(int y=0;y<h;y++){
            image.getRGB(0,flip?h-1-y:y,w,1,row,0,w);
            for(int c:row){if(whiteTransparent&&(c>>>24)==0)c=0x00ffffff;bytes.putInt((c<<8)|(c>>>24));}
        }
        bytes.flip();return bytes;
    }
}
