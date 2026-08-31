# Notificaciones de MDS Ventas

La aplicación web y el instalador de Windows avisan de nuevos mensajes y cambios en pedidos mientras están abiertos. Para recibir avisos en Android cuando la APK está cerrada o en segundo plano se necesita Firebase Cloud Messaging (FCM).

## Configuración necesaria para avisos en segundo plano

1. Crea un proyecto en Firebase y registra una aplicación Android con el identificador `com.grupomds.ventas`.
2. Descarga `google-services.json` y súbelo como secreto de GitHub llamado `FIREBASE_GOOGLE_SERVICES_JSON_BASE64` después de codificarlo en Base64.
3. Añade Firebase Cloud Messaging al proyecto Android y configura el servidor PHP para registrar los tokens FCM de cada usuario.
4. Guarda las credenciales de servidor de Firebase únicamente como secretos del hosting. Nunca las subas al repositorio ni a Plesk.

FCM necesita tanto el cliente Android como un entorno de servidor de confianza que envíe las notificaciones. El APK generado por la acción de GitHub se instala y funciona sin Firebase; con Firebase se habilitan además los avisos en segundo plano.
