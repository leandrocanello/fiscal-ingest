# Registra as 3 tarefas no Agendador do Windows (segunda a sexta).
# Rode no PowerShell do Windows, como o seu usuário:  .\agendar.ps1
# Para remover:  Get-ScheduledTask fiscal-* | Unregister-ScheduledTask -Confirm:$false
# [VERIFICAR] WSL costuma precisar do usuário com sessão aberta (pode estar com a tela bloqueada).

$Distro = "fiscal-dev"
$Usuario = "dev"
$Dias = @("Monday","Tuesday","Wednesday","Thursday","Friday")

function Nova-Tarefa($Nome, $Hora, $Script, $LimiteHoras) {
    $cmd = "cd ~/fiscal-motor && git pull -q --ff-only; exec ~/fiscal-motor/autonomo/bin/$Script"
    $acao = New-ScheduledTaskAction -Execute "wsl.exe" -Argument "-d $Distro -u $Usuario -- bash -lc `"$cmd`""
    $gatilho = New-ScheduledTaskTrigger -Weekly -DaysOfWeek $Dias -At $Hora
    $config = New-ScheduledTaskSettingsSet -WakeToRun -StartWhenAvailable `
        -ExecutionTimeLimit (New-TimeSpan -Hours $LimiteHoras) -MultipleInstances IgnoreNew `
        -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName $Nome -Action $acao -Trigger $gatilho -Settings $config -Force | Out-Null
    Write-Host "Tarefa $Nome registrada para $Hora (seg-sex)."
}

Nova-Tarefa "fiscal-janela-1"   "08:00" "janela.sh"     5
# D5 (ADR-029): duas janelas desde o início.
Nova-Tarefa "fiscal-janela-2" "13:00" "janela.sh"     5
Nova-Tarefa "fiscal-fechamento" "17:50" "fechamento.sh" 1
