# 3.3.5A — Silent Device Readiness

## UX principle

Afora must keep operational observability rich for supervisors while minimizing friction for field operators.

- READY: no UI interruption.
- READY_WITH_WARNINGS: normally no UI interruption.
- BLOCKED: show only the actionable problem that prevents safe capture.
- Critical-but-non-blocking conditions: show a short, actionable warning only when it materially helps the operator.

## Internal evidence retained

The readiness layer continues to evaluate and log:

- precise location permission
- location services state
- initial GPS fix quality
- battery
- local storage
- network state
- notification capability
- background execution capability
- sync backlog
- trip identity

These checks are internal operational evidence and are intended for Telemetry / Cloud / Operations Center consumption rather than routine field-operator display.

## Product rule

If Afora can tolerate, recover from, or synchronize around a condition by itself, the field operator should not need to see it. If the operator must take an action to make capture safe or possible, Afora should show only that action.
