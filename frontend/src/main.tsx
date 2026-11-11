import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router";
import "./i18n";
import "./index.css";
import { router } from "./App";
import { ApiError } from "./api/client";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // Don't hammer the server on 4xx – only retry network/5xx (e.g. a free-tier server waking up).
      // A sleeping free-tier backend can take ~1 minute to wake, so be patient with 5xx/network errors.
      retry: (count, err) => count < 6 && !(err instanceof ApiError && err.status >= 400 && err.status < 500),
      retryDelay: (attempt) => Math.min(2000 * 2 ** attempt, 15_000),
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
