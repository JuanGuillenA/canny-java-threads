/** Version secuencial: un solo hilo ejecuta todas las etapas sobre toda la imagen. */
public final class CannySecuencial {

    private CannySecuencial() { }

    public static byte[] procesar(int[] rgb, int ancho, int alto) {
        int total = ancho * alto;
        float[] gris = new float[total];
        float[] suave = new float[total];
        float[] magnitud = new float[total];
        byte[] direccion = new byte[total];
        float[] delgado = new float[total];
        byte[] clase = new byte[total];

        Canny.escalaDeGrises(rgb, gris, ancho, 0, alto);
        Canny.suavizadoGaussiano(gris, suave, ancho, alto, 0, alto);
        Canny.gradienteSobel(suave, magnitud, direccion, ancho, alto, 0, alto);
        Canny.supresionNoMaximos(magnitud, direccion, delgado, ancho, alto, 0, alto);
        Canny.dobleUmbral(delgado, clase, ancho, 0, alto);
        return Canny.histeresis(clase, ancho, alto);
    }
}
