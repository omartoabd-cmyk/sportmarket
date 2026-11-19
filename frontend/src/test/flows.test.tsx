import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { AuthProvider } from "../auth/AuthContext";
import { listing, response, user, pageResponse } from "./fixtures";
function mount(path = "/") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}
function mockBackend() {
  let all = [listing];
  const fetcher = vi.fn(async (url: string, options: RequestInit = {}) => {
    const method = options.method || "GET";
    if (url.endsWith("/auth/login"))
      return response({
        accessToken: "test-token",
        tokenType: "Bearer",
        expiresIn: 3600,
      });
    if (url.endsWith("/users/me")) return response(user);
    if (url.endsWith("/categories")) return response([listing.category]);
    if (url.endsWith("/users/me/listings")) return response(all);
    if (url.endsWith("/listings/4/status")) {
      all = all.map((item) => ({ ...item, status: "SOLD" }));
      return response(all[0]);
    }
    if (url.endsWith("/listings/4") && method === "DELETE") {
      all = [];
      return response(null, 204);
    }
    if (url.endsWith("/listings/4") && method === "PUT") {
      all = [{ ...listing, ...JSON.parse(String(options.body)) }];
      return response(all[0]);
    }
    if (url.endsWith("/listings/4")) return response(all[0]);
    if (url.endsWith("/listings") && method === "POST") {
      const input = JSON.parse(String(options.body));
      all = [{ ...listing, ...input }];
      return response(all[0], 201);
    }
    if (new URL(url, "http://localhost").pathname === "/api/listings") {
      const search =
        new URL(url, "http://localhost").searchParams.get("search") || "";
      return response(
        pageResponse(
          all.filter((item) =>
            item.title.toLowerCase().includes(search.toLowerCase()),
          ),
        ),
      );
    }
    return response({ detail: "Not found" }, 404);
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
async function signIn() {
  const actions = userEvent.setup();
  await actions.type(screen.getByLabelText("Email address"), user.email);
  await actions.type(screen.getByLabelText("Password"), "test-password");
  await actions.click(screen.getByRole("button", { name: "Login" }));
  return actions;
}
describe("marketplace workflows", () => {
  it("loads real-shaped listings, searches, and opens details", async () => {
    mockBackend();
    mount();
    expect(
      await screen.findByRole("heading", { name: "Soccer ball" }),
    ).toBeInTheDocument();
    const actions = userEvent.setup();
    await actions.type(screen.getByRole("searchbox"), "tennis");
    expect(
      await screen.findByText("No gear found this time"),
    ).toBeInTheDocument();
    await actions.clear(screen.getByRole("searchbox"));
    await actions.click(
      await screen.findByRole("link", { name: /Soccer ball/ }),
    );
    expect(
      await screen.findByRole("heading", { name: "About this gear" }),
    ).toBeInTheDocument();
    expect(screen.getByText(listing.description)).toBeInTheDocument();
  });
  it("displays a helpful empty state", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        response(url.endsWith("/categories") ? [] : pageResponse([])),
      ),
    );
    mount();
    expect(
      await screen.findByText("The next season starts with you"),
    ).toBeInTheDocument();
  });
  it("shows duplicate registration errors and then redirects successful registration to login", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        response({ detail: "An account with this email already exists." }, 409),
      )
      .mockResolvedValueOnce(response(user, 201));
    vi.stubGlobal("fetch", fetcher);
    mount("/register");
    const actions = userEvent.setup();
    await actions.type(screen.getByLabelText("Full name"), user.fullName);
    await actions.type(screen.getByLabelText("Email address"), user.email);
    await actions.type(screen.getByLabelText("Password"), "test-password");
    await actions.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "already exists",
    );
    await actions.click(screen.getByRole("button", { name: "Create account" }));
    expect(
      await screen.findByRole("heading", { name: "Welcome back." }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Your account is ready",
    );
  });
  it("creates through a protected form and opens the resulting listing", async () => {
    const fetcher = mockBackend();
    mount("/listings/new");
    const actions = await signIn();
    await screen.findByRole("form", { name: "Create listing" });
    await actions.type(screen.getByLabelText("Listing title"), "Tennis racket");
    await actions.selectOptions(screen.getByLabelText("Sport / category"), "1");
    await actions.selectOptions(screen.getByLabelText("Condition"), "GOOD");
    await actions.type(
      screen.getByLabelText("Description"),
      "Lightly used racket",
    );
    await actions.type(screen.getByLabelText("Price"), "49.99");
    await actions.click(
      screen.getByRole("button", { name: "Publish listing" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Tennis racket" }),
    ).toBeInTheDocument();
    const call = fetcher.mock.calls.find(
      ([url, options]) =>
        url.endsWith("/listings") && options?.method === "POST",
    );
    expect(call).toBeTruthy();
    expect(JSON.parse(String(call![1]?.body))).not.toHaveProperty("sellerId");
  });
  it("prepopulates editing and shows backend 403 errors", async () => {
    const fetcher = mockBackend();
    mount("/listings/4/edit");
    const actions = await signIn();
    expect(await screen.findByDisplayValue("Soccer ball")).toBeInTheDocument();
    expect(screen.getByLabelText("Price")).toHaveValue(24.99);
    fetcher.mockImplementationOnce(async () =>
      response({ detail: "Forbidden" }, 403),
    );
    await actions.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Only its owner",
    );
  });
  it("changes status and requires confirmation before deletion", async () => {
    const fetcher = mockBackend();
    mount("/my-listings");
    const actions = await signIn();
    await screen.findByText("Soccer ball");
    await actions.click(screen.getByRole("button", { name: "Mark as sold" }));
    expect(
      await screen.findByRole("button", { name: "Make available" }),
    ).toBeInTheDocument();
    await actions.click(
      screen.getByRole("button", { name: "Delete Soccer ball" }),
    );
    const dialog = screen.getByRole("dialog");
    expect(
      fetcher.mock.calls.some(([, options]) => options?.method === "DELETE"),
    ).toBe(false);
    await actions.click(
      within(dialog).getByRole("button", { name: "Keep listing" }),
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await actions.click(
      screen.getByRole("button", { name: "Delete Soccer ball" }),
    );
    await actions.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Delete listing",
      }),
    );
    await waitFor(() =>
      expect(screen.queryByText("Soccer ball")).not.toBeInTheDocument(),
    );
    expect(
      screen.getByText("Your listing has been deleted."),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.some(([, options]) => options?.method === "DELETE"),
    ).toBe(true);
  });
});
