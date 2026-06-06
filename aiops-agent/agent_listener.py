import os
import sys
import json
import time
import argparse
import urllib.request
import urllib.error
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler

LOKI_URL = os.getenv("LOKI_URL", "http://localhost:3100")
TEMPO_URL = os.getenv("TEMPO_URL", "http://localhost:3200")
RUNBOOKS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "runbooks")
DIAGNOSES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "diagnoses")

os.makedirs(DIAGNOSES_DIR, exist_ok=True)

# 1. RAG Runbook Retriever (Vector-free local search)
def retrieve_runbook(alert_name: str) -> str:
    """Finds the most relevant runbook based on the alert name."""
    alert_lower = alert_name.lower()
    runbook_file = None
    
    if "circuit" in alert_lower or "breaker" in alert_lower:
        runbook_file = "circuit_breaker_open.md"
    elif "down" in alert_lower or "unreachable" in alert_lower:
        runbook_file = "service_down.md"
    elif "duplicate" in alert_lower or "idempotency" in alert_lower:
        runbook_file = "duplicate_event.md"
        
    if runbook_file:
        filepath = os.path.join(RUNBOOKS_DIR, runbook_file)
        if os.path.exists(filepath):
            with open(filepath, "r") as f:
                return f.read()
                
    # Default fallback runbook
    return "No specific runbook matches this alert. Check general troubleshooting guidelines."

# 2. Loki Log Client (using urllib)
def fetch_loki_logs(query: str, start_time: str, limit: int = 20) -> list:
    """Queries Loki for logs matching a query string around a start time."""
    try:
        # Encode params manually
        encoded_query = urllib.parse.quote(query)
        url = f"{LOKI_URL}/loki/api/v1/query_range?query={encoded_query}&limit={limit}"
        
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                result = json.loads(response.read().decode("utf-8"))
                logs = []
                for stream in result.get("data", {}).get("result", []):
                    for val in stream.get("values", []):
                        # val is a list of [timestamp_ns, log_line]
                        logs.append(val[1])
                return logs
    except Exception as e:
        print(f"Error connecting to Loki: {e}")
    return []

# 3. Tempo Trace Client (using urllib)
def fetch_tempo_trace(trace_id: str) -> dict:
    """Queries Tempo for details about a specific trace ID."""
    try:
        url = f"{TEMPO_URL}/api/traces/{trace_id}"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        print(f"Error connecting to Tempo: {e}")
    return {}

# 4. Agent Diagnostics Engine (Fallback rules + LLM formulation structure)
def diagnose_failure(alert_name: str, alert_desc: str, runbook: str, logs: list, trace_id: str, trace_details: dict) -> str:
    """Synthesizes context using agentic analysis rules, outputting a detailed report."""
    timestamp = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
    
    # Analyze logs for common exceptions
    detected_errors = []
    for log in logs:
        if "CallNotPermittedException" in log:
            detected_errors.append("CallNotPermittedException (Circuit Breaker open block)")
        elif "DuplicateEventException" in log:
            detected_errors.append("DuplicateEventException (Idempotency violation)")
        elif "OutOfMemoryError" in log:
            detected_errors.append("OutOfMemoryError (JVM Memory exhaustion)")
        elif "NullPointerException" in log:
            detected_errors.append("NullPointerException (Code exception)")
            
    detected_errors = list(set(detected_errors))
    
    # Formulate recommendations
    remediations = []
    if "CallNotPermittedException" in detected_errors or "circuit" in alert_name.lower():
        remediations = [
            "1. **Circuit Breaker Tuning**: Review Gateway Service config. Increase `resilience4j.circuitbreaker.instances.accountService.failureRateThreshold` or `waitDurationInOpenState` if downstream recovers slowly.",
            "2. **Capacity Scaling**: Increase downstream account-service replicas or database connection pool parameters.",
            "3. **Dependency Check**: Validate account-service container health status and logs."
        ]
    elif "OutOfMemoryError" in detected_errors or "down" in alert_name.lower():
        remediations = [
            "1. **Heap Memory Allocation**: Increase `-Xmx` limit in Dockerfile or docker-compose.yml.",
            "2. **Memory Leak Diagnostics**: Inspect the generated heap dump (.hprof) in the local volume `./dumps`.",
            "3. **Restart Policy**: Confirm docker-compose is set to `restart: always` to recover container automatically."
        ]
    elif "DuplicateEventException" in detected_errors or "duplicate" in alert_name.lower():
        remediations = [
            "1. **Upstream Backoff**: Update client configurations to perform retries with exponential backoff & randomized jitter.",
            "2. **Idempotency Key Scope**: Ensure UUIDs or unique transaction IDs are uniquely scoped per client event to avoid key collision."
        ]
    else:
        remediations = [
            "1. **Standard Analysis**: Check container logs via `docker logs <container_id>`.",
            "2. **Health endpoint check**: Probe `/actuator/health` to view failing component dependencies."
        ]
        
    report = f"""# AIOps Agent Diagnostic Report

**Alert Name:** {alert_name}
**Description:** {alert_desc}
**Report Generation Time:** {timestamp}
**Detected Fault Signature:** {", ".join(detected_errors) if detected_errors else "Unknown anomaly"}
**Distributed Trace Associated:** {trace_id if trace_id else "None found in recent logs"}

---

## 1. RAG Context Retrieval (Matched Runbook)
```markdown
{runbook}
```

---

## 2. Telemetry Log Capture (Loki)
{f"Captured {len(logs)} log lines around the alert time:" if logs else "No matching logs retrieved from Loki."}
```json
{json.dumps(logs, indent=2) if logs else "[]"}
```

---

## 3. Distributed Trace Analysis (Tempo)
{f"Trace details for `{trace_id}` fetched successfully." if trace_details else "No trace telemetry retrieved."}
```json
{json.dumps(trace_details, indent=2) if trace_details else "{}"}
```

---

## 4. Root Cause Analysis & Automated Remediation Suggestion
Based on telemetry correlation and our post-mortem runbook repository, we recommend the following remediations:

{chr(10).join(remediations)}

### Auto-Generated Test Scenario Recommendation
To prevent this failure from reoccurring, run the following k6 concurrency script:
```javascript
import http from 'k6/http';
import {{ sleep }} from 'k6';

export let options = {{
  stages: [
    {{ duration: '30s', target: 20 }}, // ramp up
    {{ duration: '1m', target: 50 }},  // stress test circuit breaker
    {{ duration: '30s', target: 0 }},  // ramp down
  ],
}};

export default function () {{
  let payload = JSON.stringify({{
    eventId: `evt-stress-${{__VU}}-${{__ITER}}`,
    accountId: "acct-stress",
    type: "CREDIT",
    amount: 10.00,
    currency: "USD",
    eventTimestamp: new Date().toISOString()
  }});
  
  let headers = {{
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + __ENV.JWT_TOKEN
  }};
  
  let res = http.post('http://localhost:8080/events', payload, {{ headers: headers }});
  sleep(0.1);
}}
```
"""
    return report

# HTTP Server request handler using standard library only
class WebhookHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Override to suppress standard HTTP logging to stdout if desired
        sys.stderr.write("%s - - [%s] %s\n" % (self.address_string(), self.log_date_time_string(), format%args))

    def do_POST(self):
        if self.path == "/webhook":
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                payload = json.loads(post_data.decode('utf-8'))
                print(f"Received webhook payload: {json.dumps(payload, indent=2)}")
                
                alerts = payload.get("alerts", [])
                for alert in alerts:
                    alert_name = alert.get("labels", {}).get("alertname", "Unknown Alert")
                    alert_desc = alert.get("annotations", {}).get("description", "No description provided")
                    
                    # 1. Retrieve Runbook (RAG)
                    runbook = retrieve_runbook(alert_name)
                    
                    # 2. Fetch Logs from Loki
                    query = '{container=~"gateway-service|account-service"}'
                    if "circuit" in alert_name.lower():
                        query = '{container="gateway-service"} |= "Circuit"'
                    elif "down" in alert_name.lower():
                        query = '{container=~"gateway-service|account-service"} |= "ERROR"'
                        
                    logs = fetch_loki_logs(query, alert.get("startsAt"))
                    
                    # 3. Extract Trace ID (if any) and fetch Trace from Tempo
                    trace_id = ""
                    trace_details = {}
                    for log in logs:
                        if "trace_id" in log:
                            try:
                                log_json = json.loads(log)
                                if log_json.get("trace_id"):
                                    trace_id = log_json.get("trace_id")
                                    break
                            except Exception:
                                pass
                                
                    if trace_id:
                        trace_details = fetch_tempo_trace(trace_id)
                        
                    # 4. Perform diagnosis
                    report = diagnose_failure(alert_name, alert_desc, runbook, logs, trace_id, trace_details)
                    
                    # 5. Save report
                    filename = f"diagnosis_{alert_name.replace(' ', '_').lower()}_{int(time.time())}.md"
                    filepath = os.path.join(DIAGNOSES_DIR, filename)
                    with open(filepath, "w") as f:
                        f.write(report)
                        
                    print(f"Generated diagnostic report: {filepath}")
                
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                response_body = json.dumps({"status": "success", "processed_alerts": len(alerts)})
                self.wfile.write(response_body.encode('utf-8'))
                
            except Exception as e:
                print(f"Error processing webhook: {e}")
                self.send_response(500)
                self.end_headers()
                self.wfile.write(str(e).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

# Command line interface for manual mock testing
def trigger_mock_alert(alert_type: str):
    print(f"Triggering mock alert: {alert_type}")
    
    alert_name = "Circuit Breaker Open"
    alert_desc = "Circuit breaker to Account service is in OPEN state, failing requests fast."
    mock_logs = [
        '{"timestamp":"2026-06-05T21:29:27.265-05:00","level":"ERROR","service":"gateway-service","trace_id":"ed4516e78c4a123b5c5e90a60b8aa9d8","span_id":"55fda7550463e197","message":"Circuit breaker is open. Blocking call to Account Service.","stack_trace":"io.github.resilience4j.circuitbreaker.CallNotPermittedException: Circuit breaker is open"}'
    ]
    mock_trace_id = "ed4516e78c4a123b5c5e90a60b8aa9d8"
    
    if alert_type == "down":
        alert_name = "Account Service is Down"
        alert_desc = "Account service container is down (up == 0)."
        mock_logs = [
            '{"timestamp":"2026-06-05T21:29:28.068-05:00","level":"ERROR","service":"account-service","trace_id":"","span_id":"","message":"OutOfMemoryError: Java heap space"}'
        ]
        mock_trace_id = ""
    elif alert_type == "duplicate":
        alert_name = "Duplicate Event Submission Storm"
        alert_desc = "High frequency of DuplicateEventExceptions detected."
        mock_logs = [
            '{"timestamp":"2026-06-05T21:29:27.447-05:00","level":"WARN","service":"gateway-service","trace_id":"be15505b4bab35614468c23d3b875b79","span_id":"7cd9a492c68937a4","message":"Duplicate event detected: evt-100"}'
        ]
        mock_trace_id = "be15505b4bab35614468c23d3b875b79"

    runbook = retrieve_runbook(alert_name)
    
    # Simulate Tempo trace info
    mock_trace_details = {}
    if mock_trace_id:
        mock_trace_details = {
            "traceID": mock_trace_id,
            "batches": [
                {
                    "resource": {
                        "attributes": [
                            {"key": "service.name", "value": {"stringValue": "gateway-service"}}
                        ]
                    },
                    "scopeSpans": [
                        {
                            "spans": [
                                {
                                    "traceId": mock_trace_id,
                                    "spanId": "55fda7550463e197",
                                    "name": "processEvent",
                                    "kind": 1,
                                    "status": {"code": 2}
                                }
                            ]
                        }
                    ]
                }
            ]
        }
        
    report = diagnose_failure(alert_name, alert_desc, runbook, mock_logs, mock_trace_id, mock_trace_details)
    
    filename = f"diagnosis_{alert_name.replace(' ', '_').lower()}_mock.md"
    filepath = os.path.join(DIAGNOSES_DIR, filename)
    with open(filepath, "w") as f:
        f.write(report)
        
    print(f"\n=======================================================")
    print(f"MOCK DIAGNOSIS GENERATED SUCCESSFULLY!")
    print(f"Report saved to: {filepath}")
    print(f"=======================================================\n")
    print(report[:400] + "...\n[Report Truncated in Console Output]")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AIOps Agent Listener CLI")
    parser.add_argument("--mock", type=str, choices=["circuit", "down", "duplicate"], help="Trigger a mock alert diagnostics locally")
    parser.add_argument("--port", type=int, default=5000, help="Port to run the webhook listener server on")
    
    args = parser.parse_args()
    
    if args.mock:
        trigger_mock_alert(args.mock)
    else:
        server_address = ('', args.port)
        httpd = HTTPServer(server_address, WebhookHandler)
        print(f"Starting standard library AIOps Webhook Server on port {args.port}...")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server...")
            httpd.server_close()
