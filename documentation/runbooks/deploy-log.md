# Deploy Log

One entry per build and deploy of prod, newest last. No secrets. The procedure is in [Initial deployment](initial-deployment.md) and
[Deploying an update](update-deployment.md).

## 2026-11-12: first build, not yet deployed

- Commit `a13eb6a` on branch `prod-deployment`. Both images built for `linux/arm64` on the developer's x86 machine (the Keycloak one under
  QEMU emulation) and pushed to ECR in `us-west-2`.
- App: `$EVEASSIST_ACCOUNT_ID.dkr.ecr.us-west-2.amazonaws.com/eveassist-app:0.1.0-a13eb6a`, digest
  `sha256:436225ac5ba22480a82aa16d1ecdb1a3129f159f74638db895d73f1260f29316`.
- Keycloak: `$EVEASSIST_ACCOUNT_ID.dkr.ecr.us-west-2.amazonaws.com/eveassist-keycloak:0.1.0-a13eb6a`, digest
  `sha256:6362f3bd77c2f6537639b697936b32a4bd2d23e1f8241ce08db1ffa02c9de2c6`. It carries the realm file of that commit.
- SDE in RDS: Fuzzwork build 3687575, 2026-11-11.
- RDS instance `eveassist-prod`, EC2 `$EVEASSIST_INSTANCE_ID`. No RDS snapshot taken yet.
