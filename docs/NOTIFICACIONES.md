# Notificaciones de MDS Ventas

La aplicación web y el instalador de Windows avisan de nuevos mensajes y cambios en pedidos mientras están abiertos. Para recibir avisos en Android cuando la APK está cerrada o en segundo plano se necesita Firebase Cloud Messaging (FCM).

## Configuración necesaria para avisos en segundo plano

1. Crea un proyecto en Firebase y registra una aplicación Android con el identificador `com.grupomds.ventas`.
2. Descarga `google-services.json` y súbelo como secreto de GitHub llamado `FIREBASE_GOOGLE_SERVICES_JSON_BASE64` después de codificarlo en Base64.
3. En GitHub, ve a **Settings > Secrets and variables > Actions**, crea el secreto `FIREBASE_GOOGLE_SERVICES_JSON_BASE64` y pega el resultado de codificar `google-services.json` en Base64. En PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\ruta\google-services.json'))`.
4. En Plesk, crea la carpeta `config` si no existe. Copia `config/push-firebase.example.php` como `config/push-firebase.php`, descarga una clave de cuenta de servicio de Firebase y súbela como `config/firebase-service-account.json`. Completa el `project_id` con el identificador exacto de Firebase.
5. Sube la actualización PHP y abre `actualizar-v23.php` con una cuenta administradora. Después instala la APK recién generada y abre MDS Ventas una vez con cada usuario para registrar su dispositivo.

No subas `firebase-service-account.json`, `push-firebase.php` ni `google-services.json` al repositorio. Son credenciales privadas.

FCM necesita tanto el cliente Android como un entorno de servidor de confianza que envíe las notificaciones. El APK generado por la acción de GitHub se instala y funciona sin Firebase; con Firebase se habilitan además los avisos en segundo plano.
