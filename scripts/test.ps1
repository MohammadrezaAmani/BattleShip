$ErrorActionPreference = 'Stop'
$project = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $project
New-Item -ItemType Directory -Force 'build/classes' | Out-Null
$sources = @(Get-ChildItem 'src/main/java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -d 'build/classes' @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -Djava.awt.headless=true -ea -cp 'build/classes' com.battleship.SelfTest 1000
exit $LASTEXITCODE
