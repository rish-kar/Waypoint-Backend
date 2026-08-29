#!/usr/bin/env python3
"""Generate importable Postman v2.1 collections from the Git-synced YAML source."""

from __future__ import annotations

import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"


def load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def event_from_script(script: dict) -> dict | None:
    script_type = str(script.get("type", ""))
    if script_type in {"beforeRequest", "http:beforeRequest"}:
        listen = "prerequest"
    elif script_type in {"afterResponse", "http:afterResponse"}:
        listen = "test"
    else:
        return None

    code = str(script.get("code", ""))
    return {
        "listen": listen,
        "script": {
            "type": script.get("language", "text/javascript"),
            "exec": code.splitlines(),
        },
    }


def header_items(headers: object) -> list[dict]:
    if not headers:
        return []
    if isinstance(headers, dict):
        return [{"key": str(key), "value": str(value)} for key, value in headers.items()]
    if isinstance(headers, list):
        result = []
        for header in headers:
            if isinstance(header, dict) and "key" in header:
                result.append({"key": str(header["key"]), "value": str(header.get("value", ""))})
        return result
    raise ValueError(f"Unsupported headers shape: {type(headers).__name__}")


def request_body(body: object) -> dict | None:
    if not isinstance(body, dict):
        return None
    content = body.get("content")
    if content is None:
        return None
    raw = str(content)
    result = {"mode": "raw", "raw": raw}
    if body.get("type") == "json":
        result["options"] = {"raw": {"language": "json"}}
    return result


def request_item(path: Path) -> tuple[int, str, dict]:
    source = load_yaml(path)
    name = path.name.removesuffix(".request.yaml")
    scripts = [event for script in source.get("scripts", []) if (event := event_from_script(script))]

    request = {
        "method": str(source.get("method", "GET")).upper(),
        "header": header_items(source.get("headers")),
        "url": str(source.get("url", "")),
    }
    if source.get("description"):
        request["description"] = str(source["description"])
    body = request_body(source.get("body"))
    if body is not None:
        request["body"] = body

    item = {"name": name}
    if scripts:
        item["event"] = scripts
    item["request"] = request
    return int(source.get("order", 999999)), name, item


def folder_item(path: Path) -> tuple[int, str, dict]:
    definition_path = path / ".resources" / "definition.yaml"
    definition = load_yaml(definition_path) if definition_path.exists() else {}
    requests = [request_item(request) for request in path.glob("*.request.yaml")]
    requests.sort(key=lambda entry: (entry[0], entry[1].lower()))

    folder = {
        "name": path.name,
        "item": [entry[2] for entry in requests],
    }
    if definition.get("description"):
        folder["description"] = str(definition["description"])
    return int(definition.get("order", 999999)), path.name, folder


def generate_collection(source: Path, output: Path, default_id: str, default_name: str) -> None:
    root_definition = load_yaml(source / ".resources" / "definition.yaml")
    folders = [folder_item(path) for path in source.iterdir() if path.is_dir() and path.name != ".resources"]
    folders.sort(key=lambda entry: (entry[0], entry[1].lower()))

    collection = {
        "info": {
            "_postman_id": str(root_definition.get("id", default_id)),
            "name": str(root_definition.get("name", default_name)),
            "description": str(root_definition.get("description", "")),
            "schema": SCHEMA,
        },
        "item": [entry[2] for entry in folders],
    }

    root_events = [
        event
        for script in root_definition.get("scripts", [])
        if (event := event_from_script(script))
    ]
    if root_events:
        collection["event"] = root_events

    output.write_text(json.dumps(collection, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    parsed = json.loads(output.read_text(encoding="utf-8"))
    assert parsed["info"]["schema"] == SCHEMA
    assert [folder["name"] for folder in parsed["item"]] == [entry[1] for entry in folders]


def main() -> None:
    generate_collection(
        ROOT / "postman" / "collections" / "Waypoint-Backend",
        ROOT / "postman" / "Waypoint-Backend.postman_collection.json",
        "aa93dc55-52d9-4b40-84f0-0d397a7301a8",
        "Waypoint Backend API",
    )
    generate_collection(
        ROOT / "postman" / "collections" / "Waypoint-Metrics",
        ROOT / "postman" / "Waypoint-Metrics.postman_collection.json",
        "5fa4b539-9d7d-4e87-92e3-11c7286b7247",
        "Waypoint Metrics",
    )


if __name__ == "__main__":
    main()
