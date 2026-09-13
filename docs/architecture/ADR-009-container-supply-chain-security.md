# ADR-009 — Container supply-chain security

Status: accepted for Phase 8.2

Date: 2026-09-11

## Context

Phase 8.1 verifies the container runtime contract. Delivery still needs a component inventory and a gate against known vulnerabilities in the shipped image. The March 2026 Trivy incident also makes immutable scanner inputs necessary.

## Decisions

- Generate a CycloneDX 1.6 JSON application SBOM with Maven plugin 2.9.3 during `verify`, after tests and coverage. Include compile/runtime/provided/system scopes, excluding test-only dependencies. This is a dependency inventory, not a claim that every provided dependency is shipped.
- Generate a separate CycloneDX SBOM of the final image, including OS packages and packaged Java libraries. Keep both inventories as separate CI artifacts for 14 days, named with the source commit.
- Pin Trivy 0.74.0 to official image index digest `sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969`. Scan an exported archive of the image just built; do not expose the Docker socket to the scanner.
- Pin all workflow actions and the Dockerfile frontend to immutable revisions, retaining readable versions in comments.
- Retain a complete vulnerability report, including unfixed findings. Block fixed HIGH and CRITICAL vulnerabilities in either OS packages or application dependencies. Do not relax severity to make an existing image pass.
- Scan source/configuration for secrets and scan image content and metadata for secrets. Image secret scanning alone cannot prove absence of secrets in compressed JAR resources. Do not publish secret scan output as an artifact.
- Refresh vulnerability databases on each scan invocation; download/scan failures fail the gate. No blanket ignore list, `continue-on-error`, or missing-report success path.
- A finding requires a dependency/base-image fix and regression verification, or an explicit approved, expiring exception with the finding id, owner, rationale and mitigation.

## Delivery

Maven verification and application SBOM precede image build, runtime assertions, image export, vulnerability reporting, blocking scans, and image SBOM. Each output must be non-empty and identify its component scope. Upload vulnerability evidence even if the vulnerability gate fails, but never secrets, the exported archive, local environment files, or credentials. Failure blocks merge.

This phase adds no business types or changes to API/database contracts. Signing, provenance, registry publication, continuous scanning of released images, cloud deployment, and license acceptance remain separate work.

## Primary references

- [CycloneDX Maven goals](https://cyclonedx.github.io/cyclonedx-maven-plugin/makeAggregateBom-mojo.html)
- [Trivy 0.74.0](https://github.com/aquasecurity/trivy/releases/tag/v0.74.0)
- [Trivy incident disclosure](https://github.com/aquasecurity/trivy/discussions/10425)
- [GitHub action pinning](https://docs.github.com/en/actions/reference/security/secure-use)
