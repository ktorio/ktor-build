md $Env:SIGN_KEY_LOCATION -force
cd $Env:SIGN_KEY_LOCATION

echo "Stopping gpg-agent and removing GNUPG folder"
Stop-Process -Name "gpg-agent" -ErrorAction SilentlyContinue
rm -r -fo C:\Users\builduser\.gnupg 2>null

# Hard-coding path for GPG since this fails on TeamCity
# $gpg=(get-command gpg.exe).Path
$gpg="C:\Program Files\Git\usr\bin\gpg.exe"
Set-Alias -Name gpg2.exe -Value $gpg

echo "Exporting public key"
[System.IO.File]::WriteAllText("$pwd\keyfile", $Env:SIGN_KEY_PUBLIC)
& $gpg --batch --import keyfile
rm keyfile

echo "Exporting private key"
[System.IO.File]::WriteAllText("$pwd\keyfile", $Env:SIGN_KEY_PRIVATE)
& $gpg --allow-secret-key-import --batch --import keyfile
rm keyfile
& $gpg --list-keys

echo "Sending keys"
& $gpg --keyserver hkps://keyserver.ubuntu.com --send-keys $Env:SIGN_KEY_ID

& "gpgconf" --kill gpg-agent
& "gpgconf" --homedir "/c/Users/builduser/.gnupg" --launch gpg-agent
