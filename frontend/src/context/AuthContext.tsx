import { createContext, useContext, useState, type ReactNode } from 'react';
import type { UserSummary } from '../services/authService';

interface AuthContextType {
  registeredUser: UserSummary | null;
  setRegisteredUser: (user: UserSummary | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [registeredUser, setRegisteredUser] = useState<UserSummary | null>(null);

  return (
    <AuthContext.Provider value={{ registeredUser, setRegisteredUser }}>
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
