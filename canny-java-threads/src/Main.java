import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Compara el filtro de Canny secuencial contra la version paralela con 2, 4 y
 * todos los hilos logicos del equipo.
 *
 * Uso:  java -cp bin Main [ruta_imagen]
 * Por defecto usa imagenes/porsche.jpg
 */
public final class Main {

    private static final int CALENTAMIENTO = 3;   // ejecuciones que no se miden (JIT de Java)
    private static final int REPETICIONES = 5;    // ejecuciones que si se miden

    private Main() { }

    public static void main(String[] args) throws Exception {
        String ruta = args.length > 0 ? args[0] : "imagenes/porsche.jpg";

        // ---- Lectura (no se mide) ----
        BufferedImage img = ImagenUtil.leer(ruta);
        int ancho = img.getWidth();
        int alto = img.getHeight();
        int[] rgb = ImagenUtil.pixelesRGB(img);
        int nucleosLogicos = Runtime.getRuntime().availableProcessors();

        System.out.println("=== Filtro de Canny: secuencial vs. paralelo (Java Threads) ===");
        System.out.printf(Locale.US, "Imagen: %s (%d x %d = %,d pixeles)%n", ruta, ancho, alto, (long) ancho * alto);
        System.out.println("Procesadores logicos detectados: " + nucleosLogicos);
        System.out.println("Java: " + System.getProperty("java.version")
                + " | SO: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.printf("Calentamiento: %d ejecuciones | Medicion: %d repeticiones por caso%n%n",
                CALENTAMIENTO, REPETICIONES);

        // Configuraciones de hilos: 2, 4 y todos los logicos del equipo
        TreeSet<Integer> conjunto = new TreeSet<>(Arrays.asList(2, 4, nucleosLogicos));
        conjunto.removeIf(h -> h < 2);
        List<Integer> configuraciones = new ArrayList<>(conjunto);

        // ---- Calentamiento (no se mide) ----
        System.out.print("Calentando la JVM");
        for (int i = 0; i < CALENTAMIENTO; i++) {
            CannySecuencial.procesar(rgb, ancho, alto);
            for (int h : configuraciones) {
                CannyParalelo.procesar(rgb, ancho, alto, h);
            }
            System.out.print(".");
        }
        System.out.println(" listo\n");

        // ---- Medicion secuencial ----
        double[] tiemposSec = new double[REPETICIONES];
        byte[] resultadoSec = null;
        for (int r = 0; r < REPETICIONES; r++) {
            long t0 = System.nanoTime();
            resultadoSec = CannySecuencial.procesar(rgb, ancho, alto);
            tiemposSec[r] = (System.nanoTime() - t0) / 1e6;
        }
        double promedioSec = promedio(tiemposSec);

        // ---- Medicion paralela ----
        List<Fila> filas = new ArrayList<>();
        filas.add(new Fila("Secuencial", 1, tiemposSec, promedioSec, true));
        byte[] resultadoPar = null;
        int hilosMax = configuraciones.get(configuraciones.size() - 1);
        boolean todosIguales = true;

        for (int h : configuraciones) {
            double[] tiempos = new double[REPETICIONES];
            byte[] resultado = null;
            for (int r = 0; r < REPETICIONES; r++) {
                long t0 = System.nanoTime();
                resultado = CannyParalelo.procesar(rgb, ancho, alto, h);
                tiempos[r] = (System.nanoTime() - t0) / 1e6;
            }
            boolean igual = Arrays.equals(resultadoSec, resultado);
            todosIguales &= igual;
            filas.add(new Fila("Paralela", h, tiempos, promedioSec, igual));
            if (h == hilosMax) {
                resultadoPar = resultado;
            }
        }

        // ---- Tiempo de la parte secuencial (histeresis) ----
        double promedioHisteresis = medirHisteresis(rgb, ancho, alto);
        double fraccion = promedioHisteresis / promedioSec;

        // ---- Resultados en consola ----
        System.out.println("Hilos | Version    | Promedio (ms) | Aceleracion | Eficiencia | Igual al secuencial | Repeticiones (ms)");
        System.out.println("------+------------+---------------+-------------+------------+---------------------+------------------");
        Fila mejor = filas.get(0);
        for (Fila f : filas) {
            System.out.printf(Locale.US, "%5d | %-10s | %13.2f | %11.2f | %10.2f | %-19s | %s%n",
                    f.hilos, f.version, f.promedio, f.aceleracion, f.eficiencia,
                    f.igual ? "si" : "NO", f.repeticionesTexto());
            if (f.promedio < mejor.promedio) {
                mejor = f;
            }
        }
        System.out.println();
        System.out.printf(Locale.US, "Histeresis (parte secuencial): %.2f ms = %.1f %% del tiempo secuencial%n",
                promedioHisteresis, fraccion * 100);
        for (int h : configuraciones) {
            double amdahl = 1.0 / (fraccion + (1 - fraccion) / h);
            System.out.printf(Locale.US, "  Aceleracion maxima teorica con %d hilos (Ley de Amdahl): %.2f%n", h, amdahl);
        }
        System.out.printf(Locale.US, "Mejor configuracion: %d hilo(s) con %.2f ms (aceleracion %.2f)%n",
                mejor.hilos, mejor.promedio, mejor.aceleracion);
        System.out.println(todosIguales
                ? "Verificacion: todas las versiones paralelas producen exactamente la misma imagen que la secuencial."
                : "ATENCION: alguna version paralela produjo una imagen distinta a la secuencial.");

        // ---- Escritura de archivos (no se mide) ----
        ImagenUtil.guardarBordes(resultadoSec, ancho, alto, "resultados/bordes_secuencial.png");
        ImagenUtil.guardarBordes(resultadoPar, ancho, alto, "resultados/bordes_paralelo_" + hilosMax + "_hilos.png");
        guardarCSV(filas, "resultados/tiempos.csv");
        guardarTablaWiki(filas, mejor, promedioHisteresis, fraccion, "resultados/tabla_tiempos_wiki.html");

        System.out.println();
        System.out.println("Archivos generados en la carpeta resultados/:");
        System.out.println("  bordes_secuencial.png");
        System.out.println("  bordes_paralelo_" + hilosMax + "_hilos.png");
        System.out.println("  tiempos.csv");
    }

    /** Mide solo la etapa de histeresis, que es la parte que no se paraleliza. */
    private static double medirHisteresis(int[] rgb, int ancho, int alto) {
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

        double[] tiempos = new double[REPETICIONES];
        for (int r = 0; r < REPETICIONES; r++) {
            long t0 = System.nanoTime();
            Canny.histeresis(clase, ancho, alto);
            tiempos[r] = (System.nanoTime() - t0) / 1e6;
        }
        return promedio(tiempos);
    }

    private static double promedio(double[] valores) {
        double suma = 0;
        for (double v : valores) {
            suma += v;
        }
        return suma / valores.length;
    }

    private static void guardarCSV(List<Fila> filas, String ruta) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(ruta))) {
            out.println("version,hilos,promedio_ms,aceleracion,eficiencia,igual_al_secuencial,repeticiones_ms");
            for (Fila f : filas) {
                out.printf(Locale.US, "%s,%d,%.3f,%.3f,%.3f,%s,%s%n", f.version, f.hilos, f.promedio,
                        f.aceleracion, f.eficiencia, f.igual ? "si" : "no", f.repeticionesTexto().replace(", ", ";"));
            }
        }
    }

    /** Genera la tabla de tiempos con el mismo estilo de las paginas del wiki del AVAC. */
    private static void guardarTablaWiki(List<Fila> filas, Fila mejor, double histeresis, double fraccion,
                                         String ruta) throws IOException {
        String th = "background-color: #f2f4f8; border: 1px solid #d9dee7; border-bottom: 2px solid #1b2a4a; padding: 8px 10px;";
        String td = "border: 1px solid #d9dee7; padding: 8px 10px;";
        try (PrintWriter out = new PrintWriter(new FileWriter(ruta))) {
            out.println("<table style=\"border-collapse: collapse; width: 100%; font-size: 15px; margin: 12px 0;\">");
            out.println("<thead><tr>");
            out.println("<th style=\"" + th + " text-align: left;\">Versi&oacute;n</th>");
            out.println("<th style=\"" + th + " text-align: right;\">Hilos</th>");
            out.println("<th style=\"" + th + " text-align: right;\">Tiempo promedio (ms)</th>");
            out.println("<th style=\"" + th + " text-align: right;\">Aceleraci&oacute;n</th>");
            out.println("<th style=\"" + th + " text-align: right;\">Eficiencia</th>");
            out.println("<th style=\"" + th + " text-align: left;\">Repeticiones (ms)</th>");
            out.println("</tr></thead>");
            out.println("<tbody>");
            for (Fila f : filas) {
                boolean resaltar = f == mejor && f.hilos > 1;
                String estiloFila = resaltar ? " style=\"background-color: #e3f2f0; font-weight: bold;\"" : "";
                out.printf(Locale.US,
                        "<tr%s><td style=\"%s\">%s</td><td style=\"%s text-align: right;\">%d</td>"
                        + "<td style=\"%s text-align: right;\">%.2f</td><td style=\"%s text-align: right;\">%.2f</td>"
                        + "<td style=\"%s text-align: right;\">%.2f</td><td style=\"%s font-size: 13px; color: #5a6680;\">%s</td></tr>%n",
                        estiloFila, td, f.version, td, f.hilos, td, f.promedio, td, f.aceleracion,
                        td, f.eficiencia, td, f.repeticionesTexto());
            }
            out.println("</tbody>");
            out.println("</table>");
            out.printf(Locale.US,
                    "<p style=\"line-height: 1.65; margin: 0 0 10px 0;\">La etapa de hist&eacute;resis, que se ejecuta de forma "
                    + "secuencial en ambas versiones, tard&oacute; en promedio <strong>%.2f ms</strong>, es decir, el "
                    + "<strong>%.1f %%</strong> del tiempo secuencial.</p>%n", histeresis, fraccion * 100);
        }
    }

    /** Una fila de la tabla de resultados. */
    private static final class Fila {
        final String version;
        final int hilos;
        final double[] tiempos;
        final double promedio;
        final double aceleracion;
        final double eficiencia;
        final boolean igual;

        Fila(String version, int hilos, double[] tiempos, double promedioSecuencial, boolean igual) {
            this.version = version;
            this.hilos = hilos;
            this.tiempos = tiempos;
            this.promedio = promedio(tiempos);
            this.aceleracion = promedioSecuencial / this.promedio;
            this.eficiencia = this.aceleracion / hilos;
            this.igual = igual;
        }

        String repeticionesTexto() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tiempos.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(String.format(Locale.US, "%.2f", tiempos[i]));
            }
            return sb.toString();
        }
    }
}
