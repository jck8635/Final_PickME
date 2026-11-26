import {test,expect} from '@playwright/test';
import {session,unique,decisionData} from './extended-support';
import {randomUUID} from 'node:crypto';
test('API: 결정 상담 및 실제 결과 저장',async({page})=>{
  test.skip(process.env.E2E_AI!=='yes','실제 AI 호출은 선택 실행'); test.setTimeout(600000);
  const api=await session(page); const data=decisionData(unique());
  const headers={'X-Counseling-Session':randomUUID()};
  let result=await api('POST','/decisions/consult',data,200,headers);
  expect(Array.isArray(result.questions)).toBe(true);
  if(!result.result){expect(result.questions.length).toBeGreaterThan(0); result=await api('POST','/decisions/consult',data,200,{...headers,'X-Decision-Proceed':'true'});}
  expect(typeof result.result).toBe('string'); expect(result.result.trim().length).toBeGreaterThan(0);
  const {decisionId:id}=await api('POST','/decisions/save',{...data,result:result.result});
  try {const saved=await api('GET',`/history/${id}`); expect(saved.title).toBe(data.title); expect(saved.result).toBe(result.result);}
  finally {await api('DELETE',`/history/${id}`);}
});
