import { defineConfig, devices } from '@playwright/test';
export default defineConfig({testDir:'./e2e', timeout:360000, expect:{timeout:15000}, workers:1, retries:0, reporter:[['list'],['html',{open:'never'}]], use:{...devices['Desktop Chrome'],baseURL:'http://localhost:5173',trace:'off',screenshot:'off',video:'off'}});
