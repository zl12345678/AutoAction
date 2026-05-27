param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes

function Write-JsonResult {
    param(
        [bool]$Success,
        [string]$Message,
        [object[]]$Elements = @()
    )

    @{
        success = $Success
        message = $Message
        elements = $Elements
    } | ConvertTo-Json -Depth 8 -Compress
}

function Get-Request {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "Request payload is empty."
    }
    return $raw | ConvertFrom-Json
}

function Find-WindowByTitleContains {
    param([string]$WindowTitleContains)

    $root = [System.Windows.Automation.AutomationElement]::RootElement
    $windows = $root.FindAll(
        [System.Windows.Automation.TreeScope]::Children,
        [System.Windows.Automation.Condition]::TrueCondition
    )

    foreach ($window in $windows) {
        $name = $window.Current.Name
        if (-not [string]::IsNullOrWhiteSpace($name) -and $name -like "*$WindowTitleContains*") {
            return $window
        }
    }

    return $null
}

function Convert-Element {
    param([System.Windows.Automation.AutomationElement]$Element)

    return @{
        name = $Element.Current.Name
        automationId = $Element.Current.AutomationId
        controlType = $Element.Current.ControlType.ProgrammaticName
    }
}

function Build-Condition {
    param($Action)

    $conditions = New-Object System.Collections.Generic.List[System.Windows.Automation.Condition]
    if (-not [string]::IsNullOrWhiteSpace($Action.controlType)) {
        $conditions.Add(
            [System.Windows.Automation.PropertyCondition]::new(
                [System.Windows.Automation.AutomationElement]::ControlTypeProperty,
                [System.Windows.Automation.ControlType]::LookupById((Get-ControlTypeId -ControlTypeName $Action.controlType))
            )
        )
    }
    if (-not [string]::IsNullOrWhiteSpace($Action.name)) {
        $conditions.Add(
            [System.Windows.Automation.PropertyCondition]::new(
                [System.Windows.Automation.AutomationElement]::NameProperty,
                $Action.name
            )
        )
    }
    if (-not [string]::IsNullOrWhiteSpace($Action.automationId)) {
        $conditions.Add(
            [System.Windows.Automation.PropertyCondition]::new(
                [System.Windows.Automation.AutomationElement]::AutomationIdProperty,
                $Action.automationId
            )
        )
    }

    if ($conditions.Count -eq 0) {
        return [System.Windows.Automation.Condition]::TrueCondition
    }
    if ($conditions.Count -eq 1) {
        return $conditions[0]
    }
    return [System.Windows.Automation.AndCondition]::new($conditions.ToArray())
}

function Get-ControlTypeId {
    param([string]$ControlTypeName)

    switch ($ControlTypeName.ToLowerInvariant()) {
        "button" { return [System.Windows.Automation.ControlType]::Button.Id }
        "edit" { return [System.Windows.Automation.ControlType]::Edit.Id }
        "text" { return [System.Windows.Automation.ControlType]::Text.Id }
        "window" { return [System.Windows.Automation.ControlType]::Window.Id }
        "checkbox" { return [System.Windows.Automation.ControlType]::CheckBox.Id }
        default { throw "Unsupported control type: $ControlTypeName" }
    }
}

function Invoke-Action {
    param(
        [System.Windows.Automation.AutomationElement]$Window,
        $Action
    )

    $condition = Build-Condition -Action $Action
    $element = $Window.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $condition)
    if ($null -eq $element) {
        throw "UI Automation element not found."
    }

    switch ($Action.pattern) {
        "INVOKE" {
            $pattern = $element.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
            ([System.Windows.Automation.InvokePattern]$pattern).Invoke()
        }
        "SET_VALUE" {
            $pattern = $element.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern)
            ([System.Windows.Automation.ValuePattern]$pattern).SetValue($Action.value)
        }
        default {
            throw "Unsupported pattern: $($Action.pattern)"
        }
    }

    return Convert-Element -Element $element
}

try {
    $request = Get-Request
    $operation = $request.operation

    switch ($operation) {
        "inspectWindow" {
            $window = Find-WindowByTitleContains -WindowTitleContains $request.windowTitleContains
            if ($null -eq $window) {
                Write-Output (Write-JsonResult -Success:$false -Message "Window not found." -Elements @())
                exit 0
            }

            $children = $window.FindAll(
                [System.Windows.Automation.TreeScope]::Descendants,
                [System.Windows.Automation.Condition]::TrueCondition
            )
            $elements = @()
            foreach ($child in $children) {
                $elements += Convert-Element -Element $child
            }
            Write-Output (Write-JsonResult -Success:$true -Message "Window inspected." -Elements $elements)
        }
        "executeRule" {
            $window = Find-WindowByTitleContains -WindowTitleContains $request.windowTitleContains
            if ($null -eq $window) {
                Write-Output (Write-JsonResult -Success:$false -Message "Window not found." -Elements @())
                exit 0
            }

            $executed = @()
            foreach ($action in $request.actions) {
                $executed += Invoke-Action -Window $window -Action $action
            }
            Write-Output (Write-JsonResult -Success:$true -Message "Rule executed." -Elements $executed)
        }
        default {
            throw "Unsupported operation: $operation"
        }
    }
} catch {
    Write-Output (Write-JsonResult -Success:$false -Message $_.Exception.Message -Elements @())
    exit 1
}
