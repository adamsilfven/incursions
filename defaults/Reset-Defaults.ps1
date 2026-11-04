param([string]$Instance)
$ErrorActionPreference = 'Stop'
try {
 if (-not $Instance) { $Instance = Read-Host 'Instance or dedicated server folder (Minecraft/server must be closed)' }
 $root = (Resolve-Path -LiteralPath $Instance).Path.TrimEnd('\','/')
 if (-not (Test-Path -LiteralPath (Join-Path $root 'mods') -PathType Container)) { throw 'Selected folder must contain a mods folder.' }
 $payload = Join-Path $PSScriptRoot 'incursions-server.toml'
 if (-not (Test-Path -LiteralPath $payload -PathType Leaf)) { throw 'Missing supplied default config.' }
 $targets = @((Join-Path $root 'config/incursions-server.toml'), (Join-Path $root 'defaultconfigs/incursions-server.toml'))
 $saves = Join-Path $root 'saves'
 if (Test-Path -LiteralPath $saves -PathType Container) {
  foreach ($world in Get-ChildItem -LiteralPath $saves -Directory) {
   $target = Join-Path $world.FullName 'serverconfig/incursions-server.toml'
   if (Test-Path -LiteralPath $target -PathType Leaf) { $targets += $target }
  }
 }
 $properties = Join-Path $root 'server.properties'
 if (Test-Path -LiteralPath $properties -PathType Leaf) {
  $line = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^level-name=' } | Select-Object -First 1
  if ($line) {
   $level = $line.Substring(11).Trim()
   if ($level -match '[\\:]|\.\.' -or [IO.Path]::IsPathRooted($level)) { throw 'Nonstandard level-name: reset its serverconfig manually using the supplied config.' }
   $target = Join-Path (Join-Path $root $level) 'serverconfig/incursions-server.toml'
   if (Test-Path -LiteralPath $target -PathType Leaf) { $targets += $target }
  }
 }
 $targets = @($targets | Select-Object -Unique)
 $backup = Join-Path $root ('incursions-config-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
 New-Item -ItemType Directory -Path $backup -Force | Out-Null
 $receipt = @()
 foreach ($target in $targets) {
  $relative = $target.Substring($root.Length).TrimStart('\','/')
  $existed = Test-Path -LiteralPath $target -PathType Leaf
  if ($existed) {
   $copy = Join-Path $backup $relative
   New-Item -ItemType Directory -Path (Split-Path $copy -Parent) -Force | Out-Null
   Copy-Item -LiteralPath $target -Destination $copy
  }
  $receipt += [PSCustomObject]@{path=$target;relative=$relative;existed=$existed}
 }
 $receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'receipt.json')
 $written = @()
 try {
  foreach ($entry in $receipt) {
   New-Item -ItemType Directory -Path (Split-Path $entry.path -Parent) -Force | Out-Null
   $written += $entry
   Copy-Item -LiteralPath $payload -Destination $entry.path -Force
   if ((Get-FileHash -LiteralPath $entry.path).Hash -ne (Get-FileHash -LiteralPath $payload).Hash) { throw 'Config verification failed.' }
  }
 } catch {
  foreach ($entry in $written) {
   if ($entry.existed) { Copy-Item -LiteralPath (Join-Path $backup $entry.relative) -Destination $entry.path -Force }
   elseif (Test-Path -LiteralPath $entry.path) { Remove-Item -LiteralPath $entry.path }
  }
  throw
 }
 Write-Host ('Reset ' + $targets.Count + ' configs. Trigger checks: 100 ticks; cooldown: 21600 ticks. Backup: ' + $backup)
 exit 0
} catch { Write-Error $_; exit 1 }
