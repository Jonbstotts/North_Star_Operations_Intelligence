package com.wtm.ui;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Loads raster images and applies JPEG EXIF Orientation metadata.
 *
 * Many phones store the physical JPEG pixels rotated and rely on EXIF tag
 * 0x0112 to tell viewers how to display the image. Swing/ImageIO does not
 * consistently honor that tag, so this helper normalizes the pixels before
 * the image reaches the UI.
 *
 * Supported EXIF orientation values:
 * 1 = normal
 * 2 = mirror horizontal
 * 3 = rotate 180
 * 4 = mirror vertical
 * 5 = transpose
 * 6 = rotate 90 CW
 * 7 = transverse
 * 8 = rotate 270 CW
 */
public final class OrientedImageLoader {
    private static final long MAX_FILE_BYTES=50L*1024L*1024L;
    private static final long MAX_PIXELS=40_000_000L;
    private static final int MAX_DIMENSION=12_000;

    private OrientedImageLoader(){}

    public static BufferedImage load(Path path) throws IOException {
        validateImageBeforeDecode(path);

        BufferedImage image=ImageIO.read(path.toFile());
        if(image==null)return null;

        String name=path.getFileName().toString().toLowerCase();
        if(!name.endsWith(".jpg") && !name.endsWith(".jpeg"))
            return image;

        int orientation=readExifOrientation(path);
        return applyOrientation(image,orientation);
    }



    /**
     * Reads only image metadata first so an accidentally enormous/corrupt local
     * announcement image cannot force an unreasonable allocation during decode.
     */
    private static void validateImageBeforeDecode(Path path) throws IOException {
        if(path==null||!Files.isRegularFile(path)||!Files.isReadable(path))
            throw new IOException("Image file is not readable.");

        long size=Files.size(path);
        if(size<=0||size>MAX_FILE_BYTES)
            throw new IOException("Image file size is outside the permitted range.");

        try(ImageInputStream input=ImageIO.createImageInputStream(path.toFile())){
            if(input==null)
                throw new IOException("Unable to inspect image.");

            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())
                throw new IOException("Unsupported image format.");

            ImageReader reader=readers.next();
            try{
                reader.setInput(input,true,true);
                int width=reader.getWidth(0);
                int height=reader.getHeight(0);

                long pixels=(long)width*(long)height;
                if(width<=0||height<=0
                        ||width>MAX_DIMENSION
                        ||height>MAX_DIMENSION
                        ||pixels>MAX_PIXELS){
                    throw new IOException(
                            "Image dimensions exceed the permitted display-media limit."
                    );
                }
            }finally{
                reader.dispose();
            }
        }
    }

    static int readExifOrientation(Path path){
        try(InputStream in=new BufferedInputStream(new FileInputStream(path.toFile()))){
            if(readU16BE(in)!=0xFFD8) return 1; // JPEG SOI

            while(true){
                int prefix=in.read();
                if(prefix<0) break;
                if(prefix!=0xFF) continue;

                int marker;
                do{ marker=in.read(); }while(marker==0xFF);
                if(marker<0) break;

                // Start of Scan / End of Image.
                if(marker==0xDA || marker==0xD9) break;

                int length=readU16BE(in);
                if(length<2) break;
                int payload=length-2;

                if(marker==0xE1){
                    byte[] data=in.readNBytes(payload);
                    if(data.length!=payload) break;

                    int orientation=parseExifBlock(data);
                    if(orientation>=1 && orientation<=8)
                        return orientation;
                }else{
                    long remaining=payload;
                    while(remaining>0){
                        long skipped=in.skip(remaining);
                        if(skipped<=0){
                            if(in.read()<0) return 1;
                            skipped=1;
                        }
                        remaining-=skipped;
                    }
                }
            }
        }catch(Exception ignored){}
        return 1;
    }

    private static int parseExifBlock(byte[] data){
        // "Exif\0\0"
        if(data.length<14
                || data[0]!='E'||data[1]!='x'||data[2]!='i'||data[3]!='f'
                || data[4]!=0||data[5]!=0)
            return 1;

        int tiff=6;
        ByteOrder order;

        if(data[tiff]=='I' && data[tiff+1]=='I')
            order=ByteOrder.LITTLE_ENDIAN;
        else if(data[tiff]=='M' && data[tiff+1]=='M')
            order=ByteOrder.BIG_ENDIAN;
        else
            return 1;

        ByteBuffer b=ByteBuffer.wrap(data).order(order);

        if((b.getShort(tiff+2)&0xFFFF)!=42)
            return 1;

        long ifdOffset=b.getInt(tiff+4)&0xFFFFFFFFL;
        long ifdPos=tiff+ifdOffset;
        if(ifdPos<0 || ifdPos+2>data.length)
            return 1;

        int entries=b.getShort((int)ifdPos)&0xFFFF;
        int pos=(int)ifdPos+2;

        for(int i=0;i<entries;i++){
            int entry=pos+i*12;
            if(entry+12>data.length) break;

            int tag=b.getShort(entry)&0xFFFF;
            if(tag!=0x0112) continue;

            int type=b.getShort(entry+2)&0xFFFF;
            long count=b.getInt(entry+4)&0xFFFFFFFFL;

            // Orientation is normally SHORT count=1.
            if(type==3 && count>=1)
                return b.getShort(entry+8)&0xFFFF;

            return 1;
        }

        return 1;
    }

    private static BufferedImage applyOrientation(BufferedImage src,int orientation){
        if(orientation<=1 || orientation>8)
            return src;

        int sw=src.getWidth();
        int sh=src.getHeight();

        boolean swap=orientation>=5 && orientation<=8;
        int dw=swap?sh:sw;
        int dh=swap?sw:sh;

        BufferedImage dst=new BufferedImage(
                dw,dh,
                src.getColorModel().hasAlpha()
                        ?BufferedImage.TYPE_INT_ARGB
                        :BufferedImage.TYPE_INT_RGB
        );

        AffineTransform tx=new AffineTransform();

        switch(orientation){
            case 2 -> { // mirror horizontal
                tx.translate(sw,0);
                tx.scale(-1,1);
            }
            case 3 -> { // rotate 180
                tx.translate(sw,sh);
                tx.rotate(Math.PI);
            }
            case 4 -> { // mirror vertical
                tx.translate(0,sh);
                tx.scale(1,-1);
            }
            case 5 -> { // transpose
                tx.rotate(Math.PI/2);
                tx.scale(1,-1);
            }
            case 6 -> { // rotate 90 CW
                tx.translate(sh,0);
                tx.rotate(Math.PI/2);
            }
            case 7 -> { // transverse
                tx.translate(sh,sw);
                tx.rotate(Math.PI/2);
                tx.scale(-1,1);
            }
            case 8 -> { // rotate 270 CW
                tx.translate(0,sw);
                tx.rotate(-Math.PI/2);
            }
            default -> {}
        }

        Graphics2D g=dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src,tx,null);
        g.dispose();

        return dst;
    }

    private static int readU16BE(InputStream in) throws IOException {
        int a=in.read(),b=in.read();
        if(a<0||b<0) return -1;
        return (a<<8)|b;
    }
}
