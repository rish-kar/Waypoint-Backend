#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent
COLLECTION = ROOT / "collections" / "Waypoint-Backend"
errors = []


def read(rel):
    path = ROOT / rel
    if not path.exists():
        errors.append(f"Missing required Postman file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(rel, *needles):
    text = read(rel)
    for needle in needles:
        if needle not in text:
            errors.append(f"{rel}: missing safety marker {needle!r}")
    return text


def forbid(rel, *needles):
    text = read(rel)
    for needle in needles:
        if needle in text:
            errors.append(f"{rel}: forbidden stale/hardcoded value {needle!r}")
    return text


# Global request safety: stale production TOTP must never leak into a local/non-admin request.
# Collection-level scripts execute before request-level pre-request scripts, so global
# validation must not reject dynamic header/body variables that are intentionally generated later.
require(
    "collections/Waypoint-Backend/.resources/definition.yaml",
    "pm.request.headers.remove('X-Admin-TOTP')",
    "Unresolved Postman URL variables",
    "dynamic header/body variables",
)
forbid(
    "collections/Waypoint-Backend/.resources/definition.yaml",
    "checkResolved('Body'",
    "checkResolved(`Header",
)

# Simulated webhook flows must generate unique IDs and current provider event timestamps.
activate = require(
    "collections/Waypoint-Backend/04 - Webhooks/01 - Subscription Events/01 - Activate Monthly Subscription.request.yaml",
    "Date.now()",
    "updated_at: now.toISOString()",
    "pm.environment.set('subscriptionId', subscriptionId)",
    "/api/v1/subscriptions/current",
    "PREMIUM_MONTHLY",
)
refund = require(
    "collections/Waypoint-Backend/04 - Webhooks/01 - Subscription Events/02 - Refund Subscription.request.yaml",
    "updated_at: new Date().toISOString()",
    "Run Activate Monthly Subscription first",
    "/api/v1/subscriptions/current",
    "REFUNDED",
)
for text, name in [(activate, "activate monthly"), (refund, "refund")]:
    if "2030-01-01T00:00:00Z" in text:
        errors.append(f"{name}: hardcoded provider timestamp must not be used")

# User/admin selectors must clear dependent IDs before selecting new data.
require(
    "collections/Waypoint-Backend/05 - Admin/02 - Users/02 - Find User by Email.request.yaml",
    "pm.environment.unset('userId')",
    "pm.environment.unset('adminSubscriptionId')",
    "pm.environment.unset('adminGrantId')",
)
require(
    "collections/Waypoint-Backend/05 - Admin/03 - Subscriptions/01 - List Subscriptions.request.yaml",
    "pm.environment.unset('adminSubscriptionId')",
    "item.userId === expectedUserId",
)
require(
    "collections/Waypoint-Backend/05 - Admin/03 - Subscriptions/02 - Update Subscription.request.yaml",
    "subscriptionUserId !== userId",
    "Updated subscription still belongs to selected user",
)
require(
    "collections/Waypoint-Backend/05 - Admin/04 - Premium Special/03 - List Special Grants.request.yaml",
    "userId={{userId}}",
    "pm.environment.unset('adminGrantId')",
)
require(
    "collections/Waypoint-Backend/05 - Admin/05 - Webhook Events/01 - List Webhook Events.request.yaml",
    "pm.environment.unset('adminWebhookEventId')",
    "body.items.length === 1",
)

# Auth flows must not preserve credentials/IDs from a failed new login attempt.
for rel in [
    "collections/Waypoint-Backend/01 - Authentication/01 - Google/01 - Google Login.request.yaml",
    "collections/Waypoint-Backend/01 - Authentication/02 - Microsoft Login/02 - Exchange Session.request.yaml",
]:
    require(rel, "pm.environment.unset('jwt')", "pm.environment.unset('userId')")
require(
    "collections/Waypoint-Backend/01 - Authentication/03 - Session/02 - Logout.request.yaml",
    "pm.environment.unset('jwt')",
    "pm.environment.unset('userId')",
)

# Runtime-sensitive requests must not carry a stale fixed timestamp.
forbid(
    "collections/Waypoint-Backend/07 - AI/03 - Intent - Group Tabs.request.yaml",
    "2026-08-24T12:00:00+05:30",
)
require(
    "collections/Waypoint-Backend/07 - AI/03 - Intent - Group Tabs.request.yaml",
    "new Date().toISOString()",
)

# Lemon Squeezy lifecycle bootstrap must start clean.
require(
    "collections/Waypoint-Backend/06 - Lemon Squeezy Test Mode/01 - Setup/01 - Bootstrap Test Context.request.yaml",
    "lemonSqueezyLifecycleSubscriptionId",
    "lemonSqueezyInvoiceId",
    "pm.environment.unset(key)",
)

# Both environment representations must start with an empty generated subscriptionId.
yaml_env = read("environments/Waypoint Local.environment.yaml")
if re.search(r"- key: subscriptionId\s+value:\s*[^'\"\n]*postman-subscription", yaml_env):
    errors.append("Waypoint Local.environment.yaml: subscriptionId must start empty")
json_env_path = ROOT / "Waypoint-Local.postman_environment.json"
try:
    env = json.loads(json_env_path.read_text(encoding="utf-8"))
    values = {item.get("key"): item.get("value") for item in env.get("values", [])}
    if values.get("subscriptionId"):
        errors.append("Waypoint-Local.postman_environment.json: subscriptionId must start empty")
    if "adminSubscriptionUserId" not in values:
        errors.append("Waypoint-Local.postman_environment.json: adminSubscriptionUserId is required")
except Exception as exc:
    errors.append(f"Waypoint-Local.postman_environment.json: invalid JSON: {exc}")

# Catch the exact stale value that caused cross-run collisions anywhere in maintained Postman sources.
for path in ROOT.rglob("*"):
    if path.is_file() and path.suffix in {".yaml", ".json"}:
        text = path.read_text(encoding="utf-8")
        if "postman-subscription-001" in text:
            errors.append(f"{path.relative_to(ROOT)}: contains forbidden fixed subscription ID")

if errors:
    print("Postman collection safety validation FAILED:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Postman collection safety validation passed.")
