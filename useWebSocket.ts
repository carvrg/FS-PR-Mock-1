import { useState, useEffect } from "react";

/**
 * Interview Problem #5 — React / TypeScript (Custom Hook)
 * Difficulty: Hard | Issues to find: 3
 *
 * A custom hook that wraps a WebSocket connection for real-time data streaming.
 * It is used in a dashboard where tabs mount and unmount dynamically.
 * The hook compiles without errors and data displays correctly on first load.
 * Find 3 things that are wrong or should be refactored.
 */
function useWebSocket<T>(url: string) {
  const [data, setData] = useState<T | null>(null);

  useEffect(() => {
    const ws = new WebSocket(url);

    ws.onmessage = (event) => {
      setData(JSON.parse(event.data) as T);
    };

    ws.onerror = (err) => console.error("WS error", err);
  }, [url]);

  const send = (message: T) => {
    ws.send(JSON.stringify(message));
  };

  return { data, send };
}

export default useWebSocket;
