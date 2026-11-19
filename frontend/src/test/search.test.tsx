import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { HomePage } from "../pages/HomePage";
import { listing, pageResponse, response } from "./fixtures";
function Location() {
  return <output data-testid="url">{useLocation().search}</output>;
}
function mount(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <HomePage />
        <Location />
      </AuthProvider>
    </MemoryRouter>,
  );
}
function backend() {
  const fetcher = vi.fn(async (url: string) => {
    const parsed = new URL(url, "http://localhost");
    if (parsed.pathname.endsWith("/categories"))
      return response([listing.category, { id: 2, name: "Tennis" }]);
    const page = Number(parsed.searchParams.get("page") || 0);
    const size = Number(parsed.searchParams.get("size") || 12);
    return response(
      pageResponse(
        [{ ...listing, id: page + 1, title: `Page ${page + 1} gear` }],
        page,
        size,
        30,
      ),
    );
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
function listingCalls(fetcher: ReturnType<typeof backend>) {
  return fetcher.mock.calls.filter(([url]) =>
    new URL(url, "http://localhost").pathname.endsWith("/listings"),
  );
}
describe("server-side marketplace search", () => {
  it("preserves combined filters across pagination, shows totals, and resets page on dropdown changes", async () => {
    const fetcher = backend();
    mount(
      "/?search=nike&categoryId=1&condition=GOOD&minPrice=10&maxPrice=100&sort=price_asc&page=1&size=12",
    );
    const actions = userEvent.setup();
    expect(await screen.findByText(/30 matching listings/)).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();
    expect(screen.getByLabelText("Search equipment")).toHaveValue("nike");
    await actions.click(screen.getByRole("button", { name: "Next" }));
    await screen.findByText("Page 3 of 3");
    let query = new URL(listingCalls(fetcher).at(-1)![0], "http://localhost")
      .searchParams;
    expect(query.get("page")).toBe("2");
    expect(query.get("search")).toBe("nike");
    expect(query.get("minPrice")).toBe("10");
    expect(query.get("maxPrice")).toBe("100");
    expect(query.get("condition")).toBe("GOOD");
    expect(query.get("sort")).toBe("price_asc");
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    await actions.selectOptions(screen.getByLabelText("Category"), "2");
    await screen.findByText("Page 1 of 3");
    query = new URL(listingCalls(fetcher).at(-1)![0], "http://localhost")
      .searchParams;
    expect(query.get("page")).toBe("0");
    expect(query.get("categoryId")).toBe("2");
    expect(
      fetcher.mock.calls.filter(([url]) => url.endsWith("/categories")),
    ).toHaveLength(1);
  });
  it("debounces keyword typing and sends only the final search with page zero", async () => {
    const fetcher = backend();
    mount("/?page=2");
    await screen.findByText("Page 3 of 3");
    const actions = userEvent.setup();
    const start = listingCalls(fetcher).length;
    await actions.type(screen.getByRole("searchbox"), "nike");
    expect(listingCalls(fetcher)).toHaveLength(start);
    await waitFor(() => expect(listingCalls(fetcher)).toHaveLength(start + 1));
    const query = new URL(listingCalls(fetcher).at(-1)![0], "http://localhost")
      .searchParams;
    expect(query.get("search")).toBe("nike");
    expect(query.get("page")).toBe("0");
  });
  it("validates and applies price ranges together, resets page, and clears filters", async () => {
    const fetcher = backend();
    mount("/?page=1");
    await screen.findByText("Page 2 of 3");
    const actions = userEvent.setup();
    await actions.type(screen.getByLabelText("Minimum price"), "100");
    await actions.type(screen.getByLabelText("Maximum price"), "20");
    await actions.click(screen.getByRole("button", { name: "Apply prices" }));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Minimum price must not exceed",
    );
    expect(listingCalls(fetcher)).toHaveLength(1);
    await actions.clear(screen.getByLabelText("Minimum price"));
    await actions.type(screen.getByLabelText("Minimum price"), "10");
    await actions.click(screen.getByRole("button", { name: "Apply prices" }));
    await screen.findByText("Page 1 of 3");
    const query = new URL(listingCalls(fetcher).at(-1)![0], "http://localhost")
      .searchParams;
    expect(query.get("minPrice")).toBe("10");
    expect(query.get("maxPrice")).toBe("20");
    expect(query.get("page")).toBe("0");
    await actions.click(screen.getByRole("button", { name: "Clear filters" }));
    await waitFor(() =>
      expect(screen.getByTestId("url")).toHaveTextContent(""),
    );
    expect(screen.getByLabelText("Minimum price")).toHaveValue(null);
  });
  it("ignores a stale response that resolves after a newer filter request", async () => {
    let resolveOld!: (response: Response) => void;
    const fetcher = vi.fn(async (url: string) => {
      if (url.endsWith("/categories")) return response([listing.category]);
      const query = new URL(url, "http://localhost").searchParams;
      if (!query.has("condition"))
        return new Promise<Response>((resolve) => {
          resolveOld = resolve;
        });
      return response(
        pageResponse([{ ...listing, title: "New filtered gear" }]),
      );
    });
    vi.stubGlobal("fetch", fetcher);
    mount();
    const actions = userEvent.setup();
    await actions.selectOptions(screen.getByLabelText("Condition"), "GOOD");
    expect(await screen.findByText("New filtered gear")).toBeInTheDocument();
    await act(async () => {
      resolveOld(response(pageResponse([{ ...listing, title: "Stale gear" }])));
    });
    expect(screen.queryByText("Stale gear")).not.toBeInTheDocument();
    expect(screen.getByText("New filtered gear")).toBeInTheDocument();
  });
  it("shows server filter errors and recovers from a retry", async () => {
    let failed = false;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url.endsWith("/categories")) return response([listing.category]);
        if (!failed) {
          failed = true;
          return response(
            { detail: "The selected category does not exist." },
            400,
          );
        }
        return response(pageResponse([]));
      }),
    );
    mount("/?categoryId=999");
    expect(
      await screen.findByText("The selected category does not exist."),
    ).toBeInTheDocument();
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Try again" }));
    expect(
      await screen.findByText("No gear found this time"),
    ).toBeInTheDocument();
  });
});
