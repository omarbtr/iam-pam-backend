# ============================================================================
# Create AD user: Omar Bentahar
# Run on a machine that has the Active Directory PowerShell module installed
# (Domain Controller, or any Windows machine with RSAT-AD-PowerShell)
# ============================================================================

param(
    [string]$DomainController = "localhost",
    [string]$OUPath           = "OU=Users,DC=iam-pam,DC=local"   # adjust to your domain
)

$Username    = "omar.bentahar"
$FirstName   = "Omar"
$LastName    = "Bentahar"
$DisplayName = "Omar Bentahar"
$Email       = "omar4bentahar@gmail.com"
$UPN         = "$Username@iam-pam.local"    # adjust domain suffix
$Password    = ConvertTo-SecureString "omar.btr" -AsPlainText -Force

try {
    Import-Module ActiveDirectory -ErrorAction Stop

    $existing = Get-ADUser -Filter "SamAccountName -eq '$Username'" -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "User '$Username' already exists — skipping creation." -ForegroundColor Yellow
    } else {
        New-ADUser `
            -Name              $DisplayName `
            -GivenName         $FirstName `
            -Surname           $LastName `
            -SamAccountName    $Username `
            -UserPrincipalName $UPN `
            -EmailAddress      $Email `
            -Path              $OUPath `
            -AccountPassword   $Password `
            -ChangePasswordAtLogon $false `
            -Enabled           $true `
            -Server            $DomainController

        Write-Host "✔  AD user created:" -ForegroundColor Green
        Write-Host "   sAMAccountName : $Username"
        Write-Host "   UPN            : $UPN"
        Write-Host "   Email          : $Email"
        Write-Host "   OU             : $OUPath"
    }
} catch {
    Write-Error "Failed: $_"
    exit 1
}
