param(
 [string]$Original='recovery/offroute-20261003/installed-original.apk',
 [string]$Candidate='artifacts/Offroute-1.5.4.apk'
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$old=[IO.Compression.ZipFile]::OpenRead((Join-Path $pwd $Original))
$new=[IO.Compression.ZipFile]::OpenRead((Join-Path $pwd $Candidate))
function EntryHash($entry){$stream=$entry.Open();try{return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream))}finally{$stream.Dispose()}}
try{
 $newEntries=@{};foreach($entry in $new.Entries){$newEntries[$entry.FullName]=$entry}
 $verified=0
 foreach($entry in $old.Entries){
  if($entry.FullName -match '^assets/(models/|maps/|public/maps/)' -or $entry.FullName -match '^assets/public/assets/.*(\.css|worker.*\.js)$'){
   $other=$newEntries[$entry.FullName]
   if(-not $other -or $other.Length -ne $entry.Length -or (EntryHash $entry) -ne (EntryHash $other)){throw "Unexpected changed payload: $($entry.FullName)"}
   $verified++
  }
 }
 if($verified -lt 4){throw 'Map/model verification did not find expected assets'}
 $result=[ordered]@{unchangedPayloadEntries=$verified;candidate=$Candidate;sha256=(Get-FileHash -LiteralPath $Candidate).Hash;bytes=(Get-Item -LiteralPath $Candidate).Length;uiComparison='ui-preservation.log'}
 $result | ConvertTo-Json | Set-Content artifacts/offroute-20261003/apk-verification.json
 $result | ConvertTo-Json
}finally{$old.Dispose();$new.Dispose()}
