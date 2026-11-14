import { useEffect, useRef, useState } from 'react';

/** FR-UI-007: bounded event-projection delay. A refresh does not extend the
 * 30-second window; route changes/unmount cancel timers and obsolete results.
 */
export function useParcelTimeline(id, loader, revision) {
  const [result, setResult] = useState({});
  const window = useRef(null);
  useEffect(() => {
    if (!loader) return undefined;
    let current = true;
    let timer;
    if (window.current?.id !== id) window.current = { id, started: Date.now() };
    const started = window.current.started;
    const load = async () => {
      try {
        const data = await loader(id);
        if (current) setResult({ id, revision, status: 'success', data });
      } catch (error) {
        if (!current) return;
        const elapsed = Date.now() - started;
        if (error.status === 404 && elapsed < 30000) {
          setResult({ id, revision, status: 'updating' });
          timer = setTimeout(load, Math.min(2000, 30000 - elapsed));
        } else setResult({ id, revision, status: 'error', error });
      }
    };
    void load();
    return () => { current = false; clearTimeout(timer); };
  }, [id, loader, revision]);
  if (!loader) return { status: 'unavailable' };
  return result.id === id && result.revision === revision ? result : { status: 'loading' };
}
