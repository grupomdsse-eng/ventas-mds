const { app, BrowserWindow, shell, Tray, Menu } = require('electron');
const path = require('path');

const defaultAppUrl = 'https://ventasmds.matriculadosdelsur.com';
let appUrl = defaultAppUrl;
try {
  const candidate = new URL(process.env.MDS_VENTAS_URL || defaultAppUrl);
  // El instalador nunca debe cargar una aplicación por HTTP ni un esquema
  // local inesperado, incluso si alguien modifica una variable de entorno.
  if (candidate.protocol === 'https:') appUrl = candidate.href;
} catch (_) { /* se conserva el destino corporativo por defecto */ }
const trustedOrigin = new URL(appUrl).origin;
let mainWindow;
let tray;
let exiting = false;

function isTrustedWebContents(webContents) {
  try { return new URL(webContents.getURL()).origin === trustedOrigin; }
  catch (_) { return false; }
}

function openExternalSafely(rawUrl) {
  try {
    const target = new URL(rawUrl);
    if (!['https:', 'mailto:', 'tel:'].includes(target.protocol)) return;
    shell.openExternal(target.href);
  } catch (_) { /* URL no válida: no se abre */ }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    minWidth: 980,
    minHeight: 700,
    width: 1440,
    height: 920,
    title: 'MDS Ventas',
    icon: path.join(__dirname, 'assets', 'icon.ico'),
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      // Mantiene activo el sondeo de mensajes al minimizar la aplicación,
      // para que Windows pueda mostrar los avisos del chat.
      backgroundThrottling: false
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    openExternalSafely(url);
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    try {
      if (new URL(url).origin === trustedOrigin) return;
    } catch (_) { /* se cancela abajo */ }
    event.preventDefault();
    openExternalSafely(url);
  });

  mainWindow.on('close', (event) => {
    if (exiting) return;
    event.preventDefault();
    mainWindow.hide();
  });

  mainWindow.loadURL(appUrl);
  return mainWindow;
}

function showWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) createWindow();
  mainWindow.show();
  mainWindow.focus();
}

app.whenReady().then(() => {
  const { session } = require('electron');
  const allowedPermissions = new Set(['notifications', 'media']);
  session.defaultSession.setPermissionCheckHandler((webContents, permission) => {
    return isTrustedWebContents(webContents) && allowedPermissions.has(permission);
  });
  session.defaultSession.setPermissionRequestHandler((webContents, permission, callback) => {
    callback(isTrustedWebContents(webContents) && allowedPermissions.has(permission));
  });
  createWindow();
  tray = new Tray(path.join(__dirname, 'assets', 'icon.ico'));
  tray.setToolTip('MDS Ventas');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Abrir MDS Ventas', click: showWindow },
    { type: 'separator' },
    { label: 'Salir', click: () => { exiting = true; app.quit(); } }
  ]));
  tray.on('double-click', showWindow);
  app.on('activate', () => {
    showWindow();
  });
});

app.on('before-quit', () => { exiting = true; });
