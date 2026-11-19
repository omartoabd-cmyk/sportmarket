import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider, RequireAuth, useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import { response, user } from "./fixtures";
function Probe() {
  const { user, login, logout, authorized, notice } = useAuth();
  return (
    <>
      <p>{user?.fullName || "Anonymous"}</p>
      <p>{notice}</p>
      <button
        onClick={() =>
          login({ email: "alex@example.test", password: "test-password" })
        }
      >
        Sign in
      </button>
      <button onClick={logout}>Sign out</button>
      <button
        onClick={() => {
          void authorized((token) => api.myListings(token)).catch(() => {});
        }}
      >
        Protected call
      </button>
    </>
  );
}
function mount() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </MemoryRouter>,
  );
}
function stubLogin(expiresIn = 3600) {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      response({
        accessToken: "test-only-token",
        tokenType: "Bearer",
        expiresIn,
      }),
    )
    .mockResolvedValueOnce(response(user));
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
describe("in-memory authentication", () => {
  it("loads identity, attaches tokens to protected calls, and clears on logout without storage", async () => {
    const fetcher = stubLogin();
    fetcher.mockResolvedValue(response([]));
    const localWrite = vi.spyOn(Storage.prototype, "setItem");
    mount();
    const actions = userEvent.setup();
    await actions.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Alex Seller")).toBeInTheDocument();
    await actions.click(screen.getByRole("button", { name: "Protected call" }));
    expect(fetcher.mock.calls[2][1].headers.get("Authorization")).toBe(
      "Bearer test-only-token",
    );
    await actions.click(screen.getByRole("button", { name: "Sign out" }));
    expect(screen.getByText("Anonymous")).toBeInTheDocument();
    expect(localWrite).not.toHaveBeenCalled();
    await actions.click(screen.getByRole("button", { name: "Protected call" }));
    expect(fetcher).toHaveBeenCalledTimes(3);
  });
  it("does not restore a token after the auth provider is remounted", async () => {
    stubLogin();
    const first = mount();
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByText("Alex Seller");
    first.unmount();
    mount();
    expect(screen.getByText("Anonymous")).toBeInTheDocument();
  });
  it("clears the session when a protected API call returns 401", async () => {
    const fetcher = stubLogin();
    fetcher.mockResolvedValue(response({ detail: "Expired" }, 401));
    mount();
    const actions = userEvent.setup();
    await actions.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByText("Alex Seller");
    await actions.click(screen.getByRole("button", { name: "Protected call" }));
    expect(await screen.findByText("Anonymous")).toBeInTheDocument();
    expect(
      screen.getByText(/session has expired or is no longer valid/),
    ).toBeInTheDocument();
  });
  it("expires the session using expiresIn", async () => {
    vi.useFakeTimers();
    stubLogin(1);
    mount();
    await act(async () => {
      screen.getByRole("button", { name: "Sign in" }).click();
    });
    expect(screen.getByText("Alex Seller")).toBeInTheDocument();
    await act(async () => {
      vi.advanceTimersByTime(1001);
    });
    expect(screen.getByText("Anonymous")).toBeInTheDocument();
  });
  it("redirects anonymous users from protected routes to login", async () => {
    render(
      <MemoryRouter initialEntries={["/listings/new"]}>
        <AuthProvider>
          <Routes>
            <Route
              path="/listings/new"
              element={
                <RequireAuth>
                  <p>Private editor</p>
                </RequireAuth>
              }
            />
            <Route path="/login" element={<p>Login destination</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );
    await waitFor(() =>
      expect(screen.getByText("Login destination")).toBeInTheDocument(),
    );
    expect(screen.queryByText("Private editor")).not.toBeInTheDocument();
  });
});
