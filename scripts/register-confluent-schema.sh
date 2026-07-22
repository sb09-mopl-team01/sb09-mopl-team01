#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <schema-path> <subject>" >&2
  exit 64
fi

schema_path="$1"
subject="$2"

for required_var in KAFKA_SCHEMA_REGISTRY_URL KAFKA_SCHEMA_REGISTRY_API_KEY KAFKA_SCHEMA_REGISTRY_API_SECRET; do
  if [[ -z "${!required_var:-}" ]]; then
    echo "Required environment variable is missing: $required_var" >&2
    exit 64
  fi
done

if [[ ! -f "$schema_path" ]]; then
  echo "Schema file does not exist: $schema_path" >&2
  exit 66
fi

registry_url="${KAFKA_SCHEMA_REGISTRY_URL%/}"
schema_json="$(jq -c . "$schema_path")"
request_body="$(jq -nc --arg schema "$schema_json" '{schema: $schema, schemaType: "JSON"}')"
compatibility_url="$registry_url/config/$subject"
versions_url="$registry_url/subjects/$subject/versions"

auth_args=(--user "$KAFKA_SCHEMA_REGISTRY_API_KEY:$KAFKA_SCHEMA_REGISTRY_API_SECRET")
json_header=(-H 'Content-Type: application/vnd.schemaregistry.v1+json')

curl --fail-with-body --silent --show-error "${auth_args[@]}" "${json_header[@]}" \
  -X PUT "$compatibility_url" --data '{"compatibility":"BACKWARD"}' >/dev/null

if curl --fail --silent "${auth_args[@]}" "$versions_url" >/dev/null 2>&1; then
  compatibility_check_url="$registry_url/compatibility/subjects/$subject/versions/latest"
  check_result="$(curl --fail-with-body --silent --show-error "${auth_args[@]}" "${json_header[@]}" \
    -X POST "$compatibility_check_url" --data "$request_body")"
  if [[ "$(jq -r '.is_compatible' <<< "$check_result")" != "true" ]]; then
    echo "Schema is not backward compatible. subject=$subject" >&2
    exit 1
  fi
fi

version_result="$(curl --fail-with-body --silent --show-error "${auth_args[@]}" "${json_header[@]}" \
  -X POST "$versions_url" --data "$request_body")"
echo "Confluent Schema Registry version registered. subject=$subject version=$(jq -r '.id' <<< "$version_result")"
