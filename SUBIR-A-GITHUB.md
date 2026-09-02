# Publicar las aplicaciones de MDS Ventas desde GitHub

1. En PowerShell, dentro de esta carpeta, ejecuta `powershell -ExecutionPolicy Bypass -File .\PREPARAR-GITHUB.ps1`. Se creará `MDS-Ventas-Native-v6-GitHub.zip` en la carpeta padre.
2. En GitHub crea un repositorio **privado**, por ejemplo `mds-ventas-instaladores`.
3. Descomprime el ZIP y sube su contenido a la raíz del repositorio. No subas `node_modules`, `dist`, APKs, EXEs, `android/local.properties` ni `google-services.json`.
4. Confirma la subida a la rama `main`.
5. Abre la pestaña **Actions**. Autoriza los flujos si GitHub lo solicita.
6. Ejecuta **Generar instalador Windows** para obtener `MDS Ventas Setup.exe` o **Generar APK Android** para obtener `app-debug.apk`.
7. Al terminar cada ejecución, entra en **Artifacts** y descarga el archivo correspondiente.

## Instalar

- **Windows:** abre el EXE descargado y sigue el asistente. Windows puede pedir confirmación porque se trata de un instalador interno sin certificado de firma de código.
- **Android:** descarga el APK, permite instalar aplicaciones desde el navegador/gestor de archivos que estés usando e instálalo. Esta APK es para distribución interna; no se publica en Google Play.

## Notificaciones en segundo plano

Los avisos mientras la aplicación está abierta se incluyen en la web actualizada. Para recibirlos con Android cerrado se necesita configurar Firebase Cloud Messaging siguiendo `docs/NOTIFICACIONES.md`. No incluyas credenciales de Firebase en el repositorio.
