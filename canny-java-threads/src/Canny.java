/**
 * Etapas del filtro de Canny.
 *
 * Cada metodo procesa solo las filas [y0, y1) de la imagen. La version
 * secuencial lo llama con todas las filas (0 a alto) y la version paralela
 * lo llama desde cada hilo con su propia franja de filas.
 *
 * Cada etapa lee un arreglo de entrada (que ya esta completo) y escribe en
 * un arreglo de salida distinto. Por eso los hilos nunca escriben en la misma
 * posicion y no hace falta sincronizacion dentro de una etapa.
 */
public final class Canny {

    public static final float UMBRAL_BAJO = 20f;
    public static final float UMBRAL_ALTO = 50f;

    public static final byte NINGUNO = 0;
    public static final byte DEBIL = 1;
    public static final byte FUERTE = 2;

    private static final int[][] GAUSS = {
        {2, 4, 5, 4, 2},
        {4, 9, 12, 9, 4},
        {5, 12, 15, 12, 5},
        {4, 9, 12, 9, 4},
        {2, 4, 5, 4, 2}
    };
    private static final float SUMA_GAUSS = 159f;

    private Canny() { }

    private static int limitar(int v, int max) {
        return v < 0 ? 0 : (v >= max ? max - 1 : v);
    }

    /** convierte los pixeles RGB a escala de grises (luminancia). */
    public static void escalaDeGrises(int[] rgb, float[] gris, int ancho, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            int fila = y * ancho;
            for (int x = 0; x < ancho; x++) {
                int p = rgb[fila + x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                gris[fila + x] = 0.299f * r + 0.587f * g + 0.114f * b;
            }
        }
    }

    /** suavizado gaussiano 5x5 para reducir el ruido. */
    public static void suavizadoGaussiano(float[] gris, float[] suave, int ancho, int alto, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < ancho; x++) {
                float suma = 0f;
                for (int ky = -2; ky <= 2; ky++) {
                    int fila = limitar(y + ky, alto) * ancho;
                    for (int kx = -2; kx <= 2; kx++) {
                        suma += GAUSS[ky + 2][kx + 2] * gris[fila + limitar(x + kx, ancho)];
                    }
                }
                suave[y * ancho + x] = suma / SUMA_GAUSS;
            }
        }
    }

    /**
     * Etapa 3: gradiente con el operador de Sobel.
     */
    public static void gradienteSobel(float[] suave, float[] magnitud, byte[] direccion,
                                      int ancho, int alto, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            int ya = limitar(y - 1, alto) * ancho;
            int yc = y * ancho;
            int yb = limitar(y + 1, alto) * ancho;
            for (int x = 0; x < ancho; x++) {
                int xi = limitar(x - 1, ancho);
                int xd = limitar(x + 1, ancho);

                float gx = (suave[ya + xd] + 2 * suave[yc + xd] + suave[yb + xd])
                         - (suave[ya + xi] + 2 * suave[yc + xi] + suave[yb + xi]);
                float gy = (suave[yb + xi] + 2 * suave[yb + x] + suave[yb + xd])
                         - (suave[ya + xi] + 2 * suave[ya + x] + suave[ya + xd]);

                magnitud[yc + x] = (float) Math.sqrt(gx * gx + gy * gy);

                double angulo = Math.toDegrees(Math.atan2(gy, gx));
                if (angulo < 0) {
                    angulo += 180;
                }
                byte d;
                if (angulo < 22.5 || angulo >= 157.5) {
                    d = 0;          // horizontal
                } else if (angulo < 67.5) {
                    d = 1;          // diagonal de 45 grados
                } else if (angulo < 112.5) {
                    d = 2;          // vertical
                } else {
                    d = 3;          // diagonal de 135 grados
                }
                direccion[yc + x] = d;
            }
        }
    }

    /**
     * supresion de no maximos. Un pixel se conserva solo si su magnitud
     * es mayor o igual que la de sus dos vecinos 
     */
    public static void supresionNoMaximos(float[] magnitud, byte[] direccion, float[] delgado,
                                          int ancho, int alto, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < ancho; x++) {
                int i = y * ancho + x;
                if (y == 0 || y == alto - 1 || x == 0 || x == ancho - 1) {
                    delgado[i] = 0f;
                    continue;
                }
                float m = magnitud[i];
                float v1;
                float v2;
                switch (direccion[i]) {
                    case 0:
                        v1 = magnitud[i - 1];
                        v2 = magnitud[i + 1];
                        break;
                    case 1:
                        v1 = magnitud[i - ancho + 1];
                        v2 = magnitud[i + ancho - 1];
                        break;
                    case 2:
                        v1 = magnitud[i - ancho];
                        v2 = magnitud[i + ancho];
                        break;
                    default:
                        v1 = magnitud[i - ancho - 1];
                        v2 = magnitud[i + ancho + 1];
                        break;
                }
                delgado[i] = (m >= v1 && m >= v2) ? m : 0f;
            }
        }
    }

    /** doble umbralizacion. Clasifica cada pixel como fuerte, debil o ninguno. */
    public static void dobleUmbral(float[] delgado, byte[] clase, int ancho, int y0, int y1) {
        for (int i = y0 * ancho; i < y1 * ancho; i++) {
            float m = delgado[i];
            if (m >= UMBRAL_ALTO) {
                clase[i] = FUERTE;
            } else if (m >= UMBRAL_BAJO) {
                clase[i] = DEBIL;
            } else {
                clase[i] = NINGUNO;
            }
        }
    }

    /**
     * histeresis. Parte de los pixeles fuertes y convierte en borde a los
     * pixeles debiles conectados a ellos (8 vecinos). Un borde puede cruzar toda
     * la imagen, por eso esta etapa se ejecuta de forma secuencial en ambas versiones.
     *
     * @return 
     */
    public static byte[] histeresis(byte[] clase, int ancho, int alto) {
        int total = ancho * alto;
        byte[] bordes = new byte[total];
        int[] pila = new int[total];
        int tope = 0;

        for (int i = 0; i < total; i++) {
            if (clase[i] == FUERTE) {
                bordes[i] = (byte) 255;
                pila[tope++] = i;
            }
        }

        while (tope > 0) {
            int i = pila[--tope];
            int y = i / ancho;
            int x = i % ancho;
            for (int dy = -1; dy <= 1; dy++) {
                int ny = y + dy;
                if (ny < 0 || ny >= alto) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    if ((dx == 0 && dy == 0) || nx < 0 || nx >= ancho) {
                        continue;
                    }
                    int j = ny * ancho + nx;
                    if (clase[j] == DEBIL && bordes[j] == 0) {
                        bordes[j] = (byte) 255;
                        pila[tope++] = j;
                    }
                }
            }
        }
        return bordes;
    }
}
