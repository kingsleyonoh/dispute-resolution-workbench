# Wkhtmltopdf Is Missing From Temurin Alpine Runtime

- **Symptom:** Docker image build fails at `RUN apk add --no-cache wkhtmltopdf` with `wkhtmltopdf (no such package)`.
- **Cause:** The Alpine repositories available to `eclipse-temurin:22-jre-alpine` do not provide a `wkhtmltopdf` package.
- **Solution:** Do not install `wkhtmltopdf` in the Alpine runtime image. When PDF work lands, switch to a runtime base image/package source that supplies the binary or add a verified dedicated binary layer.
- **Discovered in:** Dispute Resolution Workbench, Batch 002, 2026-05-05.
- **Affects:** Dockerfile changes that try to install PDF tooling on Alpine-based Temurin images.
