# Crea un ZIP limpio para un repositorio privado de GitHub.
# No incluye dependencias, claves, APKs ni instaladores generados.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$destination = Join-Path (Split-Path -Parent $root) 'MDS-Ventas-Native-GitHub.zip'
$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ('mds-ventas-github-' + [guid]::NewGuid().ToString('N'))

New-Item -ItemType Directory -Path $temporary | Out-Null
try {
    Get-ChildItem -LiteralPath $root -Force | Where-Object {
        $_.Name -notin @('node_modules', 'dist', 'release')
    } | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $temporary $_.Name) -Recurse -Force
    }

    @(
        (Join-Path $temporary 'android\.gradle'),
        (Join-Path $temporary 'android\build'),
        (Join-Path $temporary 'android\app\build'),
        (Join-Path $temporary 'android\local.properties'),
        (Join-Path $temporary 'android\app\google-services.json')
    ) | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object {
        Remove-Item -LiteralPath $_ -Force -Recurse
    }

    if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Force }
    Compress-Archive -Path (Join-Path $temporary '*') -DestinationPath $destination -CompressionLevel Optimal
    Write-Host "Paquete limpio creado: $destination" -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force -Recurse }
}
