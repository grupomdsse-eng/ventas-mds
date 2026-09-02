# MDS Ventas instalable

Este proyecto genera dos envoltorios nativos que usan la aplicación PHP ya publicada en `https://ventasmds.matriculadosdelsur.com`.

- Windows: `npm run build:windows` crea un instalador `.exe` en `dist/`.
- Android: `npm run android:sync` genera el proyecto nativo en `android/`. Con Android Studio se compila un APK firmado para distribuirlo.

La aplicación requiere conexión a Internet, porque datos, usuarios y archivos siguen protegidos y centralizados en el servidor de MDS Ventas.

## Widgets de Android

La APK incluye cuatro widgets para la pantalla de inicio: **Flujos próximos** es un tablón 3×2 con las tareas de hoy y mañana y el color de su etapa; **Chat directo** es un acceso circular con la foto del compañero elegido; **Hacer pedido** es un botón 1×1 para abrir el formulario nativo; y **Pedidos pendientes** muestra visualmente los pedidos aún por tramitar. El pedido rápido obliga a seleccionar primero la empresa y después muestra únicamente sus clientes. Los comerciales no pueden elegir preparador desde este formulario. Para que puedan consultar datos, inicia sesión una vez en la APK y marca “Mantener mi sesión iniciada”. Los tablones se actualizan aproximadamente cada 30 minutos, al abrir la aplicación y con su botón de actualización. El de pedidos se refresca también al llegar el aviso de un pedido nuevo si Firebase está configurado.

La APK incluye gestos laterales: desliza desde el borde izquierdo hacia la derecha para volver, y desde el borde derecho hacia la izquierda para avanzar. Solo se activan al empezar en el borde, por lo que no interfieren con el tablero ni con las conversaciones.

## Generación automática con GitHub

1. Ejecuta `powershell -ExecutionPolicy Bypass -File .\PREPARAR-GITHUB.ps1` y usa el ZIP que genera para crear un repositorio privado en GitHub. No subas `node_modules`, `dist` ni el APK generado. Consulta [SUBIR-A-GITHUB.md](SUBIR-A-GITHUB.md) para los pasos completos.
2. En GitHub entra en **Actions** y ejecuta **Generar instalador Windows** o **Generar APK Android**.
3. Cuando termine, descarga el resultado desde **Artifacts**. El instalador Windows será un `.exe` y Android generará `app-debug.apk`.

El APK de la acción está firmado con la clave de depuración de GitHub, válido para instalarlo manualmente en los móviles del equipo. Para publicar en Google Play se debe configurar una clave de firma propia.

## Preparación Android local

1. Instala Android Studio y un JDK 21.
2. Ejecuta `npm install` y después `npm run android:sync`.
3. Abre la carpeta `android` con Android Studio.
4. Usa **Build > Generate Signed Bundle / APK > APK** para crear el instalador de producción.

El manifiesto Android permite cargar exclusivamente el dominio HTTPS de MDS Ventas. La cámara y el selector de archivos se abren desde los controles de adjuntos de la aplicación web.

## Notificaciones

La web/PWA y el instalador de Windows solicitan permiso y muestran avisos de mensajes y pedidos mientras se están ejecutando. Para que Android los reciba con la APK cerrada —y para garantizar avisos nativos en segundo plano— hay que completar Firebase Cloud Messaging; consulta [docs/NOTIFICACIONES.md](docs/NOTIFICACIONES.md).
