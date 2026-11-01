import { createContext, useContext, useState, type ReactNode } from 'react';
import {
  loginCustomer,
  type LoginPayload,
  type LoginResponse,
  type LoginUserSummary,
  type UserSummary,
} from '../services/authService';

interface AuthContextType {
  user: LoginUserSummary | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  login: (payload: LoginPayload) => Promise<LoginResponse>;
  logout: () => void;
  registeredUser: UserSummary | null;
  setRegisteredUser: (user: UserSummary | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [registeredUser, setRegisteredUser] = useState<UserSummary | null>(null);
  // In-memory token and authenticated user storage only - never localStorage / sessionStorage
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<LoginUserSummary | null>(null);

  const login = async (payload: LoginPayload): Promise<LoginResponse> => {
    const data = await loginCustomer(payload);
    setAccessToken(data.accessToken);
    setUser(data.user);
    return data;
  };

  const logout = () => {
    setAccessToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!accessToken,
        login,
        logout,
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
