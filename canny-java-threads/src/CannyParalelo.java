/**
 * Version paralela con la clase Thread.
 *
 * La imagen se divide en franjas horizontales, una por hilo. En cada etapa se
 * crean los hilos con start() y se esperan con join() antes de pasar a la
 * siguiente, porque cada etapa necesita el resultado completo de la anterior
 * (por ejemplo, el filtro gaussiano lee 2 filas de la franja vecina).
 */
public final class CannyParalelo {

    @FunctionalInterface
    private interface TareaFilas {
        void procesar(int y0, int y1);
    }

    private CannyParalelo() { }

    public static byte[] procesar(int[] rgb, int ancho, int alto, int hilos) throws InterruptedException {
        int total = ancho * alto;
        float[] gris = new float[total];
        float[] suave = new float[total];
        float[] magnitud = new float[total];
        byte[] direccion = new byte[total];
        float[] delgado = new float[total];
        byte[] clase = new byte[total];

        ejecutar(hilos, alto, (y0, y1) -> Canny.escalaDeGrises(rgb, gris, ancho, y0, y1));
        ejecutar(hilos, alto, (y0, y1) -> Canny.suavizadoGaussiano(gris, suave, ancho, alto, y0, y1));
        ejecutar(hilos, alto, (y0, y1) -> Canny.gradienteSobel(suave, magnitud, direccion, ancho, alto, y0, y1));
        ejecutar(hilos, alto, (y0, y1) -> Canny.supresionNoMaximos(magnitud, direccion, delgado, ancho, alto, y0, y1));
        ejecutar(hilos, alto, (y0, y1) -> Canny.dobleUmbral(delgado, clase, ancho, y0, y1));
        return Canny.histeresis(clase, ancho, alto);
    }

    /** Reparte las filas entre los hilos, los inicia y espera a que todos terminen. */
    private static void ejecutar(int hilos, int alto, TareaFilas tarea) throws InterruptedException {
        Thread[] trabajadores = new Thread[hilos];
        int base = alto / hilos;
        int resto = alto % hilos;
        int inicio = 0;

        for (int h = 0; h < hilos; h++) {
            int filas = base + (h < resto ? 1 : 0);
            final int y0 = inicio;
            final int y1 = inicio + filas;
            trabajadores[h] = new Thread(() -> tarea.procesar(y0, y1), "hilo-" + h);
            trabajadores[h].start();
            inicio = y1;
        }
        for (Thread t : trabajadores) {
            t.join();
        }
    }
}
