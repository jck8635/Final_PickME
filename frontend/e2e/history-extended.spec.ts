import {test,expect} from '@playwright/test';
import {session,unique,decisionData} from './extended-support';
test('API: 고민기록 저장, 키워드 검색, 북마크, 회고 및 통계, 삭제',async({page})=>{
  const api=await session(page); const title=unique();
  const baseline=(await api('GET','/history/stats')).summary;
  const {decisionId:id}=await api('POST','/decisions/save',decisionData(title));
  const path=`/history/${id}`;
  try {
    expect((await api('GET',path)).title).toBe(title);
    const list=await api('GET',`/history/page?q=${encodeURIComponent(title)}`);
    expect(list.items.some((x:any)=>x.id===id)).toBe(true);
    expect((await api('POST',`${path}/bookmark`)).bookmarked).toBe(true);
    expect((await api('GET',path)).bookmarked).toBe(true);
    expect((await api('POST',`${path}/bookmark`)).bookmarked).toBe(false);
    expect((await api('GET','/history/stats')).summary.totalDecisions).toBe(baseline.totalDecisions+1);
    for(const score of [4,5]) {
      await api('PUT',`${path}/retrospective`,{actualChoice:'비빔밥',satisfactionScore:score,retrospectiveNote:`만족도 ${score}`});
      const item=await api('GET',path); expect(item.satisfactionScore).toBe(score); expect(item.retrospectiveNote).toBe(`만족도 ${score}`);
      expect((await api('GET','/history/stats')).summary.retrospectiveCount).toBe(baseline.retrospectiveCount+1);
    }
    await api('PUT',`${path}/retrospective`,{actualChoice:'',satisfactionScore:null,retrospectiveNote:''});
    expect((await api('GET',path)).satisfactionScore).toBeNull();
    expect((await api('GET','/history/stats')).summary.retrospectiveCount).toBe(baseline.retrospectiveCount);
  } finally {await api('DELETE',path);}
  await api('GET',path,undefined,404);
  expect((await api('GET','/history/stats')).summary.totalDecisions).toBe(baseline.totalDecisions);
});
