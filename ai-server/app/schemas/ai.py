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
    
    # 마이페이지 사용자 정보 양식
# 백엔드가 DB 에서 읽어 보내는 값  입력안한 항목은 None
class UserProfile(BaseModel):
    region: str | None = None
    occupation: str | None = None
    mbti : str | None = None 
    interests : list[str] | None = None
    dislikedFoods: list[str] | None= None
    bio : str | None=None



class DecisionRequest(BaseModel):
    categories: list[str] = Field(default_factory=list, max_length=10)
    style: str = "objective"
    title: str = Field(default="", max_length=200)
    content: str = Field(max_length=5000)
    conditions: str = Field(default="", max_length=1000)
    options: list[DecisionOption] = Field(default_factory=list, max_length=5)
    criteria: str = Field(default="", max_length=1000)
    # 추가비로그인이면 None
    user_profile: UserProfile | None = None

    
class AgentPlanning(BaseModel):
    request: DecisionRequest


class AgentEvaluation(BaseModel):
    request: DecisionRequest
    analysis: dict
    evidence: dict


class DecisionResponse(BaseModel):
    result: str
