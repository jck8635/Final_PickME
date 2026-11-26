import {test,expect} from '@playwright/test';
import {session,unique,decisionData} from './extended-support';
test('API: 실제 임베딩 생성 및 RAG 재정렬·요약',async({page})=>{
  test.skip(process.env.E2E_AI!=='yes','실제 AI 호출은 선택 실행');
  test.setTimeout(600000);
  const api=await session(page); const title=unique()+' 비빔밥 점심 메뉴';
  const {decisionId:id}=await api('POST','/decisions/save',decisionData(title));
  try {
    const e=await api('POST',`/history/${id}/embedding`);
    expect(e.decisionId).toBe(id); expect(e.dimension).toBe(768); expect(e.status).toBe('saved');
    const result=await api('POST','/history/similar',{text:title});
    expect(result.mode).toBe('reranked'); expect(result.results.length).toBeLessThanOrEqual(3);
    expect(result.results.some((x:any)=>x.decisionId===id)).toBe(true);
    expect(typeof result.summary).toBe('string'); expect(result.summary.trim().length).toBeGreaterThan(0);
    for(const row of result.results) {expect(row.relevance).toBeGreaterThanOrEqual(70); await api('GET',`/history/${row.decisionId}`);}
  } finally {await api('DELETE',`/history/${id}`);}
});
