#!/bin/bash
# Creates one database per service. Database-per-service is absolute
# (ARCHITECTURE.md section 4.1): no service reads another's tables.
#
# Databases required: authdb userdb sensordb trackdb threatdb
#                     alertdb notifydb reportdb auditdb configdb
#
# Scaffold only — not implemented.
set -euo pipefail
echo "init-databases.sh: not implemented"
