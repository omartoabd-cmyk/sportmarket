import type { ListingCondition } from "../types";
// Currency has not been specified by the API. Do not silently label it CAD or USD.
export const price = (amount: number) =>
  "$" +
  new Intl.NumberFormat("en", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
export const conditions: Record<ListingCondition, string> = {
  NEW: "New",
  LIKE_NEW: "Like new",
  GOOD: "Good",
  FAIR: "Fair",
  POOR: "Poor",
};
export const date = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
