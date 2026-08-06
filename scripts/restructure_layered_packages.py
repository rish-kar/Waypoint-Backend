from __future__ import annotations

import re
import subprocess
from pathlib import Path

MAIN_ROOT = Path("src/main/java/com/waypoint/backend")
TEST_ROOT = Path("src/test/java/com/waypoint/backend")

MAIN_MOVES = {
    "auth/AuthController.java": "controller/auth/AuthController.java",
    "auth/AuthResponse.java": "model/auth/AuthResponse.java",
    "auth/GoogleAuthRequest.java": "model/auth/GoogleAuthRequest.java",
    "auth/GoogleAuthService.java": "service/auth/GoogleAuthService.java",
    "auth/GoogleProfile.java": "model/auth/GoogleProfile.java",
    "auth/GoogleProfileClient.java": "utilities/client/google/GoogleProfileClient.java",
    "auth/GoogleProperties.java": "config/auth/GoogleProperties.java",
    "auth/GoogleWebClientProfileClient.java": "utilities/client/google/GoogleWebClientProfileClient.java",
    "billing/BillingController.java": "controller/billing/BillingController.java",
    "billing/BillingService.java": "service/billing/BillingService.java",
    "billing/BillingStatusResponse.java": "model/billing/BillingStatusResponse.java",
    "billing/CheckoutResponse.java": "model/billing/CheckoutResponse.java",
    "billing/LemonSqueezyClient.java": "utilities/client/lemonsqueezy/LemonSqueezyClient.java",
    "billing/LemonSqueezyProperties.java": "config/billing/LemonSqueezyProperties.java",
    "billing/LemonSqueezyWebClient.java": "utilities/client/lemonsqueezy/LemonSqueezyWebClient.java",
    "common/ApiErrorResponse.java": "model/common/ApiErrorResponse.java",
    "common/ApiException.java": "utilities/exception/ApiException.java",
    "common/ExternalServiceException.java": "utilities/exception/ExternalServiceException.java",
    "common/GlobalExceptionHandler.java": "controller/advice/GlobalExceptionHandler.java",
    "common/InvalidRequestException.java": "utilities/exception/InvalidRequestException.java",
    "common/NotFoundException.java": "utilities/exception/NotFoundException.java",
    "common/UnauthorizedException.java": "utilities/exception/UnauthorizedException.java",
    "common/UpstreamServiceException.java": "utilities/exception/UpstreamServiceException.java",
    "config/AppProperties.java": "config/application/AppProperties.java",
    "config/ConfigurationStartupValidator.java": "config/application/ConfigurationStartupValidator.java",
    "config/CorsProperties.java": "config/application/CorsProperties.java",
    "config/RequestLoggingFilter.java": "config/logging/RequestLoggingFilter.java",
    "config/WebClientConfig.java": "config/client/WebClientConfig.java",
    "entitlement/EntitlementController.java": "controller/entitlement/EntitlementController.java",
    "entitlement/EntitlementResponse.java": "model/entitlement/EntitlementResponse.java",
    "entitlement/EntitlementService.java": "service/entitlement/EntitlementService.java",
    "plan/BillingInterval.java": "model/plan/BillingInterval.java",
    "plan/PlanCode.java": "model/plan/PlanCode.java",
    "plan/PlanEntity.java": "model/plan/PlanEntity.java",
    "plan/PlanRepository.java": "repository/plan/PlanRepository.java",
    "plan/PlanResponse.java": "model/plan/PlanResponse.java",
    "plan/PlanService.java": "service/plan/PlanService.java",
    "security/JwtAuthenticationFilter.java": "security/jwt/JwtAuthenticationFilter.java",
    "security/JwtClaims.java": "security/jwt/JwtClaims.java",
    "security/JwtProperties.java": "security/jwt/JwtProperties.java",
    "security/JwtService.java": "security/jwt/JwtService.java",
    "security/SecurityConfig.java": "security/config/SecurityConfig.java",
    "subscription/CheckoutPlan.java": "model/subscription/CheckoutPlan.java",
    "subscription/SubscriptionAccessDecision.java": "model/subscription/SubscriptionAccessDecision.java",
    "subscription/SubscriptionAccessPolicy.java": "service/subscription/SubscriptionAccessPolicy.java",
    "subscription/SubscriptionEntity.java": "model/subscription/SubscriptionEntity.java",
    "subscription/SubscriptionRepository.java": "repository/subscription/SubscriptionRepository.java",
    "subscription/SubscriptionStatus.java": "model/subscription/SubscriptionStatus.java",
    "user/MeController.java": "controller/user/MeController.java",
    "user/MeResponse.java": "model/user/MeResponse.java",
    "user/UserEntity.java": "model/user/UserEntity.java",
    "user/UserRepository.java": "repository/user/UserRepository.java",
    "user/UserResponse.java": "model/user/UserResponse.java",
    "user/UserService.java": "service/user/UserService.java",
    "webhook/ProcessingStatus.java": "model/webhook/ProcessingStatus.java",
    "webhook/WebhookController.java": "controller/webhook/WebhookController.java",
    "webhook/WebhookEventEntity.java": "model/webhook/WebhookEventEntity.java",
    "webhook/WebhookEventRepository.java": "repository/webhook/WebhookEventRepository.java",
    "webhook/WebhookEventStore.java": "service/webhook/WebhookEventStore.java",
    "webhook/WebhookService.java": "service/webhook/WebhookService.java",
    "webhook/WebhookSubscriptionProcessor.java": "service/webhook/WebhookSubscriptionProcessor.java",
}

TEST_MOVES = {
    "auth/GoogleAuthServiceTests.java": "service/auth/GoogleAuthServiceTests.java",
    "auth/GooglePropertiesValidationTests.java": "config/auth/GooglePropertiesValidationTests.java",
    "auth/GoogleWebClientProfileClientTests.java": "utilities/client/google/GoogleWebClientProfileClientTests.java",
    "plan/PlanServiceTests.java": "service/plan/PlanServiceTests.java",
    "security/JwtServiceTests.java": "security/jwt/JwtServiceTests.java",
}


def package_for(relative_path: str) -> str:
    parent = Path(relative_path).parent
    if str(parent) == ".":
        return "com.waypoint.backend"
    return "com.waypoint.backend." + ".".join(parent.parts)


def git_move(root: Path, old_relative: str, new_relative: str) -> None:
    old_path = root / old_relative
    new_path = root / new_relative
    if not old_path.exists():
        raise FileNotFoundError(old_path)
    new_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "mv", str(old_path), str(new_path)], check=True)


def rewrite_java(path: Path, package_name: str, project_packages: dict[str, str]) -> None:
    text = path.read_text(encoding="utf-8")
    package_pattern = re.compile(r"^package\s+[\w.]+;", re.MULTILINE)
    if not package_pattern.search(text):
        raise RuntimeError(f"Missing package declaration: {path}")
    text = package_pattern.sub(f"package {package_name};", text, count=1)

    # Remove old project imports. They are regenerated from the classes actually referenced.
    text = re.sub(
        r"^import\s+(?:static\s+)?com\.waypoint\.backend\.[^;]+;\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )

    own_class = path.stem
    imports: list[str] = []
    for class_name, target_package in project_packages.items():
        if class_name == own_class or target_package == package_name:
            continue
        if re.search(rf"\b{re.escape(class_name)}\b", text):
            imports.append(f"import {target_package}.{class_name};")

    if imports:
        import_block = "\n".join(sorted(set(imports)))
        package_match = package_pattern.search(text)
        assert package_match is not None
        insert_at = package_match.end()
        text = text[:insert_at] + "\n\n" + import_block + text[insert_at:]

    text = re.sub(r"\n{4,}", "\n\n\n", text)
    path.write_text(text, encoding="utf-8")


def validate_package_paths(root: Path) -> None:
    for path in root.rglob("*.java"):
        relative = path.relative_to(root)
        expected = package_for(str(relative))
        text = path.read_text(encoding="utf-8")
        match = re.search(r"^package\s+([\w.]+);", text, flags=re.MULTILINE)
        if match is None or match.group(1) != expected:
            actual = match.group(1) if match else "<missing>"
            raise RuntimeError(f"Package mismatch for {path}: expected {expected}, got {actual}")


def write_architecture_document() -> None:
    Path("docs/PACKAGE_STRUCTURE.md").write_text(
        """# Backend package structure

The backend follows a layered Spring Boot package structure. Each layer is split into feature-specific subpackages.

```text
com.waypoint.backend
├── controller
│   ├── advice
│   ├── auth
│   ├── billing
│   ├── entitlement
│   ├── user
│   └── webhook
├── model
│   ├── auth
│   ├── billing
│   ├── common
│   ├── entitlement
│   ├── plan
│   ├── subscription
│   ├── user
│   └── webhook
├── service
│   ├── auth
│   ├── billing
│   ├── entitlement
│   ├── plan
│   ├── subscription
│   ├── user
│   └── webhook
├── repository
│   ├── plan
│   ├── subscription
│   ├── user
│   └── webhook
├── utilities
│   ├── client
│   │   ├── google
│   │   └── lemonsqueezy
│   └── exception
├── config
│   ├── application
│   ├── auth
│   ├── billing
│   ├── client
│   └── logging
└── security
    ├── config
    └── jwt
```

- `controller`: HTTP endpoints and exception advice.
- `model`: entities, request/response records, enums and value objects.
- `service`: business rules and orchestration.
- `repository`: Spring Data persistence interfaces.
- `utilities`: external API clients and shared exceptions.
- `config`: application properties, startup validation, HTTP clients and logging configuration.
- `security`: Spring Security and JWT handling.
""",
        encoding="utf-8",
    )


def main() -> None:
    for old_relative, new_relative in MAIN_MOVES.items():
        git_move(MAIN_ROOT, old_relative, new_relative)
    for old_relative, new_relative in TEST_MOVES.items():
        git_move(TEST_ROOT, old_relative, new_relative)

    project_packages = {
        Path(new_relative).stem: package_for(new_relative)
        for new_relative in MAIN_MOVES.values()
    }
    project_packages["WaypointBackendApplication"] = "com.waypoint.backend"

    for path in MAIN_ROOT.rglob("*.java"):
        relative = str(path.relative_to(MAIN_ROOT))
        rewrite_java(path, package_for(relative), project_packages)
    for path in TEST_ROOT.rglob("*.java"):
        relative = str(path.relative_to(TEST_ROOT))
        rewrite_java(path, package_for(relative), project_packages)

    validate_package_paths(MAIN_ROOT)
    validate_package_paths(TEST_ROOT)
    write_architecture_document()


if __name__ == "__main__":
    main()
