import {test,expect} from '@playwright/test';import {guard,login} from './support';
test('비인증 보호 API 차단',async({request})=>{guard();expect((await request.get('/api/history/page')).status()).toBe(401);expect((await request.get('/api/admin/overview')).status()).toBe(401);});
test('일반 회원 JWT로 관리자 API 접근 차단',async({page,request})=>{const r=await login(page);const token=(await r.json()).accessToken;expect(typeof token).toBe('string');expect((await request.get('/api/admin/overview',{headers:{Authorization:`Bearer ${token}`}})).status()).toBe(403);});
