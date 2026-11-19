import { describe, expect, it, vi } from "vitest";
import { api, ApiError, fieldErrors, errorMessage } from "../api/client";
import { listing, response, pageResponse } from "./fixtures";
describe("API contracts", () => {
  it("sends bearer and JSON headers with protected writes, without seller fields", async () => {
    const fetcher = vi.fn().mockResolvedValue(response(listing));
    vi.stubGlobal("fetch", fetcher);
    await api.create(
      {
        title: "Ball",
        description: "Good ball",
        price: 24.99,
        categoryId: 1,
        condition: "GOOD",
      },
      "test-token",
    );
    const [url, options] = fetcher.mock.calls[0];
    expect(url).toBe("/api/listings");
    expect(options.method).toBe("POST");
    expect(options.headers.get("Authorization")).toBe("Bearer test-token");
    expect(options.headers.get("Content-Type")).toBe("application/json");
    expect(options.credentials).toBe("omit");
    expect(JSON.parse(options.body)).toEqual({
      title: "Ball",
      description: "Good ball",
      price: 24.99,
      categoryId: 1,
      condition: "GOOD",
    });
  });
  it("keeps public reads free of stale bearer tokens", async () => {
    const fetcher = vi.fn().mockResolvedValue(response(pageResponse([])));
    vi.stubGlobal("fetch", fetcher);
    await api.listings();
    expect(fetcher.mock.calls[0][1].headers.has("Authorization")).toBe(false);
  });
  it("handles empty 204 delete responses", async () => {
    const fetcher = vi.fn().mockResolvedValue(response(null, 204));
    vi.stubGlobal("fetch", fetcher);
    await expect(api.delete(4, "test-token")).resolves.toBeUndefined();
    expect(fetcher.mock.calls[0][1].method).toBe("DELETE");
  });
  it("preserves status and field validation messages from ProblemDetail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        response(
          {
            detail: "Request validation failed.",
            errors: [{ field: "price", message: "must be greater than 0" }],
          },
          400,
        ),
      ),
    );
    const error = await api
      .create(
        {
          title: "",
          description: "",
          price: 0,
          categoryId: 1,
          condition: "GOOD",
        },
        "test-token",
      )
      .catch((error) => error);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(fieldErrors(error)).toEqual({ price: "must be greater than 0" });
  });
  it("shows a useful error for network failures", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new TypeError("fetch failed")),
    );
    await expect(api.categories()).rejects.toThrow("backend is running");
  });
});

describe("403 error context", () => {
  it("does not describe a registration rejection as listing ownership", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(response({ detail: "Registration blocked." }, 403)),
    );
    const error = await api
      .register({
        fullName: "Sam",
        email: "sam@example.test",
        password: "test-password",
      })
      .catch((error) => error);
    expect(errorMessage(error)).toBe("Registration blocked.");
    expect(errorMessage(error)).not.toContain("listing");
  });
  it("explains ownership for listing mutations", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(response({ detail: "Forbidden" }, 403)),
    );
    const error = await api
      .status(4, "SOLD", "test-token")
      .catch((error) => error);
    expect(errorMessage(error)).toContain("Only its owner");
  });
});

it("serializes optional listing filters and consumes the page envelope", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      response({
        content: [listing],
        page: 2,
        size: 12,
        totalElements: 25,
        totalPages: 3,
        first: false,
        last: true,
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  const page = await api.listings({
    search: "Nike + kit",
    categoryId: "1",
    condition: "GOOD",
    minPrice: "0",
    maxPrice: "100",
    sort: "price_asc",
    page: "2",
    size: "12",
  });
  const url = new URL(fetcher.mock.calls[0][0], "http://localhost");
  expect(url.searchParams.get("search")).toBe("Nike + kit");
  expect(url.searchParams.get("minPrice")).toBe("0");
  expect(url.searchParams.get("categoryId")).toBe("1");
  expect(url.searchParams.get("condition")).toBe("GOOD");
  expect(url.searchParams.get("sort")).toBe("price_asc");
  expect(url.searchParams.get("page")).toBe("2");
  expect(page.content[0].id).toBe(listing.id);
  expect(page.totalElements).toBe(25);
});
