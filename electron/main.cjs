const { app, BrowserWindow, shell, Tray, Menu } = require('electron');
const path = require('path');

const appUrl = process.env.MDS_VENTAS_URL || 'https://ventasmds.matriculadosdelsur.com';
let mainWindow;
let tray;
let exiting = false;

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
      // Mantiene activo el sondeo de mensajes al minimizar la aplicación,
      // para que Windows pueda mostrar los avisos del chat.
      backgroundThrottling: false
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
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
  session.defaultSession.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(permission === 'notifications');
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
