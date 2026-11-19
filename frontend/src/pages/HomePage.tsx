import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import type { ListingFilters } from "../types";
import { conditions } from "../lib/format";
import { api, ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useResource } from "../hooks/useResource";
import { EmptyState, Loading, ResourceError } from "../components/Feedback";
import { ListingCard } from "../components/ListingCard";
import { Icon } from "../components/Icon";
export function HomePage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const committedSearch = params.get("search") || "";
  const [query, setQuery] = useState(committedSearch);
  const [minDraft, setMinDraft] = useState(params.get("minPrice") || "");
  const [maxDraft, setMaxDraft] = useState(params.get("maxPrice") || "");
  const [priceError, setPriceError] = useState("");
  const categoryResource = useResource(
    useCallback((signal) => api.categories(signal), []),
  );
  const categories = categoryResource.data || [];
  const queryKey = params.toString();
  const catalogue = useResource(
    useCallback(
      (signal: AbortSignal) => {
        const selected = Object.fromEntries(
          new URLSearchParams(queryKey),
        ) as ListingFilters;
        return api.listings(
          {
            ...selected,
            sort: selected.sort || "newest",
            page: selected.page || "0",
            size: selected.size || "12",
          },
          signal,
        );
      },
      [queryKey],
    ),
  );
  const updateFilter = useCallback(
    (key: string, value: string, replace = false) => {
      setParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          if (value) next.set(key, value);
          else next.delete(key);
          next.set("page", "0");
          return next;
        },
        { replace },
      );
    },
    [setParams],
  );
  useEffect(() => {
    setQuery(committedSearch);
  }, [committedSearch]);
  useEffect(() => {
    if (query === committedSearch) return;
    const timer = window.setTimeout(
      () => updateFilter("search", query, true),
      350,
    );
    return () => window.clearTimeout(timer);
  }, [query, committedSearch, updateFilter]);
  const minValue = params.get("minPrice") || "";
  const maxValue = params.get("maxPrice") || "";
  useEffect(() => {
    setMinDraft(minValue);
    setMaxDraft(maxValue);
    setPriceError("");
  }, [minValue, maxValue]);
  function applyPrices(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (
      (minDraft &&
        (!Number.isFinite(Number(minDraft)) || Number(minDraft) < 0)) ||
      (maxDraft && (!Number.isFinite(Number(maxDraft)) || Number(maxDraft) < 0))
    ) {
      setPriceError("Prices must be nonnegative numbers.");
      return;
    }
    if (minDraft && maxDraft && Number(minDraft) > Number(maxDraft)) {
      setPriceError("Minimum price must not exceed maximum price.");
      return;
    }
    setPriceError("");
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      for (const [key, value] of [
        ["minPrice", minDraft],
        ["maxPrice", maxDraft],
      ]) {
        if (value !== "") next.set(key, value);
        else next.delete(key);
      }
      next.set("page", "0");
      return next;
    });
  }
  function clearFilters() {
    setQuery("");
    setMinDraft("");
    setMaxDraft("");
    setPriceError("");
    setParams({});
  }
  function goToPage(page: number) {
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("page", String(page));
      return next;
    });
  }
  const results = catalogue.data;
  const hasFilters = [
    "search",
    "categoryId",
    "condition",
    "minPrice",
    "maxPrice",
  ].some((key) => !!params.get(key));
  return (
    <>
      <section className="hero">
        <div className="container hero-inner">
          <div className="hero-copy">
            <p className="eyebrow hero-eyebrow">
              <span className="tiny-line" /> THE COMMUNITY GEAR EXCHANGE
            </p>
            <h1>
              Good gear.
              <br />
              <span>Next chapter.</span>
            </h1>
            <p className="hero-description">
              Your next great find. Someone else's next adventure.
              <br className="desktop-break" /> Buy and sell sports equipment
              with more game left to give.
            </p>
            <div className="hero-actions">
              <a className="btn btn-lime" href="#explore">
                Find your gear
              </a>
              <Link
                className="hero-sell"
                to={user ? "/listings/new" : "/register"}
              >
                Give your gear a second life
              </Link>
            </div>
            <div className="hero-note">
              <Icon name="check" width="16" height="16" /> Pre-loved equipment.
              Fresh possibilities.
            </div>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="court">
              <div className="court-line court-center" />
              <div className="court-circle" />
              <div className="court-box left" />
              <div className="court-box right" />
            </div>
            <span className="art-label">BUILT FOR YOUR NEXT MOVE</span>
            <span className="court-ball">
              <svg viewBox="0 0 120 120" fill="none">
                <circle
                  cx="60"
                  cy="60"
                  r="49"
                  fill="#c6f16a"
                  stroke="#1d4c3b"
                  strokeWidth="2"
                />
                <path
                  d="M11 60h98M60 11v98M25 25c47 12 58 24 70 70M95 25C48 37 37 48 25 95"
                  stroke="#1d4c3b"
                  strokeWidth="2"
                />
              </svg>
            </span>
            <div className="art-card">
              <span className="art-card-icon">
                <Icon name="bag" width="24" height="24" />
              </span>
              <div>
                <strong>Great gear goes around.</strong>
                <span>Make room for your next season.</span>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section className="container catalogue-section" id="explore">
        <div className="section-heading">
          <div>
            <p className="eyebrow text-green">READY FOR ANOTHER ROUND</p>
            <h2>Find your next favourite.</h2>
          </div>
          <span className="section-caption">
            Equipment for every kind of athlete.
          </span>
        </div>
        <div className="browse-controls">
          <label className="search-box">
            <Icon name="search" />
            <input
              aria-label="Search equipment"
              type="search"
              placeholder="What are you playing next?"
              maxLength={100}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          <label className="sort-control">
            <span>Sort by</span>
            <select
              aria-label="Sort listings"
              value={params.get("sort") || "newest"}
              onChange={(event) => updateFilter("sort", event.target.value)}
            >
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="price_asc">Price: low to high</option>
              <option value="price_desc">Price: high to low</option>
            </select>
            <Icon name="down" width="16" height="16" />
          </label>
        </div>
        <div className="marketplace-filters">
          <div className="filter-field">
            <label htmlFor="filter-category">Category</label>
            <div className="select-wrap">
              <select
                id="filter-category"
                value={params.get("categoryId") || ""}
                onChange={(event) =>
                  updateFilter("categoryId", event.target.value)
                }
                disabled={categoryResource.loading}
              >
                <option value="">All sports</option>
                {categories.map((item) => (
                  <option value={item.id} key={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
              <Icon name="down" />
            </div>
          </div>
          <div className="filter-field">
            <label htmlFor="filter-condition">Condition</label>
            <div className="select-wrap">
              <select
                id="filter-condition"
                value={params.get("condition") || ""}
                onChange={(event) =>
                  updateFilter("condition", event.target.value)
                }
              >
                <option value="">Any condition</option>
                {Object.entries(conditions).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
              <Icon name="down" />
            </div>
          </div>
          <form
            className="price-filters"
            onSubmit={applyPrices}
            aria-label="Price filters"
          >
            <div className="filter-field">
              <label htmlFor="filter-min">Minimum price</label>
              <input
                id="filter-min"
                type="number"
                min="0"
                step="any"
                inputMode="decimal"
                placeholder="No minimum"
                value={minDraft}
                onChange={(event) => setMinDraft(event.target.value)}
                aria-describedby={priceError ? "price-filter-error" : undefined}
              />
            </div>
            <div className="filter-field">
              <label htmlFor="filter-max">Maximum price</label>
              <input
                id="filter-max"
                type="number"
                min="0"
                step="any"
                inputMode="decimal"
                placeholder="No maximum"
                value={maxDraft}
                onChange={(event) => setMaxDraft(event.target.value)}
                aria-describedby={priceError ? "price-filter-error" : undefined}
              />
            </div>
            <button className="btn btn-secondary" type="submit">
              Apply prices
            </button>
            {priceError && (
              <p id="price-filter-error" role="alert" className="field-error">
                {priceError}
              </p>
            )}
          </form>
        </div>
        {!!categoryResource.error && (
          <div className="notice notice-error" role="alert">
            Categories could not be loaded.{" "}
            <button className="text-link" onClick={categoryResource.reload}>
              Retry categories
            </button>
          </div>
        )}
        <div className="filter-summary">
          <span className="muted">
            {query !== committedSearch
              ? "Updating search..."
              : "Search titles and combine filters to find your next piece of gear."}
          </span>
          {hasFilters && (
            <button className="text-link" onClick={clearFilters}>
              Clear filters
            </button>
          )}
        </div>
        {catalogue.loading ? (
          <Loading cards />
        ) : catalogue.error ? (
          <>
            <ResourceError error={catalogue.error} retry={catalogue.reload} />
            {catalogue.error instanceof ApiError &&
              !!catalogue.error.problem.errors?.length && (
                <ul className="notice notice-error" role="alert">
                  {catalogue.error.problem.errors.map((item, index) => (
                    <li key={index}>
                      {item.field}: {item.message}
                    </li>
                  ))}
                </ul>
              )}
          </>
        ) : (
          results && (
            <>
              <div className="results-heading" role="status">
                <span>
                  {results.totalElements} matching{" "}
                  {results.totalElements === 1 ? "listing" : "listings"}
                  {results.content.length > 0 &&
                    ` / Showing ${results.page * results.size + 1} to ${results.page * results.size + results.content.length}`}
                </span>
                <span className="muted">A little used. A lot to give.</span>
              </div>
              {results.content.length ? (
                <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                  {results.content.map((item) => (
                    <ListingCard key={item.id} listing={item} />
                  ))}
                </div>
              ) : results.totalElements > 0 ? (
                <EmptyState title="No listings on this page">
                  Results may have changed.{" "}
                  <button className="text-link" onClick={() => goToPage(0)}>
                    Back to the first page
                  </button>
                </EmptyState>
              ) : hasFilters ? (
                <EmptyState title="No gear found this time">
                  Try another sport, condition, price range, or search term.
                  <button
                    className="text-link block mx-auto mt-3"
                    onClick={clearFilters}
                  >
                    Clear all filters
                  </button>
                </EmptyState>
              ) : (
                <EmptyState
                  title="The next season starts with you"
                  action="Create the first listing"
                  to={user ? "/listings/new" : "/register"}
                >
                  No equipment is available just yet. Give your unused gear a
                  second life.
                </EmptyState>
              )}
              <div className="pagination-bar">
                <label className="page-size">
                  Per page
                  <select
                    aria-label="Results per page"
                    value={params.get("size") || "12"}
                    onChange={(event) =>
                      updateFilter("size", event.target.value)
                    }
                  >
                    {[12, 24, 50].map((size) => (
                      <option value={size} key={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                </label>
                <nav aria-label="Listing pages" className="pagination-controls">
                  <button
                    className="btn btn-secondary"
                    disabled={results.first}
                    onClick={() => goToPage(results.page - 1)}
                  >
                    Previous
                  </button>
                  <span aria-live="polite">
                    {results.totalPages
                      ? results.page >= results.totalPages
                        ? "Outside available pages"
                        : `Page ${results.page + 1} of ${results.totalPages}`
                      : "No pages"}
                  </span>
                  <button
                    className="btn btn-secondary"
                    disabled={results.last}
                    onClick={() => goToPage(results.page + 1)}
                  >
                    Next
                  </button>
                </nav>
              </div>
            </>
          )
        )}
      </section>
      <section className="container">
        <div className="sell-banner">
          <div>
            <p className="eyebrow">CLEAR YOUR SPACE. KEEP THE GAME GOING.</p>
            <h2>
              Not playing with it?
              <br />
              Someone else could be.
            </h2>
          </div>
          <Link
            className="btn btn-primary"
            to={user ? "/listings/new" : "/register"}
          >
            <Icon name="plus" /> List your gear
          </Link>
        </div>
      </section>
    </>
  );
}
