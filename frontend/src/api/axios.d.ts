import type { AxiosInstance } from 'axios';

declare const api: AxiosInstance;
export default api;

export function setAuthToken(token: string | null): void;
export function getAuthToken(): string | null;
export function setSessionExpiredCallback(callback: (() => void) | null): void;
