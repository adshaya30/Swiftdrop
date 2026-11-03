import {
  createContext,
  useContext,
  useState,
  useEffect,
  type ReactNode,
} from 'react';
import {
  loginCustomer,
  logoutCustomer,
  refreshToken,
  type LoginPayload,
  type LoginResponse,
  type LoginUserSummary,
  type UserSummary,
} from '../services/authService';
import { setAuthToken, setSessionExpiredCallback } from '../api/axios';

interface AuthContextType {
  user: LoginUserSummary | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (payload: LoginPayload) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<LoginResponse>;
  registeredUser: UserSummary | null;
  setRegisteredUser: (user: UserSummary | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [registeredUser, setRegisteredUser] = useState<UserSummary | null>(null);
  // In-memory token and authenticated user storage only - never localStorage / sessionStorage
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<LoginUserSummary | null>(null);
  const [isInitializing, setIsInitializing] = useState<boolean>(true);

  useEffect(() => {
    setSessionExpiredCallback(() => {
      setAccessToken(null);
      setUser(null);
      setAuthToken(null);
    });

    let isMounted = true;

    // Silent session recovery on initial load / reload
    const initAuth = async () => {
      try {
        const data = await refreshToken();
        if (isMounted) {
          setAccessToken(data.accessToken);
          setUser(data.user);
          setAuthToken(data.accessToken);
        }
      } catch {
        // No valid session cookie found or expired — stay unauthenticated
        if (isMounted) {
          setAccessToken(null);
          setUser(null);
          setAuthToken(null);
        }
      } finally {
        if (isMounted) {
          setIsInitializing(false);
        }
      }
    };

    initAuth();

    return () => {
      isMounted = false;
      setSessionExpiredCallback(null);
    };
  }, []);

  const login = async (payload: LoginPayload): Promise<LoginResponse> => {
    const data = await loginCustomer(payload);
    setAccessToken(data.accessToken);
    setUser(data.user);
    setAuthToken(data.accessToken);
    return data;
  };

  const refreshSession = async (): Promise<LoginResponse> => {
    const data = await refreshToken();
    setAccessToken(data.accessToken);
    setUser(data.user);
    setAuthToken(data.accessToken);
    return data;
  };

  const logout = async (): Promise<void> => {
    try {
      await logoutCustomer();
    } catch {
      // Even if network or server fails, ensure local session is wiped
    } finally {
      setAccessToken(null);
      setUser(null);
      setAuthToken(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!accessToken,
        isInitializing,
        login,
        logout,
        refreshSession,
        registeredUser,
        setRegisteredUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
