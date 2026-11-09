[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OwnerEmail,

    [Parameter(Mandatory = $true)]
    [string]$OwnerPassword,

    [Parameter(Mandatory = $true)]
    [string]$ManagerPassword,

    [Parameter(Mandatory = $true)]
    [string]$CashierPassword,

    [string]$BaseUrl = "http://localhost:8080/migestor",
    [string]$MySqlExe = "C:\xampp\mysql\bin\mysql.exe",
    [string]$DatabaseName = "gestor_no_code",
    [string]$DatabaseUser = "root",
    [string]$DatabasePassword = ""
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-MySql {
    param([Parameter(Mandatory = $true)][string]$Sql)

    if (-not (Test-Path -LiteralPath $MySqlExe)) {
        throw "No se encontró mysql.exe en $MySqlExe"
    }

    $arguments = @(
        "-u", $DatabaseUser,
        "-N", "-B",
        "--default-character-set=utf8mb4",
        "--database=$DatabaseName",
        "--execute=$Sql"
    )
    if (-not [string]::IsNullOrEmpty($DatabasePassword)) {
        $arguments = @("-u", $DatabaseUser, "--password=$DatabasePassword") + $arguments[2..($arguments.Count - 1)]
    }

    $output = @(& $MySqlExe @arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL rechazó una consulta: $($output -join [Environment]::NewLine)"
    }
    return @($output | ForEach-Object { [string]$_ })
}

function Get-SqlScalar {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $rows = @(Invoke-MySql -Sql $Sql)
    if ($rows.Count -eq 0) { return "" }
    return [string]$rows[0]
}

function Escape-SqlLiteral {
    param([AllowEmptyString()][string]$Value)
    if ($null -eq $Value) { return "" }
    return $Value.Replace("\", "\\").Replace("'", "''")
}

function Get-FlashError {
    param([string]$Html)
    if ([string]::IsNullOrWhiteSpace($Html)) { return $null }
    $match = [regex]::Match(
        $Html,
        '<div\s+class="flash error"[^>]*>(?<message>.*?)</div>',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    if (-not $match.Success) { return $null }
    $plain = $match.Groups['message'].Value -replace '<[^>]+>', ' '
    $plain = [System.Net.WebUtility]::HtmlDecode($plain)
    return ($plain -replace '\s+', ' ').Trim()
}

function Invoke-AppPost {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][hashtable]$Body
    )
    $response = Invoke-WebRequest -Uri ($BaseUrl + $Path) -Method Post -Body $Body `
        -WebSession $script:Session -MaximumRedirection 10 -UseBasicParsing
    $flashError = Get-FlashError -Html $response.Content
    if ($flashError) { throw "La aplicación rechazó la operación en ${Path}: $flashError" }
    if ($response.Content -match 'Correo o contraseña incorrectos') {
        throw "La sesión dejó de estar autenticada durante el sembrado."
    }
    return $response
}

function Assert-AppPage {
    param([Parameter(Mandatory = $true)][string]$Path)
    $response = Invoke-WebRequest -Uri ($BaseUrl + $Path) -WebSession $script:Session `
        -MaximumRedirection 10 -UseBasicParsing
    if ($response.StatusCode -ne 200) { throw "La ruta $Path respondió HTTP $($response.StatusCode)." }
    $flashError = Get-FlashError -Html $response.Content
    if ($flashError) { throw "La ruta $Path mostró un error: $flashError" }
    return $response
}

function New-DemoModule {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Description,
        [ValidateSet('TABLE', 'PANEL')][string]$Type = 'TABLE',
        [long]$LinkedModuleId = 0,
        [ValidateSet('', 'REGISTER', 'ADJUST')][string]$PanelAction = ''
    )

    $previousId = [long](Get-SqlScalar -Sql "SELECT COALESCE(MAX(id),0) FROM custom_modules WHERE business_id=$script:BusinessId")
    $body = @{
        name        = $Name
        description = $Description
        moduleType  = $Type
    }
    if ($Type -eq 'PANEL') {
        $body.linkedModuleId = [string]$LinkedModuleId
        $body.panelAction = $PanelAction
    }
    [void](Invoke-AppPost -Path '/app/modules/create' -Body $body)

    # En Windows, mysql.exe puede recibir mal los acentos escritos en --execute.
    # Recuperar la clave por su secuencia evita depender de la codificación del nombre.
    $id = Get-SqlScalar -Sql "SELECT id FROM custom_modules WHERE business_id=$script:BusinessId AND id>$previousId ORDER BY id DESC LIMIT 1"
    if (-not $id) { throw "No se pudo recuperar el módulo $Name" }
    return [long]$id
}

function New-DemoColumn {
    param(
        [Parameter(Mandatory = $true)][long]$ModuleId,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)]
        [ValidateSet('TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'RELATION', 'LOOKUP', 'FORMULA')]
        [string]$DataType,
        [string]$Options = '',
        [switch]$Required,
        [string]$DefaultValue = '',
        [long]$RelatedModuleId = 0,
        [long]$RelatedDisplayColumnId = 0,
        [long]$RelationColumnId = 0,
        [long]$SourceColumnId = 0,
        [string]$FormulaExpression = '',
        [string]$LowStockThreshold = ''
    )

    $previousId = [long](Get-SqlScalar -Sql "SELECT COALESCE(MAX(id),0) FROM module_columns WHERE business_id=$script:BusinessId AND module_id=$ModuleId")
    $body = @{
        moduleId             = [string]$ModuleId
        name                 = $Name
        dataType             = $DataType
        options              = $Options
        defaultValue         = $DefaultValue
        relatedModuleId      = if ($RelatedModuleId -gt 0) { [string]$RelatedModuleId } else { '' }
        relatedDisplayColumnId = if ($RelatedDisplayColumnId -gt 0) { [string]$RelatedDisplayColumnId } else { '' }
        relationColumnId     = if ($RelationColumnId -gt 0) { [string]$RelationColumnId } else { '' }
        sourceColumnId       = if ($SourceColumnId -gt 0) { [string]$SourceColumnId } else { '' }
        formulaExpression    = $FormulaExpression
        lowStockThreshold    = $LowStockThreshold
    }
    if ($Required) { $body.requiredField = 'on' }
    [void](Invoke-AppPost -Path '/app/columns' -Body $body)

    $id = Get-SqlScalar -Sql "SELECT id FROM module_columns WHERE business_id=$script:BusinessId AND module_id=$ModuleId AND id>$previousId ORDER BY id DESC LIMIT 1"
    if (-not $id) { throw "No se pudo recuperar la columna $Name" }
    return [long]$id
}

function Add-DemoRow {
    param(
        [Parameter(Mandatory = $true)][long]$ModuleId,
        [Parameter(Mandatory = $true)][hashtable]$Values
    )
    $body = @{ moduleId = [string]$ModuleId }
    foreach ($entry in $Values.GetEnumerator()) {
        $body["col_$($entry.Key)"] = [string]$entry.Value
    }
    [void](Invoke-AppPost -Path '/app/rows' -Body $body)
    $id = Get-SqlScalar -Sql "SELECT MAX(id) FROM module_rows WHERE business_id=$script:BusinessId AND module_id=$ModuleId"
    if (-not $id) { throw "No se pudo recuperar la fila recién creada del módulo $ModuleId" }
    return [long]$id
}

function Set-PanelSettings {
    param(
        [Parameter(Mandatory = $true)][long]$PanelId,
        [Parameter(Mandatory = $true)][long]$LinkedModuleId,
        [Parameter(Mandatory = $true)][ValidateSet('REGISTER', 'ADJUST')][string]$PanelAction,
        [long]$AdjustmentColumnId = 0,
        [ValidateSet('', 'ADD', 'SUBTRACT', 'SET')][string]$AdjustmentOperation = '',
        [switch]$AllowNegative
    )
    $body = @{
        moduleId            = [string]$PanelId
        action              = 'settings'
        linkedModuleId      = [string]$LinkedModuleId
        panelAction         = $PanelAction
        adjustmentColumnId  = if ($AdjustmentColumnId -gt 0) { [string]$AdjustmentColumnId } else { '' }
        adjustmentOperation = $AdjustmentOperation
    }
    if ($AllowNegative) { $body.allowNegative = 'on' }
    [void](Invoke-AppPost -Path '/app/panels/config' -Body $body)
}

function Set-PanelFields {
    param(
        [Parameter(Mandatory = $true)][long]$PanelId,
        [Parameter(Mandatory = $true)][long[]]$VisibleColumnIds,
        [long[]]$RequiredColumnIds = @()
    )
    $rows = @(Invoke-MySql -Sql "SELECT id FROM module_columns WHERE business_id=$script:BusinessId AND module_id=(SELECT linked_module_id FROM custom_modules WHERE id=$PanelId AND business_id=$script:BusinessId) AND data_type NOT IN ('LOOKUP','FORMULA') AND LOWER(TRIM(name))<>'id' ORDER BY position,id")
    $body = @{ moduleId = [string]$PanelId; action = 'fields' }
    $position = 0
    foreach ($row in $rows) {
        $columnId = [long]$row
        $body["position_$columnId"] = [string]$position
        $body["default_$columnId"] = ''
        if ($VisibleColumnIds -contains $columnId) { $body["visible_$columnId"] = 'on' }
        if ($RequiredColumnIds -contains $columnId) { $body["required_$columnId"] = 'on' }
        $position++
    }
    [void](Invoke-AppPost -Path '/app/panels/config' -Body $body)
}

function Add-PanelRow {
    param(
        [Parameter(Mandatory = $true)][long]$PanelId,
        [Parameter(Mandatory = $true)][long]$TargetModuleId,
        [Parameter(Mandatory = $true)][hashtable]$Values
    )
    $body = @{ moduleId = [string]$PanelId }
    foreach ($entry in $Values.GetEnumerator()) {
        $body["col_$($entry.Key)"] = [string]$entry.Value
    }
    [void](Invoke-AppPost -Path '/app/rows' -Body $body)
    return [long](Get-SqlScalar -Sql "SELECT MAX(id) FROM module_rows WHERE business_id=$script:BusinessId AND module_id=$TargetModuleId")
}

function Invoke-StockAdjustment {
    param(
        [Parameter(Mandatory = $true)][long]$PanelId,
        [Parameter(Mandatory = $true)][long]$RowId,
        [Parameter(Mandatory = $true)][decimal]$Quantity,
        [Parameter(Mandatory = $true)][string]$Note
    )
    $body = @{
        moduleId      = [string]$PanelId
        rowId         = [string]$RowId
        quantity      = $Quantity.ToString([System.Globalization.CultureInfo]::InvariantCulture)
        note          = $Note
        operationToken = [guid]::NewGuid().ToString()
    }
    [void](Invoke-AppPost -Path '/app/rows' -Body $body)
}

function New-SavedView {
    param(
        [Parameter(Mandatory = $true)][long]$ModuleId,
        [Parameter(Mandatory = $true)][string]$Name,
        [long]$SortColumnId = 0,
        [ValidateSet('ASC', 'DESC')][string]$SortDirection = 'ASC',
        [switch]$DefaultView,
        [long]$FilterColumnId = 0,
        [string]$FilterOperator = '',
        [string]$FilterValue = '',
        [string]$FilterSecondValue = ''
    )
    $body = @{
        moduleId     = [string]$ModuleId
        name         = $Name
        sortColumnId = if ($SortColumnId -gt 0) { [string]$SortColumnId } else { '' }
        sortDirection = $SortDirection
    }
    if ($DefaultView) { $body.defaultView = 'on' }
    if ($FilterColumnId -gt 0) {
        $body.filterColumnId = [string]$FilterColumnId
        $body.filterOperator = $FilterOperator
        $body.filterValue = $FilterValue
        $body.filterSecondValue = $FilterSecondValue
    }
    [void](Invoke-AppPost -Path '/app/views' -Body $body)
}

function New-SavedReport {
    param(
        [Parameter(Mandatory = $true)][long]$ModuleId,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet('TABLE', 'SUMMARY', 'CHART')][string]$ReportType,
        [Parameter(Mandatory = $true)][ValidateSet('COUNT', 'SUM', 'AVG', 'MIN', 'MAX')][string]$AggregateFunction,
        [long]$ValueColumnId = 0,
        [long]$GroupByColumnId = 0,
        [ValidateSet('BAR', 'LINE', 'PIE')][string]$ChartType = 'BAR'
    )
    $body = @{
        action            = 'create'
        moduleId          = [string]$ModuleId
        name              = $Name
        reportType        = $ReportType
        aggregateFunction = $AggregateFunction
        valueColumnId     = if ($ValueColumnId -gt 0) { [string]$ValueColumnId } else { '' }
        groupByColumnId   = if ($GroupByColumnId -gt 0) { [string]$GroupByColumnId } else { '' }
        chartType         = $ChartType
    }
    [void](Invoke-AppPost -Path '/app/reports' -Body $body)
}

function New-AlertRule {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][long]$ColumnId,
        [Parameter(Mandatory = $true)][string]$Operator,
        [Parameter(Mandatory = $true)][string]$ComparisonValue,
        [Parameter(Mandatory = $true)][string]$Message
    )
    [void](Invoke-AppPost -Path '/app/alerts' -Body @{
        action             = 'create'
        name               = $Name
        columnId           = [string]$ColumnId
        conditionOperator  = $Operator
        comparisonValue    = $ComparisonValue
        messageTemplate    = $Message
    })
}

function New-DemoUser {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Email,
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][ValidateSet('MANAGER', 'EMPLOYEE')][string]$Role
    )
    [void](Invoke-AppPost -Path '/app/settings/users' -Body @{
        action = 'create'; name = $Name; email = $Email; password = $Password; role = $Role
    })
    $safeEmail = Escape-SqlLiteral -Value $Email.ToLowerInvariant()
    return [long](Get-SqlScalar -Sql "SELECT id FROM users WHERE business_id=$script:BusinessId AND LOWER(email)='$safeEmail' LIMIT 1")
}

function Disable-DemoUser {
    param(
        [Parameter(Mandatory = $true)][long]$UserId,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Email,
        [Parameter(Mandatory = $true)][ValidateSet('MANAGER', 'EMPLOYEE')][string]$Role
    )
    # La ausencia intencional del campo "active" deja la cuenta inactiva.
    [void](Invoke-AppPost -Path '/app/settings/users' -Body @{
        action = 'update'; userId = [string]$UserId; name = $Name; email = $Email; role = $Role
    })
}

function Set-CashierPermissions {
    param(
        [Parameter(Mandatory = $true)][long]$UserId,
        [Parameter(Mandatory = $true)][hashtable]$Permissions
    )
    $body = @{ action = 'permission'; userId = [string]$UserId }
    foreach ($moduleEntry in $Permissions.GetEnumerator()) {
        $moduleId = [long]$moduleEntry.Key
        $capabilities = @($moduleEntry.Value)
        $body["view_$moduleId"] = 'on'
        foreach ($capability in $capabilities) {
            $body["$($capability.ToLowerInvariant())_$moduleId"] = 'on'
        }
    }
    [void](Invoke-AppPost -Path '/app/settings/users' -Body $body)
}

Write-Host "1/12 Verificando servidor, sesión y cuenta..."
$login = Invoke-WebRequest -Uri ($BaseUrl + '/login') -Method Post -Body @{
    email = $OwnerEmail
    password = $OwnerPassword
} -WebSession $script:Session -MaximumRedirection 10 -UseBasicParsing
if ($login.Content -match 'Correo o contraseña incorrectos') {
    throw "No se pudo iniciar sesión con la cuenta indicada."
}
if ($login.Content -notmatch 'Panel principal|Tus módulos|Nuevo módulo') {
    throw "La aplicación respondió, pero no se reconoció el panel autenticado."
}

$safeOwnerEmail = Escape-SqlLiteral -Value $OwnerEmail.ToLowerInvariant()
$ownerRow = @(Invoke-MySql -Sql "SELECT id,business_id,role,active FROM users WHERE LOWER(email)='$safeOwnerEmail' LIMIT 1")
if ($ownerRow.Count -ne 1) { throw "No se encontró exactamente una cuenta propietaria con ese correo." }
$ownerParts = $ownerRow[0] -split "`t"
$script:OwnerUserId = [long]$ownerParts[0]
$script:BusinessId = [long]$ownerParts[1]
if ($ownerParts[2] -ne 'OWNER' -or $ownerParts[3] -ne '1') {
    throw "La cuenta indicada debe ser un propietario activo para crear la demostración."
}

$existingDemo = [long](Get-SqlScalar -Sql "SELECT (SELECT COUNT(*) FROM custom_modules WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%') + (SELECT COUNT(*) FROM report_definitions WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%') + (SELECT COUNT(*) FROM alert_rules WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%') + (SELECT COUNT(*) FROM users WHERE business_id=$script:BusinessId AND email IN ('gerencia.demo@migestor.local','caja.demo@migestor.local'))")
if ($existingDemo -gt 0) {
    throw "Ya existe contenido [DEMO] en esta cuenta. No se hicieron duplicados."
}

$script:OriginalModuleCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM custom_modules WHERE business_id=$script:BusinessId")
$script:OriginalRowCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM module_rows WHERE business_id=$script:BusinessId")

Write-Host "2/12 Creando las seis tablas de demostración..."
$guideModule = New-DemoModule -Name '[DEMO] 00 - Guía de recorrido' -Description 'Empieza aquí: recorrido ordenado por todas las funciones incluidas en esta demostración.'
$providersModule = New-DemoModule -Name '[DEMO] Proveedores' -Description 'Directorio de proveedores con datos de contacto, categoría y estado.'
$clientsModule = New-DemoModule -Name '[DEMO] Clientes' -Description 'Clientes de mostrador, frecuentes y empresas con límite de crédito.'
$productsModule = New-DemoModule -Name '[DEMO] Productos e inventario' -Description 'Catálogo con proveedor relacionado, datos heredados, fórmulas y control de existencias.'
$salesModule = New-DemoModule -Name '[DEMO] Ventas' -Description 'Registro de ventas con cliente, producto, precio heredado y total calculado.'
$purchasesModule = New-DemoModule -Name '[DEMO] Compras' -Description 'Órdenes de compra y recepciones vinculadas con proveedores y productos.'

Write-Host "3/12 Configurando columnas, relaciones, lookups y fórmulas..."
$guideColumns = [ordered]@{}
$guideColumns.Order = New-DemoColumn -ModuleId $guideModule -Name 'Orden' -DataType NUMBER -Required
$guideColumns.Function = New-DemoColumn -ModuleId $guideModule -Name 'Función' -DataType SELECT -Options 'Inicio,Módulos,Datos,Relaciones,Fórmulas,Paneles,Vistas,Reportes,Alertas,Usuarios,Importar y exportar,Historial,Respaldo' -Required
$guideColumns.Where = New-DemoColumn -ModuleId $guideModule -Name 'Dónde probar' -DataType TEXT -Required
$guideColumns.Instruction = New-DemoColumn -ModuleId $guideModule -Name 'Instrucción' -DataType TEXT -Required
$guideColumns.Status = New-DemoColumn -ModuleId $guideModule -Name 'Estado' -DataType SELECT -Options 'Ejemplo cargado,Listo para probar' -Required -DefaultValue 'Listo para probar'

$providerColumns = [ordered]@{}
$providerColumns.Company = New-DemoColumn -ModuleId $providersModule -Name 'Empresa' -DataType TEXT -Required
$providerColumns.Contact = New-DemoColumn -ModuleId $providersModule -Name 'Contacto' -DataType TEXT
$providerColumns.Phone = New-DemoColumn -ModuleId $providersModule -Name 'Teléfono' -DataType TEXT
$providerColumns.Email = New-DemoColumn -ModuleId $providersModule -Name 'Correo' -DataType TEXT
$providerColumns.Category = New-DemoColumn -ModuleId $providersModule -Name 'Categoría' -DataType SELECT -Options 'Abarrotes,Bebidas,Papelería,Limpieza,Café' -Required
$providerColumns.Active = New-DemoColumn -ModuleId $providersModule -Name 'Activo' -DataType BOOLEAN -Required -DefaultValue 'true'

$clientColumns = [ordered]@{}
$clientColumns.Name = New-DemoColumn -ModuleId $clientsModule -Name 'Cliente' -DataType TEXT -Required
$clientColumns.Phone = New-DemoColumn -ModuleId $clientsModule -Name 'Teléfono' -DataType TEXT
$clientColumns.Email = New-DemoColumn -ModuleId $clientsModule -Name 'Correo' -DataType TEXT
$clientColumns.Type = New-DemoColumn -ModuleId $clientsModule -Name 'Tipo' -DataType SELECT -Options 'Nuevo,Frecuente,Empresa' -Required -DefaultValue 'Nuevo'
$clientColumns.CreditLimit = New-DemoColumn -ModuleId $clientsModule -Name 'Límite de crédito' -DataType NUMBER -DefaultValue '0'
$clientColumns.LastPurchase = New-DemoColumn -ModuleId $clientsModule -Name 'Última compra' -DataType DATE
$clientColumns.Active = New-DemoColumn -ModuleId $clientsModule -Name 'Activo' -DataType BOOLEAN -Required -DefaultValue 'true'

$productColumns = [ordered]@{}
$productColumns.Sku = New-DemoColumn -ModuleId $productsModule -Name 'SKU' -DataType TEXT -Required
$productColumns.Name = New-DemoColumn -ModuleId $productsModule -Name 'Producto' -DataType TEXT -Required
$productColumns.Category = New-DemoColumn -ModuleId $productsModule -Name 'Categoría' -DataType SELECT -Options 'Bebidas,Abarrotes,Papelería,Snacks,Lácteos,Limpieza' -Required
$productColumns.Supplier = New-DemoColumn -ModuleId $productsModule -Name 'Proveedor' -DataType RELATION -Required -RelatedModuleId $providersModule -RelatedDisplayColumnId $providerColumns.Company
$productColumns.SupplierContact = New-DemoColumn -ModuleId $productsModule -Name 'Contacto del proveedor' -DataType LOOKUP -RelationColumnId $productColumns.Supplier -SourceColumnId $providerColumns.Contact
$productColumns.Cost = New-DemoColumn -ModuleId $productsModule -Name 'Costo' -DataType NUMBER -Required
$productColumns.Price = New-DemoColumn -ModuleId $productsModule -Name 'Precio' -DataType NUMBER -Required
$productColumns.Stock = New-DemoColumn -ModuleId $productsModule -Name 'Existencias' -DataType NUMBER -Required -DefaultValue '0' -LowStockThreshold '5'
$productColumns.Expiry = New-DemoColumn -ModuleId $productsModule -Name 'Caducidad' -DataType DATE
$productColumns.Active = New-DemoColumn -ModuleId $productsModule -Name 'Activo' -DataType BOOLEAN -Required -DefaultValue 'true'
$productColumns.Margin = New-DemoColumn -ModuleId $productsModule -Name 'Margen %' -DataType FORMULA -FormulaExpression "({$($productColumns.Price)}-{$($productColumns.Cost)})/{$($productColumns.Price)}*100"
$productColumns.InventoryValue = New-DemoColumn -ModuleId $productsModule -Name 'Valor del inventario' -DataType FORMULA -FormulaExpression "{$($productColumns.Cost)}*{$($productColumns.Stock)}"

$saleColumns = [ordered]@{}
$saleColumns.Date = New-DemoColumn -ModuleId $salesModule -Name 'Fecha' -DataType DATE -Required
$saleColumns.Folio = New-DemoColumn -ModuleId $salesModule -Name 'Folio' -DataType TEXT -Required
$saleColumns.Client = New-DemoColumn -ModuleId $salesModule -Name 'Cliente' -DataType RELATION -RelatedModuleId $clientsModule -RelatedDisplayColumnId $clientColumns.Name
$saleColumns.ClientType = New-DemoColumn -ModuleId $salesModule -Name 'Tipo de cliente' -DataType LOOKUP -RelationColumnId $saleColumns.Client -SourceColumnId $clientColumns.Type
$saleColumns.Product = New-DemoColumn -ModuleId $salesModule -Name 'Producto' -DataType RELATION -Required -RelatedModuleId $productsModule -RelatedDisplayColumnId $productColumns.Name
$saleColumns.Sku = New-DemoColumn -ModuleId $salesModule -Name 'SKU' -DataType LOOKUP -RelationColumnId $saleColumns.Product -SourceColumnId $productColumns.Sku
$saleColumns.Category = New-DemoColumn -ModuleId $salesModule -Name 'Categoría' -DataType LOOKUP -RelationColumnId $saleColumns.Product -SourceColumnId $productColumns.Category
$saleColumns.Quantity = New-DemoColumn -ModuleId $salesModule -Name 'Cantidad' -DataType NUMBER -Required
$saleColumns.UnitPrice = New-DemoColumn -ModuleId $salesModule -Name 'Precio unitario' -DataType LOOKUP -RelationColumnId $saleColumns.Product -SourceColumnId $productColumns.Price
$saleColumns.Total = New-DemoColumn -ModuleId $salesModule -Name 'Total' -DataType FORMULA -FormulaExpression "{$($saleColumns.Quantity)}*{$($saleColumns.UnitPrice)}"
$saleColumns.Payment = New-DemoColumn -ModuleId $salesModule -Name 'Método de pago' -DataType SELECT -Options 'Efectivo,Tarjeta,Transferencia' -Required
$saleColumns.Status = New-DemoColumn -ModuleId $salesModule -Name 'Estado' -DataType SELECT -Options 'Pagada,Pendiente,Cancelada' -Required -DefaultValue 'Pagada'
$saleColumns.Delivered = New-DemoColumn -ModuleId $salesModule -Name 'Entregada' -DataType BOOLEAN -Required -DefaultValue 'true'

$purchaseColumns = [ordered]@{}
$purchaseColumns.Date = New-DemoColumn -ModuleId $purchasesModule -Name 'Fecha' -DataType DATE -Required
$purchaseColumns.Folio = New-DemoColumn -ModuleId $purchasesModule -Name 'Folio' -DataType TEXT -Required
$purchaseColumns.Supplier = New-DemoColumn -ModuleId $purchasesModule -Name 'Proveedor' -DataType RELATION -Required -RelatedModuleId $providersModule -RelatedDisplayColumnId $providerColumns.Company
$purchaseColumns.SupplierName = New-DemoColumn -ModuleId $purchasesModule -Name 'Nombre del proveedor' -DataType LOOKUP -RelationColumnId $purchaseColumns.Supplier -SourceColumnId $providerColumns.Company
$purchaseColumns.SupplierContact = New-DemoColumn -ModuleId $purchasesModule -Name 'Contacto del proveedor' -DataType LOOKUP -RelationColumnId $purchaseColumns.Supplier -SourceColumnId $providerColumns.Contact
$purchaseColumns.Product = New-DemoColumn -ModuleId $purchasesModule -Name 'Producto' -DataType RELATION -Required -RelatedModuleId $productsModule -RelatedDisplayColumnId $productColumns.Name
$purchaseColumns.Sku = New-DemoColumn -ModuleId $purchasesModule -Name 'SKU' -DataType LOOKUP -RelationColumnId $purchaseColumns.Product -SourceColumnId $productColumns.Sku
$purchaseColumns.Quantity = New-DemoColumn -ModuleId $purchasesModule -Name 'Cantidad' -DataType NUMBER -Required
$purchaseColumns.UnitCost = New-DemoColumn -ModuleId $purchasesModule -Name 'Costo unitario' -DataType NUMBER -Required
$purchaseColumns.Total = New-DemoColumn -ModuleId $purchasesModule -Name 'Total' -DataType FORMULA -FormulaExpression "{$($purchaseColumns.Quantity)}*{$($purchaseColumns.UnitCost)}"
$purchaseColumns.Status = New-DemoColumn -ModuleId $purchasesModule -Name 'Estado' -DataType SELECT -Options 'Solicitada,Recibida,Cancelada' -Required -DefaultValue 'Solicitada'
$purchaseColumns.Received = New-DemoColumn -ModuleId $purchasesModule -Name 'Recibida' -DataType BOOLEAN -Required -DefaultValue 'false'

Write-Host "4/12 Cargando proveedores, clientes y productos..."
$providers = [ordered]@{}
$providers.Center = Add-DemoRow -ModuleId $providersModule -Values @{
    $providerColumns.Company='Distribuidora Centro'; $providerColumns.Contact='Ana Morales'; $providerColumns.Phone='55 1000 2000'; $providerColumns.Email='ventas@distribuidoracentro.demo'; $providerColumns.Category='Abarrotes'; $providerColumns.Active='true'
}
$providers.Coffee = Add-DemoRow -ModuleId $providersModule -Values @{
    $providerColumns.Company='Café del Valle'; $providerColumns.Contact='Luis Herrera'; $providerColumns.Phone='55 2000 3000'; $providerColumns.Email='pedidos@cafedelvalle.demo'; $providerColumns.Category='Café'; $providerColumns.Active='true'
}
$providers.Stationery = Add-DemoRow -ModuleId $providersModule -Values @{
    $providerColumns.Company='Papelería Norte'; $providerColumns.Contact='Sofía Ruiz'; $providerColumns.Phone='55 3000 4000'; $providerColumns.Email='mayoreo@papelerianorte.demo'; $providerColumns.Category='Papelería'; $providerColumns.Active='true'
}
$providers.Cleaning = Add-DemoRow -ModuleId $providersModule -Values @{
    $providerColumns.Company='Limpieza Integral'; $providerColumns.Contact='Carlos Méndez'; $providerColumns.Phone='55 4000 5000'; $providerColumns.Email='contacto@limpiezaintegral.demo'; $providerColumns.Category='Limpieza'; $providerColumns.Active='true'
}

$clients = [ordered]@{}
$clients.Public = Add-DemoRow -ModuleId $clientsModule -Values @{
    $clientColumns.Name='Público general'; $clientColumns.Phone=''; $clientColumns.Email=''; $clientColumns.Type='Nuevo'; $clientColumns.CreditLimit='0'; $clientColumns.LastPurchase='2026-10-25'; $clientColumns.Active='true'
}
$clients.Maria = Add-DemoRow -ModuleId $clientsModule -Values @{
    $clientColumns.Name='María López'; $clientColumns.Phone='55 1111 2233'; $clientColumns.Email='maria.lopez@cliente.demo'; $clientColumns.Type='Frecuente'; $clientColumns.CreditLimit='1500'; $clientColumns.LastPurchase='2026-10-24'; $clientColumns.Active='true'
}
$clients.Cafe = Add-DemoRow -ModuleId $clientsModule -Values @{
    $clientColumns.Name='Cafetería Avenida'; $clientColumns.Phone='55 2222 3344'; $clientColumns.Email='compras@cafeteriaavenida.demo'; $clientColumns.Type='Empresa'; $clientColumns.CreditLimit='5000'; $clientColumns.LastPurchase='2026-10-22'; $clientColumns.Active='true'
}
$clients.Jorge = Add-DemoRow -ModuleId $clientsModule -Values @{
    $clientColumns.Name='Jorge Ramírez'; $clientColumns.Phone='55 3333 4455'; $clientColumns.Email='jorge.ramirez@cliente.demo'; $clientColumns.Type='Frecuente'; $clientColumns.CreditLimit='800'; $clientColumns.LastPurchase='2026-10-21'; $clientColumns.Active='true'
}
$clients.Office = Add-DemoRow -ModuleId $clientsModule -Values @{
    $clientColumns.Name='Oficina Creativa'; $clientColumns.Phone='55 4444 5566'; $clientColumns.Email='administracion@oficinacreativa.demo'; $clientColumns.Type='Empresa'; $clientColumns.CreditLimit='7500'; $clientColumns.LastPurchase='2026-10-20'; $clientColumns.Active='true'
}

$products = [ordered]@{}
$products.Water = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-001'; $productColumns.Name='Agua mineral 600 ml'; $productColumns.Category='Bebidas'; $productColumns.Supplier=[string]$providers.Center; $productColumns.Cost='8'; $productColumns.Price='14'; $productColumns.Stock='4'; $productColumns.Expiry='2027-02-15'; $productColumns.Active='true'
}
$products.Soda = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-002'; $productColumns.Name='Refresco cola 600 ml'; $productColumns.Category='Bebidas'; $productColumns.Supplier=[string]$providers.Center; $productColumns.Cost='13'; $productColumns.Price='22'; $productColumns.Stock='24'; $productColumns.Expiry='2027-03-10'; $productColumns.Active='true'
}
$products.Coffee = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-003'; $productColumns.Name='Café molido 500 g'; $productColumns.Category='Abarrotes'; $productColumns.Supplier=[string]$providers.Coffee; $productColumns.Cost='72'; $productColumns.Price='105'; $productColumns.Stock='3'; $productColumns.Expiry='2027-05-30'; $productColumns.Active='true'
}
$products.Notebook = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-004'; $productColumns.Name='Libreta profesional'; $productColumns.Category='Papelería'; $productColumns.Supplier=[string]$providers.Stationery; $productColumns.Cost='28'; $productColumns.Price='45'; $productColumns.Stock='18'; $productColumns.Expiry=''; $productColumns.Active='true'
}
$products.Pen = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-005'; $productColumns.Name='Pluma azul'; $productColumns.Category='Papelería'; $productColumns.Supplier=[string]$providers.Stationery; $productColumns.Cost='4.5'; $productColumns.Price='9'; $productColumns.Stock='2'; $productColumns.Expiry=''; $productColumns.Active='true'
}
$products.Cookies = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-006'; $productColumns.Name='Galletas de chocolate'; $productColumns.Category='Snacks'; $productColumns.Supplier=[string]$providers.Center; $productColumns.Cost='11'; $productColumns.Price='18'; $productColumns.Stock='35'; $productColumns.Expiry='2026-11-10'; $productColumns.Active='true'
}
$products.Milk = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-007'; $productColumns.Name='Leche entera 1 L'; $productColumns.Category='Lácteos'; $productColumns.Supplier=[string]$providers.Center; $productColumns.Cost='21.5'; $productColumns.Price='29'; $productColumns.Stock='8'; $productColumns.Expiry='2026-10-30'; $productColumns.Active='true'
}
$products.Soap = Add-DemoRow -ModuleId $productsModule -Values @{
    $productColumns.Sku='P-008'; $productColumns.Name='Jabón líquido'; $productColumns.Category='Limpieza'; $productColumns.Supplier=[string]$providers.Cleaning; $productColumns.Cost='30'; $productColumns.Price='49'; $productColumns.Stock='12'; $productColumns.Expiry='2027-08-01'; $productColumns.Active='true'
}

Write-Host "5/12 Cargando ventas y compras con datos coherentes..."
$saleData = @(
    @('2026-10-18','V-1001',$clients.Public,$products.Water,'2','Efectivo','Pagada','true'),
    @('2026-10-19','V-1002',$clients.Maria,$products.Coffee,'1','Tarjeta','Pagada','true'),
    @('2026-10-20','V-1003',$clients.Office,$products.Notebook,'15','Transferencia','Pendiente','false'),
    @('2026-10-21','V-1004',$clients.Jorge,$products.Pen,'4','Efectivo','Pagada','true'),
    @('2026-10-22','V-1005',$clients.Cafe,$products.Cookies,'12','Transferencia','Pagada','true'),
    @('2026-10-23','V-1006',$clients.Public,$products.Milk,'2','Efectivo','Pagada','true'),
    @('2026-10-24','V-1007',$clients.Maria,$products.Soap,'1','Tarjeta','Pagada','true'),
    @('2026-10-25','V-1008',$clients.Public,$products.Soda,'3','Efectivo','Cancelada','false')
)
foreach ($sale in $saleData) {
    [void](Add-DemoRow -ModuleId $salesModule -Values @{
        $saleColumns.Date=$sale[0]; $saleColumns.Folio=$sale[1]; $saleColumns.Client=[string]$sale[2]; $saleColumns.Product=[string]$sale[3]; $saleColumns.Quantity=$sale[4]; $saleColumns.Payment=$sale[5]; $saleColumns.Status=$sale[6]; $saleColumns.Delivered=$sale[7]
    })
}

$purchaseData = @(
    @('2026-10-16','C-2001',$providers.Center,$products.Water,'48','7.5','Recibida','true'),
    @('2026-10-17','C-2002',$providers.Coffee,$products.Coffee,'12','70','Solicitada','false'),
    @('2026-10-18','C-2003',$providers.Stationery,$products.Notebook,'40','27','Recibida','true'),
    @('2026-10-19','C-2004',$providers.Cleaning,$products.Soap,'20','29','Solicitada','false')
)
foreach ($purchase in $purchaseData) {
    [void](Add-DemoRow -ModuleId $purchasesModule -Values @{
        $purchaseColumns.Date=$purchase[0]; $purchaseColumns.Folio=$purchase[1]; $purchaseColumns.Supplier=[string]$purchase[2]; $purchaseColumns.Product=[string]$purchase[3]; $purchaseColumns.Quantity=$purchase[4]; $purchaseColumns.UnitCost=$purchase[5]; $purchaseColumns.Status=$purchase[6]; $purchaseColumns.Received=$purchase[7]
    })
}

Write-Host "6/12 Creando y configurando seis paneles operativos..."
$salePanel = New-DemoModule -Name '[DEMO] Caja - Registrar venta' -Description 'Formulario simplificado que crea una venta y calcula automáticamente datos heredados y total.' -Type PANEL -LinkedModuleId $salesModule -PanelAction REGISTER
$clientPanel = New-DemoModule -Name '[DEMO] Alta rápida de cliente' -Description 'Formulario para capturar clientes sin abrir directamente la tabla.' -Type PANEL -LinkedModuleId $clientsModule -PanelAction REGISTER
$purchasePanel = New-DemoModule -Name '[DEMO] Registrar compra' -Description 'Formulario de compras con proveedor y producto relacionados.' -Type PANEL -LinkedModuleId $purchasesModule -PanelAction REGISTER
$subtractPanel = New-DemoModule -Name '[DEMO] Venta rápida - Descontar stock' -Description 'Resta existencias sin borrar el artículo y conserva movimiento e historial.' -Type PANEL -LinkedModuleId $productsModule -PanelAction ADJUST
$addPanel = New-DemoModule -Name '[DEMO] Recepción - Sumar stock' -Description 'Suma existencias al recibir mercancía y registra la trazabilidad.' -Type PANEL -LinkedModuleId $productsModule -PanelAction ADJUST
$setPanel = New-DemoModule -Name '[DEMO] Conteo físico - Fijar stock' -Description 'Reemplaza la existencia con la cantidad contada físicamente.' -Type PANEL -LinkedModuleId $productsModule -PanelAction ADJUST

Set-PanelSettings -PanelId $salePanel -LinkedModuleId $salesModule -PanelAction REGISTER
Set-PanelFields -PanelId $salePanel `
    -VisibleColumnIds @($saleColumns.Date,$saleColumns.Folio,$saleColumns.Client,$saleColumns.Product,$saleColumns.Quantity,$saleColumns.Payment,$saleColumns.Status,$saleColumns.Delivered) `
    -RequiredColumnIds @($saleColumns.Date,$saleColumns.Folio,$saleColumns.Product,$saleColumns.Quantity,$saleColumns.Payment,$saleColumns.Status,$saleColumns.Delivered)

Set-PanelSettings -PanelId $clientPanel -LinkedModuleId $clientsModule -PanelAction REGISTER
Set-PanelFields -PanelId $clientPanel `
    -VisibleColumnIds @($clientColumns.Name,$clientColumns.Phone,$clientColumns.Email,$clientColumns.Type,$clientColumns.CreditLimit,$clientColumns.LastPurchase,$clientColumns.Active) `
    -RequiredColumnIds @($clientColumns.Name,$clientColumns.Type,$clientColumns.Active)

Set-PanelSettings -PanelId $purchasePanel -LinkedModuleId $purchasesModule -PanelAction REGISTER
Set-PanelFields -PanelId $purchasePanel `
    -VisibleColumnIds @($purchaseColumns.Date,$purchaseColumns.Folio,$purchaseColumns.Supplier,$purchaseColumns.Product,$purchaseColumns.Quantity,$purchaseColumns.UnitCost,$purchaseColumns.Status,$purchaseColumns.Received) `
    -RequiredColumnIds @($purchaseColumns.Date,$purchaseColumns.Folio,$purchaseColumns.Supplier,$purchaseColumns.Product,$purchaseColumns.Quantity,$purchaseColumns.UnitCost,$purchaseColumns.Status,$purchaseColumns.Received)

Set-PanelSettings -PanelId $subtractPanel -LinkedModuleId $productsModule -PanelAction ADJUST -AdjustmentColumnId $productColumns.Stock -AdjustmentOperation SUBTRACT
Set-PanelSettings -PanelId $addPanel -LinkedModuleId $productsModule -PanelAction ADJUST -AdjustmentColumnId $productColumns.Stock -AdjustmentOperation ADD
Set-PanelSettings -PanelId $setPanel -LinkedModuleId $productsModule -PanelAction ADJUST -AdjustmentColumnId $productColumns.Stock -AdjustmentOperation SET

Write-Host "7/12 Ejecutando ejemplos reales desde paneles e inventario..."
[void](Add-PanelRow -PanelId $salePanel -TargetModuleId $salesModule -Values @{
    $saleColumns.Date='2026-10-26'; $saleColumns.Folio='V-1009'; $saleColumns.Client=[string]$clients.Maria; $saleColumns.Product=[string]$products.Coffee; $saleColumns.Quantity='2'; $saleColumns.Payment='Tarjeta'; $saleColumns.Status='Pagada'; $saleColumns.Delivered='true'
})
[void](Add-PanelRow -PanelId $purchasePanel -TargetModuleId $purchasesModule -Values @{
    $purchaseColumns.Date='2026-10-26'; $purchaseColumns.Folio='C-2005'; $purchaseColumns.Supplier=[string]$providers.Center; $purchaseColumns.Product=[string]$products.Milk; $purchaseColumns.Quantity='24'; $purchaseColumns.UnitCost='20.5'; $purchaseColumns.Status='Solicitada'; $purchaseColumns.Received='false'
})
Invoke-StockAdjustment -PanelId $subtractPanel -RowId $products.Soda -Quantity 2 -Note 'Ejemplo de salida por venta de mostrador.'
Invoke-StockAdjustment -PanelId $addPanel -RowId $products.Water -Quantity 10 -Note 'Ejemplo de recepción de proveedor.'
Invoke-StockAdjustment -PanelId $setPanel -RowId $products.Pen -Quantity 6 -Note 'Ejemplo de conteo físico y corrección de inventario.'

Write-Host "8/12 Guardando vistas filtradas y ordenadas..."
New-SavedView -ModuleId $guideModule -Name '[DEMO] Recorrido en orden' -SortColumnId $guideColumns.Order -SortDirection ASC -DefaultView
New-SavedView -ModuleId $productsModule -Name '[DEMO] Stock crítico' -SortColumnId $productColumns.Stock -SortDirection ASC -FilterColumnId $productColumns.Stock -FilterOperator LTE -FilterValue '5'
New-SavedView -ModuleId $productsModule -Name '[DEMO] Catálogo activo' -SortColumnId $productColumns.Name -SortDirection ASC -FilterColumnId $productColumns.Active -FilterOperator EQ -FilterValue 'true'
New-SavedView -ModuleId $salesModule -Name '[DEMO] Ventas pendientes' -SortColumnId $saleColumns.Date -SortDirection DESC -FilterColumnId $saleColumns.Status -FilterOperator EQ -FilterValue 'Pendiente'
New-SavedView -ModuleId $salesModule -Name '[DEMO] Ventas de octubre' -SortColumnId $saleColumns.Date -SortDirection DESC -FilterColumnId $saleColumns.Date -FilterOperator BETWEEN -FilterValue '2026-10-01' -FilterSecondValue '2026-10-31'
New-SavedView -ModuleId $purchasesModule -Name '[DEMO] Compras por recibir' -SortColumnId $purchaseColumns.Date -SortDirection DESC -FilterColumnId $purchaseColumns.Received -FilterOperator EQ -FilterValue 'false'

Write-Host "9/12 Preparando reportes y alertas..."
New-SavedReport -ModuleId $salesModule -Name '[DEMO] Ingresos totales' -ReportType SUMMARY -AggregateFunction SUM -ValueColumnId $saleColumns.Total
New-SavedReport -ModuleId $salesModule -Name '[DEMO] Ventas por categoría' -ReportType CHART -AggregateFunction SUM -ValueColumnId $saleColumns.Total -GroupByColumnId $saleColumns.Category -ChartType BAR
New-SavedReport -ModuleId $salesModule -Name '[DEMO] Ventas por método de pago' -ReportType CHART -AggregateFunction SUM -ValueColumnId $saleColumns.Total -GroupByColumnId $saleColumns.Payment -ChartType PIE
New-SavedReport -ModuleId $salesModule -Name '[DEMO] Ticket promedio' -ReportType SUMMARY -AggregateFunction AVG -ValueColumnId $saleColumns.Total
New-SavedReport -ModuleId $productsModule -Name '[DEMO] Valor actual del inventario' -ReportType SUMMARY -AggregateFunction SUM -ValueColumnId $productColumns.InventoryValue
New-SavedReport -ModuleId $purchasesModule -Name '[DEMO] Compras por proveedor' -ReportType CHART -AggregateFunction SUM -ValueColumnId $purchaseColumns.Total -GroupByColumnId $purchaseColumns.SupplierName -ChartType BAR
New-SavedReport -ModuleId $clientsModule -Name '[DEMO] Total de clientes' -ReportType SUMMARY -AggregateFunction COUNT
New-SavedReport -ModuleId $salesModule -Name '[DEMO] Listado de ventas' -ReportType TABLE -AggregateFunction COUNT

New-AlertRule -Name '[DEMO] Stock crítico' -ColumnId $productColumns.Stock -Operator LTE -ComparisonValue '5' -Message '{module}: el registro #{rowId} tiene solo {value} unidades.'
New-AlertRule -Name '[DEMO] Próxima caducidad' -ColumnId $productColumns.Expiry -Operator BEFORE -ComparisonValue '2026-11-01' -Message '{module}: revisa la caducidad {value} del registro #{rowId}.'
New-AlertRule -Name '[DEMO] Venta pendiente' -ColumnId $saleColumns.Status -Operator EQ -ComparisonValue 'Pendiente' -Message '{module}: la venta #{rowId} continúa pendiente.'
New-AlertRule -Name '[DEMO] Venta mayorista' -ColumnId $saleColumns.Total -Operator GTE -ComparisonValue '500' -Message '{module}: la venta #{rowId} supera $500; total {value}.'

Write-Host "10/12 Creando roles de ejemplo y una matriz de permisos..."
$managerEmail = 'gerencia.demo@migestor.local'
$cashierEmail = 'caja.demo@migestor.local'
$managerUserId = New-DemoUser -Name '[DEMO] Gerencia' -Email $managerEmail -Password $ManagerPassword -Role MANAGER
$cashierUserId = New-DemoUser -Name '[DEMO] Caja' -Email $cashierEmail -Password $CashierPassword -Role EMPLOYEE

$cashierPermissions = @{}
$cashierPermissions[$guideModule] = @()
$cashierPermissions[$providersModule] = @()
$cashierPermissions[$clientsModule] = @('CREATE')
$cashierPermissions[$productsModule] = @()
$cashierPermissions[$salesModule] = @()
$cashierPermissions[$purchasesModule] = @()
$cashierPermissions[$salePanel] = @('CREATE')
$cashierPermissions[$clientPanel] = @('CREATE')
$cashierPermissions[$purchasePanel] = @('CREATE')
$cashierPermissions[$subtractPanel] = @('EDIT')
$cashierPermissions[$addPanel] = @('EDIT')
$cashierPermissions[$setPanel] = @('EDIT')
Set-CashierPermissions -UserId $cashierUserId -Permissions $cashierPermissions

# Se muestran en la administración de usuarios, pero se dejan inactivos para no abrir accesos sin aprobación.
Disable-DemoUser -UserId $managerUserId -Name '[DEMO] Gerencia' -Email $managerEmail -Role MANAGER
Disable-DemoUser -UserId $cashierUserId -Name '[DEMO] Caja' -Email $cashierEmail -Role EMPLOYEE

Write-Host "11/12 Llenando la guía de recorrido..."
$guideRows = @(
    @('1','Inicio','Panel principal','Revisa las tarjetas de resumen y abre primero esta guía.','Ejemplo cargado'),
    @('2','Módulos','[DEMO] Productos e inventario','Observa una tabla editable con ID automático, distintos tipos de columna y filas reales.','Ejemplo cargado'),
    @('3','Datos','[DEMO] Clientes','Crea, edita, busca o elimina una fila de prueba; el ID no se captura manualmente.','Listo para probar'),
    @('4','Relaciones','[DEMO] Productos e inventario','Proveedor relaciona otra tabla y Contacto del proveedor hereda el dato mediante LOOKUP.','Ejemplo cargado'),
    @('5','Fórmulas','[DEMO] Productos e inventario','Margen % y Valor del inventario se calculan sin guardar datos duplicados.','Ejemplo cargado'),
    @('6','Paneles','[DEMO] Caja - Registrar venta','Captura una venta simplificada. Después usa Venta rápida - Descontar stock: hoy son dos operaciones separadas.','Listo para probar'),
    @('7','Paneles','[DEMO] Recepción - Sumar stock','Prueba sumar, restar o fijar existencias; cada ajuste conserva movimiento e historial.','Ejemplo cargado'),
    @('8','Vistas','[DEMO] Productos e inventario','Selecciona Stock crítico o Catálogo activo para aplicar filtros y orden guardados.','Ejemplo cargado'),
    @('9','Reportes','Menú Reportes','Selecciona una tabla para ver sus resúmenes y revisa las definiciones guardadas de esta demo.','Ejemplo cargado'),
    @('10','Alertas','Menú Alertas','Revisa coincidencias de stock, caducidad, ventas pendientes y ventas mayores a $500.','Ejemplo cargado'),
    @('11','Usuarios','Usuarios y permisos','Compara propietario, gerente y empleado. Los dos usuarios demo están inactivos por seguridad.','Ejemplo cargado'),
    @('12','Importar y exportar','[DEMO] Clientes o Productos','Usa Importar para CSV/XLSX y Exportar para descargar CSV/XLSX; los datos demo sirven como muestra.','Listo para probar'),
    @('13','Historial','Menú Historial','Consulta altas creadas desde paneles y los tres movimientos de inventario ya ejecutados.','Ejemplo cargado'),
    @('14','Respaldo','Menú Respaldo','Como propietario puedes descargar un ZIP completo del negocio antes de cambios importantes.','Listo para probar')
)
foreach ($item in $guideRows) {
    [void](Add-DemoRow -ModuleId $guideModule -Values @{
        $guideColumns.Order=$item[0]; $guideColumns.Function=$item[1]; $guideColumns.Where=$item[2]; $guideColumns.Instruction=$item[3]; $guideColumns.Status=$item[4]
    })
}

Write-Host "12/12 Verificando aislamiento, recorridos y totales..."
$expectedDemoModules = 12
$demoModuleCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM custom_modules WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%'")
$demoTableCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM custom_modules WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%' AND module_type='TABLE'")
$demoPanelCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM custom_modules WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%' AND module_type='PANEL'")
$demoRowCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM module_rows r JOIN custom_modules m ON m.id=r.module_id AND m.business_id=r.business_id WHERE r.business_id=$script:BusinessId AND m.name LIKE '[DEMO]%'")
$demoViewCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM saved_views v JOIN custom_modules m ON m.id=v.module_id AND m.business_id=v.business_id WHERE v.business_id=$script:BusinessId AND v.name LIKE '[DEMO]%' AND m.name LIKE '[DEMO]%'")
$demoReportCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM report_definitions WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%'")
$demoAlertCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM alert_rules WHERE business_id=$script:BusinessId AND name LIKE '[DEMO]%'")
$movementCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM inventory_movements im JOIN custom_modules p ON p.id=im.source_panel_id AND p.business_id=im.business_id WHERE im.business_id=$script:BusinessId AND p.name LIKE '[DEMO]%'")
$completedRuns = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM operation_runs o JOIN custom_modules p ON p.id=o.panel_module_id AND p.business_id=o.business_id WHERE o.business_id=$script:BusinessId AND p.name LIKE '[DEMO]%' AND o.status='COMPLETED'")
$permissionCount = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM module_permissions p JOIN custom_modules m ON m.id=p.module_id AND m.business_id=p.business_id WHERE p.business_id=$script:BusinessId AND p.user_id=$cashierUserId AND p.can_view=1 AND m.name LIKE '[DEMO]%'")
$activeDemoUsers = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM users WHERE business_id=$script:BusinessId AND email IN ('$managerEmail','$cashierEmail') AND active=1")
$originalModulesAfter = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM custom_modules WHERE business_id=$script:BusinessId AND name NOT LIKE '[DEMO]%'")
$originalRowsAfter = [long](Get-SqlScalar -Sql "SELECT COUNT(*) FROM module_rows r JOIN custom_modules m ON m.id=r.module_id AND m.business_id=r.business_id WHERE r.business_id=$script:BusinessId AND m.name NOT LIKE '[DEMO]%'")
$brokenScope = [long](Get-SqlScalar -Sql "SELECT (SELECT COUNT(*) FROM module_rows r JOIN custom_modules m ON m.id=r.module_id WHERE r.business_id<>m.business_id) + (SELECT COUNT(*) FROM cell_values c JOIN module_rows r ON r.id=c.row_id WHERE c.business_id<>r.business_id) + (SELECT COUNT(*) FROM module_columns c JOIN custom_modules m ON m.id=c.module_id WHERE c.business_id<>m.business_id)")

if ($demoModuleCount -ne $expectedDemoModules -or $demoTableCount -ne 6 -or $demoPanelCount -ne 6) { throw "La cantidad de módulos demo no coincide con el diseño." }
if ($demoRowCount -lt 40) { throw "Faltan filas de demostración; solo se encontraron $demoRowCount." }
if ($demoViewCount -ne 6 -or $demoReportCount -ne 8 -or $demoAlertCount -ne 4) { throw "Faltan vistas, reportes o alertas de demostración." }
if ($movementCount -ne 3 -or $completedRuns -ne 3) { throw "No se generaron correctamente los movimientos de inventario." }
if ($permissionCount -ne 12 -or $activeDemoUsers -ne 0) { throw "La matriz o el estado seguro de usuarios demo no quedó como se esperaba." }
if ($originalModulesAfter -ne $script:OriginalModuleCount -or $originalRowsAfter -ne $script:OriginalRowCount) { throw "Cambió contenido anterior que debía conservarse." }
if ($brokenScope -ne 0) { throw "La comprobación de aislamiento encontró $brokenScope referencias cruzadas." }

$pathsToCheck = @(
    '/app/dashboard',
    "/app/modules/$guideModule",
    "/app/modules/$productsModule",
    "/app/modules/$salePanel",
    "/app/modules/$subtractPanel",
    '/app/reports',
    '/app/alerts',
    '/app/history',
    '/app/settings/users'
)
foreach ($path in $pathsToCheck) { [void](Assert-AppPage -Path $path) }

Write-Host ""
Write-Host "Demostración creada correctamente."
Write-Host "Negocio: $script:BusinessId"
Write-Host "Módulos: $demoModuleCount (6 tablas y 6 paneles)"
Write-Host "Filas demo: $demoRowCount"
Write-Host "Vistas: $demoViewCount | Reportes: $demoReportCount | Alertas: $demoAlertCount"
Write-Host "Movimientos de inventario: $movementCount | Usuarios demo inactivos: 2"
Write-Host "Inicio del recorrido: $BaseUrl/app/modules/$guideModule"
