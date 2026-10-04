$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
New-Item -ItemType Directory -Force data/maps | Out-Null
$sourceUrl = 'https://download.geofabrik.de/asia/india/central-zone-260911.osm.pbf'
$targetFile = 'data/maps/central-zone-260911.osm.pbf'
if (-not (Test-Path -LiteralPath $targetFile)) { Invoke-WebRequest -Uri $sourceUrl -OutFile $targetFile }
Invoke-WebRequest -Uri ($sourceUrl + '.md5') -OutFile ($targetFile + '.md5')
$expectedHash = ((Get-Content -Raw -LiteralPath ($targetFile + '.md5')).Split(' ')[0]).Trim()
if ((Get-FileHash -LiteralPath $targetFile -Algorithm MD5).Hash.ToLower() -ne $expectedHash.ToLower()) { throw 'OSM source checksum failed' }
Invoke-WebRequest -Uri 'https://www.openstreetmap.org/api/0.6/relation/1950071/full' -OutFile data/maps/mp-boundary.osm
Write-Output 'Madhya Pradesh source and boundary downloaded. Run prepare-mp-map.py, then build-mp-tiles.mjs.'
