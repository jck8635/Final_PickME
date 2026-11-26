import {test,expect} from '@playwright/test';
import {session,unique,postData} from './extended-support';
test('API: 신고 접수, 중복 신고 차단, 관리자 반려',async({page,browser})=>{
  const api=await session(page); const title=unique();
  const {id}=await api('POST','/community/posts',postData(title),201);
  const ctx=await browser.newContext({baseURL:'http://localhost:5173'});
  try {
    await api('POST',`/community/posts/${id}/reports`,{reason:'테스트 신고'},201);
    await api('POST',`/community/posts/${id}/reports`,{reason:'중복 테스트 신고'},400);
    const admin=await session(await ctx.newPage(),true);
    const query=`/admin/reports?q=${encodeURIComponent(title)}`;
    const list=await admin('GET',query+'&status=PENDING');
    const report=list.items.find((x:any)=>x.postId===id); expect(report).toBeDefined();
    await admin('PATCH',`/admin/reports/${report.id}/reject`);
    expect((await admin('GET',query+'&status=REJECTED')).items.some((x:any)=>x.id===report.id)).toBe(true);
  } finally {await ctx.close(); await api('DELETE',`/community/posts/${id}`);}
});
