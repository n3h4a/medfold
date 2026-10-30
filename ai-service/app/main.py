from fastapi import FastAPI

from app.errors import register_error_handlers
from app.routers import health, ocr

app = FastAPI(
    title="Medfold AI Service",
    description="Internal service for OCR and structured extraction. Not exposed publicly.",
    version="0.1.0",
)

register_error_handlers(app)
app.include_router(health.router)
app.include_router(ocr.router)
