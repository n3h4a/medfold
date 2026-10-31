FROM python:3.12-slim

# Tesseract OCR with English and orientation detection. PDF pages are rendered with pypdfium2
# (bundled in the Python wheel), so no Poppler is needed.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-osd \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Exact versions from the lock file first (cached until requirements.txt changes), then the app.
COPY requirements.txt ./
RUN pip install -r requirements.txt
COPY pyproject.toml ./
COPY app ./app
RUN pip install --no-deps .

RUN useradd --create-home --uid 1000 medfold
USER medfold

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
