$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent $PSScriptRoot
$data = (Resolve-Path -LiteralPath (Join-Path $project 'local-db\data')).Path
$config = (Resolve-Path -LiteralPath (Join-Path $project 'local-db\my.ini')).Path
$server = 'C:\xampp\mysql\bin\mysqld.exe'
$client = 'C:\xampp\mysql\bin\mysql.exe'
if (-not (Test-Path -LiteralPath $server) -or -not (Test-Path -LiteralPath $client)) {
    throw 'No se encontro MariaDB en C:\xampp\mysql\bin.'
}

function Database-Directory {
    # Windows PowerShell 5.1 treats native stderr as a terminating error when
    # ErrorActionPreference is Stop. A refused connection is expected here.
    $ErrorActionPreference = 'Continue'
    $output = & $client --host=127.0.0.1 --port=3306 --user=root --connect-timeout=2 `
        --batch --skip-column-names --execute='SELECT @@datadir' 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($output | Select-Object -First 1).Trim()
}

function Normalize-Directory([string]$path) {
    return (($path -replace '[\\/]+','/') -replace '/+$','')
}

$activeDirectory = Database-Directory
if ($activeDirectory) {
    if (-not [string]::Equals((Normalize-Directory $activeDirectory),
            (Normalize-Directory $data), [StringComparison]::OrdinalIgnoreCase)) {
        throw "El puerto 3306 usa otra base de MariaDB: $activeDirectory. Deten MySQL en XAMPP y vuelve a ejecutar MiGestor."
    }
    Write-Host 'Base recuperada de MiGestor ya esta activa.'
    exit 0
}

$socket = [System.Net.Sockets.TcpClient]::new()
try {
    $attempt = $socket.ConnectAsync('127.0.0.1', 3306)
    if ($attempt.Wait(1000) -and $socket.Connected) {
        throw 'El puerto 3306 esta ocupado por otro servidor. Deten MySQL en XAMPP y vuelve a ejecutar MiGestor.'
    }
} finally {
    $socket.Dispose()
}

Start-Process -FilePath $server -ArgumentList "--defaults-file=$config", '--standalone' -WindowStyle Hidden
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    $activeDirectory = Database-Directory
    if ($activeDirectory -and [string]::Equals((Normalize-Directory $activeDirectory),
            (Normalize-Directory $data), [StringComparison]::OrdinalIgnoreCase)) {
        Write-Host 'Base recuperada de MiGestor lista en localhost:3306.'
        exit 0
    }
}
throw "MariaDB no inicio desde $data. Revisa local-db\data\mysql-migestor-error.log."
