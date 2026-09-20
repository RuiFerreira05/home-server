FROM ghcr.io/astral-sh/uv:alpine

RUN mkdir /notebooks
WORKDIR /notebooks

RUN uv tool install "marimo"

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/health || exit 1

EXPOSE 8080
ENTRYPOINT [ "/bin/sh", "-c", "marimo edit --headless --host 0.0.0.0 --port 8080 --token-password $MARIMO_PASSWORD" ]
