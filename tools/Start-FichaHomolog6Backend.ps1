#requires -Version 7.0
<#
.SYNOPSIS
Inicialização isolada e EXPLÍCITA para testes autorizados de rascunhos no .6.
.DESCRIPTION
Não faz build, migração, SELECT de préflight ou envio ao Linx. Exige JAR já gerado
e credencial PSCredential DPAPI do mesmo usuário Windows. Não importa .env/config
externa e não muda o ambiente persistente, frontend ou backend normal.
Autenticação/roles usam só H2 file local próprio em target/homolog6-test/auth;
não há conexão com banco primário normal. Esse artefato de teste é preservado.
Permanece em foreground para manter os streams/logs vivos; use uma sessão de
terminal/exec. Outra chamada com -Restart encerra apenas o java identificado por
PID + starttime + caminho + command line EXATOS deste launcher.
#>
[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 18081,
    [string]$CredentialPath = 'C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml',
    [string]$JavaPath = 'C:\Program Files\Java\jdk-21.0.11\bin\java.exe',
    [switch]$Restart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
throw 'Launcher de persistência central desativado: rascunho e histórico ficam somente no navegador. Nenhuma credencial, processo ou conexão foi utilizado.'
$taskStage = 'validação local'
$taskProcess = $null
$taskStartInfo = $null
$taskOutStream = $null
$taskErrStream = $null
$taskOutCopy = $null
$taskErrCopy = $null
$taskPlainPassword = $null
$taskJwtSecret = $null
$taskCredential = $null
$taskStartedByThisInvocation = $false

function Assert-OrdinaryPath {
    param([string]$LiteralPath, [switch]$File)
    $taskItem = Get-Item -LiteralPath $LiteralPath -Force
    if (($taskItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Links/reparse points não são aceitos nos alvos deste launcher.'
    }
    if ($File -and $taskItem.PSIsContainer) { throw 'Um arquivo regular era esperado.' }
    return $taskItem.FullName
}

function Test-SamePath {
    param([string]$Left, [string]$Right)
    return [StringComparer]::OrdinalIgnoreCase.Equals([IO.Path]::GetFullPath($Left), [IO.Path]::GetFullPath($Right))
}

try {
    $taskProject = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    [void](Assert-OrdinaryPath -LiteralPath $taskProject)
    [void](Assert-OrdinaryPath -LiteralPath (Join-Path $taskProject 'target'))
    $taskJarPath = Assert-OrdinaryPath -File -LiteralPath (Join-Path $taskProject 'target\modaflow-backend-1.0.0-SNAPSHOT.jar')
    $taskSourceConfig = Assert-OrdinaryPath -File -LiteralPath (Join-Path $taskProject 'src\main\resources\application-ficha-homolog6-test.yml')
    $taskJavaPath = Assert-OrdinaryPath -File -LiteralPath $JavaPath
    if ([IO.Path]::GetFileName($taskJavaPath) -ine 'java.exe') { throw 'Use um java.exe regular explícito.' }

    $taskAuthDirectory = [IO.Path]::GetFullPath((Join-Path $taskProject 'target\homolog6-test'))
    if (-not $taskAuthDirectory.StartsWith($taskProject + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'O H2 de autenticação precisa permanecer no target do workspace.'
    }
    if (-not (Test-Path -LiteralPath $taskAuthDirectory)) { [void][IO.Directory]::CreateDirectory($taskAuthDirectory) }
    [void](Assert-OrdinaryPath -LiteralPath $taskAuthDirectory)
    $taskAuthStem = Join-Path $taskAuthDirectory 'auth'
    $taskAuthOwnerPath = Join-Path $taskAuthDirectory 'launcher-owner.json'
    $taskAuthFiles = @(Get-ChildItem -LiteralPath $taskAuthDirectory -Filter 'auth*' -Force)
    foreach ($taskAuthFile in $taskAuthFiles) { [void](Assert-OrdinaryPath -File -LiteralPath $taskAuthFile.FullName) }
    if (Test-Path -LiteralPath $taskAuthOwnerPath) {
        [void](Assert-OrdinaryPath -File -LiteralPath $taskAuthOwnerPath)
        if ((Get-Item -LiteralPath $taskAuthOwnerPath).Length -gt 8192) { throw 'Marker local H2 inválido.' }
        $taskAuthOwner = Get-Content -LiteralPath $taskAuthOwnerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($taskAuthOwner.Launcher -cne 'ModaFlow-Ficha-Homolog6-Isolated-v1' -or
            -not (Test-SamePath $taskAuthOwner.ProjectPath $taskProject) -or
            -not (Test-SamePath $taskAuthOwner.AuthFileStem $taskAuthStem)) {
            throw 'O arquivo H2 existente não foi identificado como fixture deste launcher.'
        }
    } elseif ($taskAuthFiles.Count -ne 0) {
        throw 'Há arquivos auth preexistentes sem marker próprio; preserve-os, não serão reutilizados.'
    } else {
        $taskAuthMarker = [ordered]@{ Launcher = 'ModaFlow-Ficha-Homolog6-Isolated-v1'; ProjectPath = $taskProject;
            AuthFileStem = $taskAuthStem; Purpose = 'Local H2 dev authentication fixtures, no production data';
            CreatedUtc = [DateTime]::UtcNow.ToString('o') }
        [IO.File]::WriteAllText($taskAuthOwnerPath, ($taskAuthMarker | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    }
    $taskAuthJdbcUrl = 'jdbc:h2:file:' + $taskAuthStem.Replace('\', '/') + ';AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0'

    # Rejeita JAR antigo ou config diferente. Nenhuma configuração externa é lida.
    $taskStage = 'conferência do JAR isolado'
    $taskZip = [IO.Compression.ZipFile]::OpenRead($taskJarPath)
    $taskEntryStream = $null
    $taskHasher = $null
    try {
        $taskEntry = $taskZip.GetEntry('BOOT-INF/classes/application-ficha-homolog6-test.yml')
        if ($null -eq $taskEntry -or $null -eq $taskZip.GetEntry('BOOT-INF/classes/com/modaflow/backend/ficha/persistence/FichaDraftController.class')) {
            throw 'O JAR não contém configuração/controller de teste isolado. Gere o pacote aprovado antes.'
        }
        $taskEntryStream = $taskEntry.Open()
        $taskHasher = [Security.Cryptography.SHA256]::Create()
        $taskPackagedConfigHash = [Convert]::ToHexString($taskHasher.ComputeHash($taskEntryStream))
        $taskSourceConfigHash = (Get-FileHash -LiteralPath $taskSourceConfig -Algorithm SHA256).Hash
        if ($taskPackagedConfigHash -cne $taskSourceConfigHash) { throw 'Configuração empacotada desatualizada.' }
    } finally {
        if ($null -ne $taskEntryStream) { $taskEntryStream.Dispose() }
        if ($null -ne $taskHasher) { $taskHasher.Dispose() }
        $taskZip.Dispose()
    }

    $taskLogsRoot = Join-Path $taskProject 'logs'
    if (-not (Test-Path -LiteralPath $taskLogsRoot)) { [void][IO.Directory]::CreateDirectory($taskLogsRoot) }
    [void](Assert-OrdinaryPath -LiteralPath $taskLogsRoot)
    $taskLogs = [IO.Path]::GetFullPath((Join-Path $taskLogsRoot 'ficha-homolog6-test'))
    if (-not $taskLogs.StartsWith($taskProject + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'O diretório de logs precisa permanecer no workspace do backend.'
    }
    if (-not (Test-Path -LiteralPath $taskLogs)) { [void][IO.Directory]::CreateDirectory($taskLogs) }
    [void](Assert-OrdinaryPath -LiteralPath $taskLogs)
    $taskStatePath = Join-Path $taskLogs 'backend-state.json'

    # Nenhuma busca por java.exe/porta para matar processos genéricos.
    $taskStage = 'conferência de processo anterior'
    if (Test-Path -LiteralPath $taskStatePath) {
        [void](Assert-OrdinaryPath -File -LiteralPath $taskStatePath)
        if ((Get-Item -LiteralPath $taskStatePath).Length -gt 8192) { throw 'Estado anterior inválido; preserve-o para inspeção.' }
        $taskPrevious = Get-Content -LiteralPath $taskStatePath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($taskPrevious.Launcher -cne 'ModaFlow-Ficha-Homolog6-Isolated-v1' -or
            -not (Test-SamePath $taskPrevious.JarPath $taskJarPath) -or
            -not (Test-SamePath $taskPrevious.JavaExecutable $taskJavaPath) -or
            [int]$taskPrevious.Pid -le 0) {
            throw 'Estado anterior não pertence a este launcher; nenhum processo será encerrado.'
        }
        $taskPreviousProcess = Get-Process -Id ([int]$taskPrevious.Pid) -ErrorAction SilentlyContinue
        if ($null -ne $taskPreviousProcess) {
            try {
                $taskPreviousCim = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + [int]$taskPrevious.Pid)
                $taskActualStart = $taskPreviousProcess.StartTime.ToUniversalTime().ToString('o')
                # PS 7.6 pode converter ISO JSON para DateTime automaticamente.
                # Normalizar UTC sem perder os ticks; jamais comparar data em formato regional.
                $taskStoredStart = if ($taskPrevious.ProcessStartedUtc -is [DateTime]) {
                    $taskPrevious.ProcessStartedUtc.ToUniversalTime().ToString('o')
                } else {
                    [DateTime]::Parse([string]$taskPrevious.ProcessStartedUtc,
                        [Globalization.CultureInfo]::InvariantCulture,
                        [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime().ToString('o')
                }
                $taskExpectedCommand = [string]$taskPrevious.CommandLine
                if ($null -eq $taskPreviousCim -or
                    $taskActualStart -cne $taskStoredStart -or
                    -not (Test-SamePath $taskPreviousProcess.Path $taskJavaPath) -or
                    [string]$taskPreviousCim.CommandLine -cne $taskExpectedCommand -or
                    -not $taskExpectedCommand.Contains('--spring.config.location=classpath:/application-ficha-homolog6-test.yml') -or
                    -not $taskExpectedCommand.Contains('--spring.profiles.active=dev,ficha-homolog6-test') -or
                    -not $taskExpectedCommand.Contains('--spring.datasource.url=' + $taskAuthJdbcUrl) -or
                    -not $taskExpectedCommand.Contains('--server.address=127.0.0.1') -or
                    -not $taskExpectedCommand.Contains('--modaflow.linx.enabled=false') -or
                    -not $taskExpectedCommand.Contains('--modaflow.ficha.persistence.enabled=true') -or
                    $taskExpectedCommand.IndexOf($taskJarPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
                    throw 'Identidade do processo anterior divergente. Não será encerrado.'
                }
                if (-not $Restart) { throw 'Este backend de teste já está em execução; use -Restart explicitamente.' }
                $taskPreviousProcess.Kill()
                if (-not $taskPreviousProcess.WaitForExit(30000)) { throw 'O processo validado não encerrou no prazo.' }
            } finally { $taskPreviousProcess.Dispose() }
        }
    }
    if (@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) {
        throw 'A porta solicitada está ocupada. Nenhum processo diferente será encerrado.'
    }

    $taskStage = 'credencial protegida'
    [void](Assert-OrdinaryPath -File -LiteralPath $CredentialPath)
    $taskCredential = Import-Clixml -LiteralPath $CredentialPath
    if ($taskCredential -isnot [Management.Automation.PSCredential] -or [string]::IsNullOrWhiteSpace($taskCredential.UserName)) {
        throw 'O arquivo precisa conter uma PSCredential DPAPI do mesmo usuário Windows.'
    }
    # JDBC exige String; ela existe apenas em memória, jamais em args/arquivo/log.
    $taskPlainPassword = $taskCredential.GetNetworkCredential().Password
    if ([string]::IsNullOrEmpty($taskPlainPassword)) { throw 'Credencial SQL vazia.' }
    $taskJwtBytes = [byte[]]::new(64)
    $taskRng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRng.GetBytes($taskJwtBytes) }
    finally { $taskRng.Dispose() }
    $taskJwtSecret = [Convert]::ToBase64String($taskJwtBytes)
    [Array]::Clear($taskJwtBytes, 0, $taskJwtBytes.Length)

    $taskRunId = [Guid]::NewGuid().ToString('N')
    $taskOutPath = Join-Path $taskLogs ('backend-' + $taskRunId + '.stdout.log')
    $taskErrPath = Join-Path $taskLogs ('backend-' + $taskRunId + '.stderr.log')
    $taskOutStream = [IO.FileStream]::new($taskOutPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    $taskErrStream = [IO.FileStream]::new($taskErrPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)

    $taskStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $taskStartInfo.FileName = $taskJavaPath
    $taskStartInfo.WorkingDirectory = $taskProject
    $taskStartInfo.UseShellExecute = $false
    $taskStartInfo.CreateNoWindow = $true
    $taskStartInfo.RedirectStandardOutput = $true
    $taskStartInfo.RedirectStandardError = $true

    # Allowlist de variáveis OS: exclui SPRING_APPLICATION_JSON, configs/profiles,
    # JPA/Linx herdados, JavaToolOptions e quaisquer credenciais de outros serviços.
    $taskStartInfo.Environment.Clear()
    foreach ($taskName in @('SystemRoot','windir','SystemDrive','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA',
            'ProgramData','ProgramFiles','ProgramFiles(x86)','CommonProgramFiles','CommonProgramFiles(x86)',
            'Path','PATHEXT','ComSpec','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE','OS','COMPUTERNAME','USERNAME','USERDOMAIN')) {
        $taskValue = [Environment]::GetEnvironmentVariable($taskName, [EnvironmentVariableTarget]::Process)
        if ($null -ne $taskValue) { $taskStartInfo.Environment[$taskName] = $taskValue }
    }
    $taskStartInfo.Environment['MODAFLOW_FICHA_TEST_DATASOURCE_USERNAME'] = $taskCredential.UserName
    $taskStartInfo.Environment['MODAFLOW_FICHA_TEST_DATASOURCE_PASSWORD'] = $taskPlainPassword
    $taskStartInfo.Environment['MODAFLOW_FICHA_TEST_JWT_SECRET'] = $taskJwtSecret
    $taskStartInfo.Environment['MODAFLOW_FICHA_TEST_AUTH_JDBC_URL'] = $taskAuthJdbcUrl

    # ArgumentList evita shell/quoting construído. Nenhum argumento contém segredo.
    foreach ($taskArgument in @('-jar', $taskJarPath,
        '--spring.config.location=classpath:/application-ficha-homolog6-test.yml',
        '--spring.config.additional-location=', '--spring.config.import=',
        '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration',
        '--spring.profiles.active=dev,ficha-homolog6-test', '--spring.profiles.default=',
        '--server.address=127.0.0.1', ('--server.port=' + $Port), '--server.servlet.context-path=/api/v1',
        ('--spring.datasource.url=' + $taskAuthJdbcUrl),
        '--spring.datasource.driver-class-name=org.h2.Driver', '--spring.datasource.username=sa', '--spring.datasource.password=',
        '--spring.datasource.type=com.zaxxer.hikari.HikariDataSource',
        '--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect', '--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect',
        '--spring.jpa.hibernate.ddl-auto=update', '--spring.jpa.properties.hibernate.hbm2ddl.import_files=',
        '--spring.jpa.show-sql=false', '--spring.sql.init.mode=never', '--spring.h2.console.enabled=false',
        '--spring.flyway.enabled=false', '--spring.liquibase.enabled=false',
        '--modaflow.linx.enabled=false', '--modaflow.ficha.persistence.enabled=true',
        '--modaflow.ficha.persistence.maximum-document-bytes=1048576',
        '--modaflow.ficha.persistence.datasource.url=jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true;trustServerCertificate=true',
        '--modaflow.ficha.persistence.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver',
        '--modaflow.jwt.expiration-ms=3600000', '--logging.level.root=INFO', '--logging.level.com.zaxxer.hikari=WARN',
        '--logging.level.com.microsoft.sqlserver=WARN', '--logging.level.org.hibernate.SQL=WARN', '--logging.level.org.hibernate.orm.jdbc.bind=WARN')) {
        $taskStartInfo.ArgumentList.Add($taskArgument)
    }

    $taskStage = 'inicialização isolada'
    $taskProcess = [Diagnostics.Process]::new()
    $taskProcess.StartInfo = $taskStartInfo
    if (-not $taskProcess.Start()) { throw 'Java não iniciou.' }
    $taskStartedByThisInvocation = $true
    $taskOutCopy = $taskProcess.StandardOutput.BaseStream.CopyToAsync($taskOutStream)
    $taskErrCopy = $taskProcess.StandardError.BaseStream.CopyToAsync($taskErrStream)
    $taskStartInfo.Environment.Clear()
    $taskPlainPassword = $null
    $taskJwtSecret = $null
    $taskCredential = $null

    $taskCim = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $taskProcess.Id)
    if ($null -eq $taskCim -or [string]::IsNullOrWhiteSpace($taskCim.CommandLine)) { throw 'Metadados do processo não puderam ser confirmados.' }
    $taskState = [ordered]@{
        Launcher = 'ModaFlow-Ficha-Homolog6-Isolated-v1'
        Pid = $taskProcess.Id
        ProcessStartedUtc = $taskProcess.StartTime.ToUniversalTime().ToString('o')
        JavaExecutable = $taskJavaPath
        JarPath = $taskJarPath
        JarSha256 = (Get-FileHash -LiteralPath $taskJarPath -Algorithm SHA256).Hash
        CommandLine = [string]$taskCim.CommandLine
        Port = $Port
        ApiBaseUrl = ('http://127.0.0.1:' + $Port + '/api/v1')
        PrimaryDatabase = 'H2 local isolated file (auth dev fixtures only)'
        AuthFileStem = $taskAuthStem
        DraftTarget = '192.168.1.6:1433/HOMOLOG_COBRANCA/modaflow'
        LinxEnabled = $false
        StdoutLog = $taskOutPath
        StderrLog = $taskErrPath
    }
    [IO.File]::WriteAllText($taskStatePath, ($taskState | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    # Status lançado NÃO afirma API pronta nem comprova gravação real no banco.
    [pscustomobject]@{ Status = 'LaunchedNotReady'; Pid = $taskState.Pid; ProcessStartedUtc = $taskState.ProcessStartedUtc;
        ApiBaseUrl = $taskState.ApiBaseUrl; DraftTarget = $taskState.DraftTarget; PrimaryDatabase = $taskState.PrimaryDatabase;
        StatePath = $taskStatePath; StdoutLog = $taskOutPath; StderrLog = $taskErrPath }

    $taskStage = 'execução isolada'
    $taskProcess.WaitForExit()
    [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($taskOutCopy, $taskErrCopy))
    [pscustomobject]@{ Status = 'Exited'; Pid = $taskState.Pid; ExitCode = $taskProcess.ExitCode }
} catch {
    # Não imprimir exceção original, PSCredential, ambiente, password/JWT ou PSI.
    throw ('Launcher interrompido na etapa "' + $taskStage + '". Revise os alvos/configuração e logs isolados; nenhum fallback de ambiente foi tentado.')
} finally {
    if ($null -ne $taskStartInfo) { $taskStartInfo.Environment.Clear() }
    $taskPlainPassword = $null
    $taskJwtSecret = $null
    $taskCredential = $null
    # Interrupção/falha deste launcher não deixa seu próprio java órfão. Não usa
    # busca genérica nem PID externo: somente objeto criado por esta invocação.
    if ($taskStartedByThisInvocation -and $null -ne $taskProcess) {
        try {
            if (-not $taskProcess.HasExited) { $taskProcess.Kill(); [void]$taskProcess.WaitForExit(10000) }
        } catch { }
    }
    if ($null -ne $taskOutCopy -and $null -ne $taskErrCopy) {
        try { [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($taskOutCopy, $taskErrCopy), 10000) | Out-Null } catch { }
    }
    if ($null -ne $taskOutStream) { $taskOutStream.Dispose() }
    if ($null -ne $taskErrStream) { $taskErrStream.Dispose() }
    if ($null -ne $taskProcess) { $taskProcess.Dispose() }
}
