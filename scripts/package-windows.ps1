$ErrorActionPreference = 'Stop'
$project = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $project
New-Item -ItemType Directory -Force 'build/classes','build/distribution','build/native' | Out-Null
$sources = @(Get-ChildItem 'src/main/java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -d 'build/classes' @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& jar --create --file 'build/distribution/BattleShip-Reborn.jar' --main-class com.battleship.Main -C 'build/classes' com/battleship
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& jpackage --type app-image --name BattleShipReborn --app-version 1.0.0 --input 'build/distribution' --main-jar 'BattleShip-Reborn.jar' --dest 'build/native'
exit $LASTEXITCODE
