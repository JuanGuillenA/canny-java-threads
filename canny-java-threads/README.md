# Filtro de Canny secuencial y paralelo con Java Threads

Actividad 03 de Computación Paralela. Aplica el detector de bordes de Canny
a una imagen y compara una versión secuencial con una versión paralela que usa
la clase `Thread` con 2, 4 y todos los hilos lógicos del equipo.

## Estructura

```
canny-java-threads/
├── imagenes/porsche.jpg        Imagen de entrada
├── resultados/                 Imágenes de bordes, tiempos.csv y tabla para el wiki
└── src/
    ├── Main.java               Mide tiempos, compara y guarda resultados
    ├── Canny.java              Las 6 etapas del filtro (por rango de filas)
    ├── CannySecuencial.java    Versión con un solo hilo
    ├── CannyParalelo.java      Versión con Thread, start() y join()
    └── ImagenUtil.java         Lectura y escritura de imágenes
```

## Requisitos

- JDK 17 o superior
- (Opcional) Visual Studio Code con el *Extension Pack for Java*

## Ejecutar desde la terminal

Desde la carpeta del proyecto:

```bash
javac -d bin src/*.java
java -Xmx2g -cp bin Main
```

Para usar otra imagen: `java -Xmx2g -cp bin Main ruta/a/la/imagen.jpg`

## Ejecutar desde VS Code

Abrir la carpeta del proyecto, abrir `src/Main.java` y pulsar **Run**
(o F5 con la configuración "Canny: secuencial vs paralelo").

## Etapas del filtro

1. Escala de grises
2. Suavizado gaussiano 5x5
3. Gradiente de Sobel (magnitud y dirección)
4. Supresión de no máximos
5. Doble umbral (bajo = 20, alto = 50)
6. Histéresis (secuencial en ambas versiones)

Las etapas 1 a 5 se paralelizan dividiendo la imagen en franjas horizontales,
una por hilo. Entre etapas se espera a todos los hilos con `join()`.

## Medición

- Se mide solo el procesamiento con `System.nanoTime()` (sin leer ni guardar la imagen).
- 3 ejecuciones de calentamiento de la JVM que no se cuentan.
- 5 repeticiones por configuración; se reporta el promedio.
- Aceleración S = Ts / Tp y eficiencia E = S / p.
- El programa verifica que la imagen paralela sea idéntica píxel a píxel a la secuencial.
