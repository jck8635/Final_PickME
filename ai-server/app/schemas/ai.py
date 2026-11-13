from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str = ""
    tone: str = "empathy"


class ParsedDecision(BaseModel):
    title: str
    category: str
    options: list[str]


class ChatResponse(BaseModel):
    reply: str
    parsed_data: ParsedDecision | None = None
    is_crisis: bool = False


class DecisionOption(BaseModel):
    title: str = Field(max_length=200)
    note: str = Field(default="", max_length=500)


class DecisionRequest(BaseModel):
    categories: list[str] = Field(default_factory=list, max_length=10)
    style: str = "objective"
    title: str = Field(default="", max_length=200)
    content: str = Field(max_length=5000)
    conditions: str = Field(default="", max_length=1000)
    options: list[DecisionOption] = Field(default_factory=list, max_length=5)
    criteria: str = Field(default="", max_length=1000)


class DecisionResponse(BaseModel):
    result: str
