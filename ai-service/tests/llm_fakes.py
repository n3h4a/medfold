import json

from app.llm.base import LlmProvider, LlmRequest, LlmResponse


class ScriptedLlmProvider(LlmProvider):
    """Returns scripted answers (or raises scripted errors) in order and records every request."""

    name = "scripted"
    model = "scripted-model"

    def __init__(self, *answers) -> None:
        self.answers = list(answers)
        self.requests: list[LlmRequest] = []

    def complete_json(self, request: LlmRequest) -> LlmResponse:
        self.requests.append(request)
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        if isinstance(answer, LlmResponse):
            return answer
        text = answer if isinstance(answer, str) else json.dumps(answer)
        return LlmResponse(text=text, model=self.model, stop_reason="end_turn", input_tokens=100, output_tokens=50)


VALID = {"docType": "LAB_REPORT", "summary": "This lab report lists blood test results."}
