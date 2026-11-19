import type { Listing, User } from "../types";
export const user: User = {
  id: 1,
  fullName: "Alex Seller",
  email: "alex@example.test",
};
export const listing: Listing = {
  id: 4,
  images: [],
  title: "Soccer ball",
  description: "A lightly used size 5 ball.",
  price: 24.99,
  condition: "GOOD",
  status: "AVAILABLE",
  category: { id: 1, name: "Soccer" },
  seller: { id: 1, fullName: "Alex Seller" },
  createdAt: "2026-11-18T12:00:00Z",
  updatedAt: "2026-11-18T12:00:00Z",
};
export const response = (body: unknown, status = 200) =>
  new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

export const pageResponse = (
  content: Listing[],
  page = 0,
  size = 12,
  totalElements = content.length,
) => ({
  content,
  page,
  size,
  totalElements,
  totalPages: Math.ceil(totalElements / size),
  first: page === 0,
  last: (page + 1) * size >= totalElements,
});
