import { expect, type Page } from '@playwright/test';
export function guard(){if(process.env.E2E_TEST_DB_CONFIRMED !== 'yes') throw new Error('테스트 DB 연결 확인 후 E2E_TEST_DB_CONFIRMED=yes 설정');}
export function env(key:string){const v=process.env[key];if(!v)throw new Error(`${key} 설정 필요`);return v;}
export function response(page:Page,path:string,method='GET'){return page.waitForResponse(r=>new URL(r.url()).pathname===`/api${path}` && r.request().method()===method,{timeout:320000});}
export async function login(page:Page){guard();await page.goto('/login');await page.locator('#email').fill(env('E2E_EMAIL'));await page.locator('#password').fill(env('E2E_PASSWORD'));const pending=response(page,'/auth/login','POST');await page.getByRole('button',{name:'로그인',exact:true}).click();const r=await pending;expect(r.ok()).toBeTruthy();await expect(page.getByRole('link',{name:'마이페이지',exact:true})).toBeVisible();return r;}
