FROM python:3.12-slim

WORKDIR /app
ENV PYTHONPATH=/app/src \
    PYTHONUNBUFFERED=1

COPY pyproject.toml ./
COPY worker.py ./
COPY src ./src

RUN pip install --no-cache-dir "mistralai-workflows>=3.10"

CMD ["python", "worker.py"]
