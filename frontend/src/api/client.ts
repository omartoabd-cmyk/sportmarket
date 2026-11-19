import type {
  AuthResponse,
  Category,
  Listing,
  ListingInput,
  ListingImage,
  ListingFilters,
  ListingPage,
  ListingStatus,
  LoginInput,
  ProblemDetail,
  RegisterInput,
  User,
} from "../types";

const base = (import.meta.env.VITE_API_URL || "/api").replace(/\/$/, "");
export class ApiError extends Error {
  constructor(
    public status: number,
    public problem: ProblemDetail,
    public path?: string,
    public method?: string,
  ) {
    super(problem.detail || problem.title || `Request failed (${status}).`);
    this.name = "ApiError";
  }
}
interface Options extends Omit<RequestInit, "body"> {
  body?: unknown;
  token?: string;
}
export async function request<T>(
  path: string,
  { body, token, ...options }: Options = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  const multipart = body instanceof FormData;
  if (body !== undefined && !multipart)
    headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(`${base}${path}`, {
      ...options,
      headers,
      credentials: "omit",
      body:
        body === undefined
          ? undefined
          : multipart
            ? body
            : JSON.stringify(body),
    });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new Error(
      "We could not reach SportMarket. Check your connection and that the backend is running.",
    );
  }
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail;
    throw new ApiError(response.status, problem, path, options.method || "GET");
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export function imageUrl(imageId: number): string {
  return `${base}/listing-images/${imageId}/content`;
}
export class PartialImageUploadError extends Error {
  constructor(
    public listingId: number,
    public remainingFiles: File[],
    public savedImages: ListingImage[],
    message: string,
  ) {
    super(message);
    this.name = "PartialImageUploadError";
  }
}
export const api = {
  images: (id: number, signal?: AbortSignal) =>
    request<ListingImage[]>(`/listings/${id}/images`, { signal }),
  uploadImage: (id: number, file: File, token: string) => {
    const body = new FormData();
    body.append("file", file);
    return request<ListingImage>(`/listings/${id}/images`, {
      method: "POST",
      body,
      token,
    });
  },
  deleteImage: (id: number, imageId: number, token: string) =>
    request<void>(`/listings/${id}/images/${imageId}`, {
      method: "DELETE",
      token,
    }),
  listings: (filters: ListingFilters = {}, signal?: AbortSignal) => {
    const query = new URLSearchParams();
    const keys: (keyof ListingFilters)[] = [
      "search",
      "categoryId",
      "condition",
      "minPrice",
      "maxPrice",
      "sort",
      "page",
      "size",
    ];
    for (const key of keys) {
      const value = filters[key];
      if (value !== undefined && value !== "") query.set(key, value);
    }
    return request<ListingPage>(`/listings${query.size ? `?${query}` : ""}`, {
      signal,
    });
  },
  listing: (id: string | number, signal?: AbortSignal) =>
    request<Listing>(`/listings/${encodeURIComponent(id)}`, { signal }),
  categories: (signal?: AbortSignal) =>
    request<Category[]>("/categories", { signal }),
  register: (body: RegisterInput) =>
    request<User>("/auth/register", { method: "POST", body }),
  login: (body: LoginInput) =>
    request<AuthResponse>("/auth/login", { method: "POST", body }),
  me: (token: string) => request<User>("/users/me", { token }),
  myListings: (token: string, signal?: AbortSignal) =>
    request<Listing[]>("/users/me/listings", { token, signal }),
  create: (body: ListingInput, token: string) =>
    request<Listing>("/listings", { method: "POST", body, token }),
  update: (id: number, body: ListingInput, token: string) =>
    request<Listing>(`/listings/${id}`, { method: "PUT", body, token }),
  status: (id: number, status: ListingStatus, token: string) =>
    request<Listing>(`/listings/${id}/status`, {
      method: "PATCH",
      body: { status },
      token,
    }),
  delete: (id: number, token: string) =>
    request<void>(`/listings/${id}`, { method: "DELETE", token }),
};
export function errorMessage(error: unknown): string {
  if (
    error instanceof ApiError &&
    error.status === 403 &&
    /^\/listings\/[^/]+(?:\/status|\/images(?:\/[^/]+)?)?$/.test(
      error.path || "",
    ) &&
    ["POST", "PUT", "PATCH", "DELETE"].includes(error.method || "")
  )
    return "You do not have permission to change this listing. Only its owner can manage it.";
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(
    error.problem.errors?.map(({ field, message }) => [field, message]) || [],
  );
}
