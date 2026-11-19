export type ListingCondition = "NEW" | "LIKE_NEW" | "GOOD" | "FAIR" | "POOR";
export type ListingStatus = "AVAILABLE" | "SOLD";
export interface Category {
  id: number;
  name: string;
}
export interface User {
  id: number;
  fullName: string;
  email: string;
}
export interface ListingImage {
  id: number;
  listingId: number;
  contentType: string;
  displayOrder: number;
  createdAt: string;
  url: string;
}
export interface Listing {
  id: number;
  title: string;
  description: string;
  price: number;
  condition: ListingCondition;
  status: ListingStatus;
  category: Category;
  seller: { id: number; fullName: string };
  createdAt: string;
  updatedAt: string;
  images: ListingImage[];
}
export interface ListingInput {
  title: string;
  description: string;
  price: number;
  categoryId: number;
  condition: ListingCondition;
}
export interface RegisterInput {
  fullName: string;
  email: string;
  password: string;
}
export interface LoginInput {
  email: string;
  password: string;
}
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: { field: string; message: string }[];
}

export type ListingSort = "newest" | "oldest" | "price_asc" | "price_desc";
// URL query inputs stay strings so invalid bookmarked values can receive a server 400.
export interface ListingFilters {
  search?: string;
  categoryId?: string;
  condition?: string;
  minPrice?: string;
  maxPrice?: string;
  sort?: string;
  page?: string;
  size?: string;
}
export interface ListingPage {
  content: Listing[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
