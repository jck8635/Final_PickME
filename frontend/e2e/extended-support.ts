import { expect, type Page } from '@playwright/test';
import { login, guard, env, response } from './support';
export async function session(page: Page, admin = false) {
  guard();
  let r;
  if (admin) {
    await page.goto('/admin/login');
    await page.locator('#admin-username').fill(env('E2E_ADMIN_USERNAME'));
    await page.locator('#admin-password').fill(env('E2E_ADMIN_PASSWORD'));
    const pending = response(page, '/admin/auth/login', 'POST');
    await page.getByRole('button', {name:'관리자 로그인',exact:true}).click();
    r = await pending;
    expect(r.ok(), `admin login: ${r.status()}`).toBeTruthy();
  } else r = await login(page);
  const token = (await r.json()).accessToken;
  expect(typeof token).toBe('string');
  return async (method: string, path: string, data?: unknown, status = 200, extra: Record<string,string> = {}) => {
    const cookie = (await page.context().cookies()).find(c => c.name === 'XSRF-TOKEN');
    const headers: Record<string,string> = {Authorization: `Bearer ${token}`, ...extra};
    if (cookie) headers['X-XSRF-TOKEN'] = decodeURIComponent(cookie.value);
    const res = await page.request.fetch(`/api${path}`, {method, headers, ...(data === undefined ? {} : {data}), timeout: 320000});
    expect(res.status(), `${method} ${path}`).toBe(status);
    return res.json();
  };
}
export type Api = Awaited<ReturnType<typeof session>>;
export const unique = () => `E2E-${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
export const postData = (title: string) => ({title,content:'점심에 비빔밥과 국수 중 선택하려고 합니다.',category:'음식/식사',options:['비빔밥','국수']});
export const decisionData = (title: string) => ({title,content:`${title} 점심 메뉴로 비빔밥과 국수 중 선택하고 싶습니다.`,categories:['음식/식사'],style:'objective',conditions:[],options:[{title:'비빔밥',note:''},{title:'국수',note:''}],criteria:[],result:'테스트용 상담 결과: 비빔밥을 선택합니다.'});
