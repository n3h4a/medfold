from fastapi import FastAPI

from app.routers import health

app = FastAPI(
    title="Medfold AI Service",
    description="Internal service for OCR and structured extraction. Not exposed publicly.",
    version="0.1.0",
)

app.include_router(health.router)
