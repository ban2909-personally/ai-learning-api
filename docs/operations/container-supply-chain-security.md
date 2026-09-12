# Container supply-chain verification

## Run locally

Requirements: Java 21, Maven, a working Linux-container Docker engine, Bash, and access to Maven Central, Docker Hub and Trivy's vulnerability database registries.

From the repository root:

```bash
mvn --batch-mode --no-transfer-progress clean verify
docker build --tag ai-learning-api:ci .
bash scripts/verify-container-security.sh
```

Do not skip integration tests to push a change. Docker is required for both Testcontainers and image scanning. The script normalizes bind-mount paths under Git Bash on Windows; WSL/Linux use native paths.

## Outputs and scope

- `target/application-sbom.json`: CycloneDX 1.6 inventory of Maven non-test dependencies. Spring Modulith brings ArchUnit core as a runtime dependency, so its presence is expected. JUnit integrations, Mockito and Testcontainers should be absent.
- `target/security/image-sbom.json`: inventory of the final built image's OS and packaged application dependencies.
- `target/security/vulnerabilities.json`: complete known-vulnerability report, including findings that do not yet have fixes.
- `target/production-image.tar`: the actual local image supplied to the scanner. This archive is not published.
- `target/trivy-cache`: disposable scanner cache; database freshness is checked by the scanner and updates must be allowed.

Only the two inventories and the vulnerability report are uploaded, under commit-specific artifact names with 14-day retention. They are internal build evidence, not a public product endpoint or a long-term compliance archive. Their visibility follows repository/Actions permissions. No customer data or runtime credentials should enter the build context.

## Failure handling

The script stops on scanner errors, detected secrets, or fixed HIGH/CRITICAL vulnerabilities. A JSON vulnerability report may still be available in a failed CI run. A missing/empty report is not a clean bill of health. The report identifies the installed and fixed versions; upgrade the direct dependency/BOM or base-image digest and rerun the full gates. Do not lower the threshold or add blanket exclusions.

Unfixed findings remain visible for manual triage: evaluate reachable functionality, compensating controls and upgrade availability. An exception requires explicit approval and must name the finding/package, owner, expiry, rationale and mitigation. There is currently no exception file.

If a real secret is found, revoke/rotate it through its provider, remove it from source/configuration and assess prior image and Git history exposure. Never copy secret values into tickets, reports, or uploaded artifacts. The script scans source resources as well as image content/metadata because compressed JAR resources are not reliably covered by plaintext secret scanning.

## Updating tools

Verify scanner releases and image digests in the upstream project, update the pinned scanner reference in `scripts/verify-container-security.sh`, and record provenance in the ADR/report. Verify GitHub Action SHAs against the original repositories. Tool or database failures must remain failures. Vulnerability data can change without source changes, so a previously green commit can correctly fail a later scan.

Signing, provenance attestation, registry retention and periodic rescanning of releases remain separate roadmap work.
