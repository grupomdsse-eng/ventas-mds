const { app, BrowserWindow, shell } = require('electron');

const appUrl = process.env.MDS_VENTAS_URL || 'https://ventasmds.matriculadosdelsur.com';

function createWindow() {
  const window = new BrowserWindow({
    minWidth: 980,
    minHeight: 700,
    width: 1440,
    height: 920,
    title: 'MDS Ventas',
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  window.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  window.loadURL(appUrl);
}

app.whenReady().then(() => {
  const { session } = require('electron');
  session.defaultSession.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(permission === 'notifications');
  });
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
