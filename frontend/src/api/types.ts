export type Member = { id: number; email: string; nickname: string }

// ---------- AI 상담 ----------
export type Tone = 'empathy' | 'logic' | 'neutral'

export type DecisionDraft = {
  title: string
  category: string
  options: string[]
  content: string
}

export type ChatResponse = {
  reply: string
  parsedData: DecisionDraft | null
  isCrisis: boolean
}

// ---------- 결정 도우미 ----------
export type DecisionStyle = 'soft' | 'objective' | 'fact'
export type DecisionOption = { title: string; note: string }
export type Criterion = { name: string; weight: string }

export type ConsultRequest = {
  categories: string[]
  style: DecisionStyle
  title: string
  content: string
  conditions: string[]
  conditionsFree: string
  options: DecisionOption[]
  criteria: Criterion[]
}

export type ConsultResponse = { result: string; decisionId: number | null }

// ---------- 고민 기록 ----------
export type HistoryItem = {
  id: number
  title: string
  dilemma: string
  category: string
  createdAt: string
  options: DecisionOption[]
  result: string | null
  categories: string[]
  style: string | null
  conditions: string | null
  criteria: string | null
  bookmarked: boolean
  actualChoice: string | null
  satisfactionScore: number | null
  retrospectiveNote: string | null
}

// ---------- 커뮤니티 ----------
export type OptionView = { id: number; name: string; count: number; percent: number }
export type CommentView = { id: number; authorNickname: string; mine: boolean; content: string; createdAt: string }
export type PostView = {
  id: number
  title: string
  content: string
  category: string
  aiRecommendation: string | null
  authorNickname: string
  mine: boolean
  createdAt: string
  totalVotes: number
  options: OptionView[]
  myOptionId: number | null
  liked: boolean
  likeCount: number
  comments: CommentView[]
}
export type RankItem = { id: number; title: string; count: number }
export type CommunityFeed = {
  posts: PostView[]
  featuredPostId: number | null
  topLiked: RankItem[]
  topCommented: RankItem[]
  latestUpdate: string
  categories: string[]
}
