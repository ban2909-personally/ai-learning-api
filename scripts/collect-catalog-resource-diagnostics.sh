#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export DIAGNOSTICS_WORKLOAD_KIND='catalog'
exec "$script_directory/collect-resource-diagnostics.sh"
