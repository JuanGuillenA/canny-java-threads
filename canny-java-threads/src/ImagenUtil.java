import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Lectura y escritura de imagenes (fuera de la medicion de tiempos). */
public final class ImagenUtil {

    private ImagenUtil() { }

    public static BufferedImage leer(String ruta) throws IOException {
        File archivo = new File(ruta);
        if (!archivo.exists()) {
            throw new IOException("No se encontro la imagen: " + archivo.getAbsolutePath());
        }
        BufferedImage img = ImageIO.read(archivo);
        if (img == null) {
            throw new IOException("El archivo no es una imagen valida: " + ruta);
        }
        return img;
    }

    /** Devuelve los pixeles de la imagen como enteros RGB, fila por fila. */
    public static int[] pixelesRGB(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    /** Guarda la imagen de bordes (0 = negro, 255 = blanco) como PNG en escala de grises. */
    public static void guardarBordes(byte[] bordes, int ancho, int alto, String ruta) throws IOException {
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_BYTE_GRAY);
        byte[] destino = ((DataBufferByte) salida.getRaster().getDataBuffer()).getData();
        System.arraycopy(bordes, 0, destino, 0, bordes.length);
        File archivo = new File(ruta);
        File carpeta = archivo.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta: " + carpeta.getAbsolutePath());
        }
        ImageIO.write(salida, "png", archivo);
    }
}
