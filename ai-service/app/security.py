import secrets
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status

from app.config import Settings, get_settings


def require_internal_key(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_api_key: Annotated[str | None, Header()] = None,
) -> None:
    """Only the Medfold backend may call /v1 endpoints.

    Rejects every request when no key is configured, so a missing
    AI_INTERNAL_KEY never leaves the service open.
    """
    expected = settings.ai_internal_key
    if not expected or not x_internal_api_key or not secrets.compare_digest(
        x_internal_api_key, expected
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing internal API key",
        )
