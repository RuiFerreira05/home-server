FROM ghcr.io/astral-sh/uv:alpine

RUN mkdir /notebooks
WORKDIR /notebooks

RUN uv tool install "marimo"

EXPOSE 8080
ENTRYPOINT [ "/bin/sh", "-c", "marimo edit --headless --host 0.0.0.0 --port 8080 --token-password $MARIMO_PASSWORD" ]
