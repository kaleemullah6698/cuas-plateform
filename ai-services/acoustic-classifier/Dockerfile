# acoustic-classifier — ARCHITECTURE.md section 6.7
# Multi-stage: the runtime image excludes training dependencies.
FROM python:3.12-slim AS runtime

RUN useradd --create-home --shell /bin/false cuas
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src/ ./src/
COPY models/ ./models/

USER cuas
EXPOSE 9005
CMD ["python", "-m", "src.acoustic_classifier.main"]
