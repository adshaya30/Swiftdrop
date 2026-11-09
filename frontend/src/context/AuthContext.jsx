import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import * as authApi from '../api/auth';
import { clearAccessToken } from '../api/http';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading'); // 'loading' | 'authenticated' | 'anonymous'

  // Restore session at load with POST /api/v1/auth/refresh
  useEffect(() => {
    let isMounted = true;

    async function initAuth() {
      try {
        const result = await authApi.refresh();
        if (isMounted && result && result.user) {
          setUser(result.user);
          setStatus('authenticated');
        } else if (isMounted) {
          setUser(null);
          setStatus('anonymous');
        }
      } catch {
        if (isMounted) {
          setUser(null);
          setStatus('anonymous');
        }
      }
    }

    initAuth();

    return () => {
      isMounted = false;
    };
  }, []);

  const login = useCallback(async (credentials) => {
    const result = await authApi.login(credentials);
    if (result && result.user) {
      setUser(result.user);
      setStatus('authenticated');
    }
    return result;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      clearAccessToken();
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const refresh = useCallback(async () => {
    try {
      const result = await authApi.refresh();
      if (result && result.user) {
        setUser(result.user);
        setStatus('authenticated');
        return result;
      }
      setUser(null);
      setStatus('anonymous');
      return null;
    } catch (err) {
      setUser(null);
      setStatus('anonymous');
      throw err;
    }
  }, []);

  const hasRole = useCallback((roleOrRoles) => {
    if (!user || !user.role) return false;
    if (Array.isArray(roleOrRoles)) {
      return roleOrRoles.includes(user.role);
    }
    return user.role === roleOrRoles;
  }, [user]);

  const value = {
    user,
    status,
    login,
    logout,
    refresh,
    hasRole,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
