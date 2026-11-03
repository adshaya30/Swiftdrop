import type { ReactNode } from 'react';
import './PublicLayout.css';

interface Props {
  children: ReactNode;
}

/**
 * Layout shell for unauthenticated pages (register, login).
 * Centres the card on a gradient background.
 */
export default function PublicLayout({ children }: Props) {
  return (
    <div className="pub-layout">
      <div className="pub-brand" aria-label="SwiftDrop logo">
        <span className="pub-brand-mark" aria-hidden="true">SD</span>
        <span className="pub-brand-name">SwiftDrop</span>
      </div>
      <main className="pub-main" id="main-content">
        {children}
      </main>
      <footer className="pub-footer">
        <p>&copy; {new Date().getFullYear()} SwiftDrop. All rights reserved.</p>
      </footer>
    </div>
  );
}
