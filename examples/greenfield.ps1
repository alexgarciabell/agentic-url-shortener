$body = @{
  scenarioType = "GREENFIELD"
  requirement = "Create a URL shortener with create, redirect, analytics, delete, expiration and HTTP/HTTPS validation."
  workspacePath = ".\workspaces\greenfield-url-shortener"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows" -ContentType "application/json" -Body $body
$response | ConvertTo-Json -Depth 8

if ($response.status -eq "WAITING_FOR_APPROVAL") {
  $approval = @{ decision = "APPROVED"; comment = "Verified generated code reviewed." } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows/$($response.id)/approval" -ContentType "application/json" -Body $approval | ConvertTo-Json -Depth 8
}
