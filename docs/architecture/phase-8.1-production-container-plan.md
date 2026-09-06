# Phase 8.1 — Production container hardening plan

## Definition of done

- [x] Confirm Phase 7.3a feature CI, merge-time verification, main CI, and synchronized repository state.
- [x] Inspect current Maven, runtime configuration, CI, health probes, and infrastructure assumptions.
- [x] Record the container, non-root, secret, health, CI, and deferral decisions before implementation.
- [ ] Add a digest-pinned multi-stage Dockerfile and minimal build context.
- [ ] Add a fail-closed production Spring profile with graceful shutdown and bounded runtime settings.
- [ ] Add an operator runbook for required configuration, probes, filesystem, shutdown, and local image validation.
- [ ] Extend CI so a complete Maven gate must pass before the container image is built and inspected.
- [ ] Build and inspect the image locally, test production configuration failure/success paths, and rerun full Maven verification.
- [ ] Perform diff, secret, image-content, and root-user audits.
- [ ] Push the feature, wait for exact CI success, merge no-fast-forward, rerun all gates, push main, and wait for exact main CI success.
- [ ] Publish a development report with changed files, security, performance, tests, commits, and CI evidence.

## Controlled implementation sequence

1. Container and production configuration ADR.
2. Docker build/runtime boundary and `.dockerignore`.
3. Production profile and configuration contract.
4. Operations runbook and README entry point.
5. CI build and image metadata assertions.
6. Local Maven, container build, metadata, startup, failure-path, and content audits.
7. Feature CI, merge-time verification, and main CI.

## Explicitly deferred

- cloud provider, cluster, ingress, TLS certificates, and DNS;
- image registry publication, signing, provenance attestation, and release promotion;
- SBOM/vulnerability policy and dependency-update automation;
- Kubernetes/ECS/Nomad manifests and autoscaling values;
- database/MinIO backup and restore drills;
- production SLO targets, capacity tests, dashboards, and alert routing;
- payment and invitation delivery providers.
