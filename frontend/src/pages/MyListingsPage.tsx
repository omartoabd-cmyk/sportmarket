import { useCallback, useState } from "react";
import { Link } from "react-router";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useResource } from "../hooks/useResource";
import {
  ConfirmDialog,
  EmptyState,
  ErrorNotice,
  Loading,
  ResourceError,
} from "../components/Feedback";
import { ListingPhoto } from "../components/ListingPhoto";
import { Icon } from "../components/Icon";
import { conditions, date, price } from "../lib/format";
import type { Listing } from "../types";
export function MyListingsPage() {
  const { authorized } = useAuth();
  const resource = useResource(
    useCallback(
      (signal: AbortSignal) =>
        authorized((token) => api.myListings(token, signal)),
      [authorized],
    ),
  );
  const [filter, setFilter] = useState("ALL");
  const [target, setTarget] = useState<Listing | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState("");
  const listings = resource.data || [];
  const visible = listings.filter(
    (listing) => filter === "ALL" || listing.status === filter,
  );
  async function changeStatus(listing: Listing) {
    if (busy !== null) return;
    setBusy(listing.id);
    setError(null);
    setMessage("");
    try {
      const next = listing.status === "AVAILABLE" ? "SOLD" : "AVAILABLE";
      await authorized((token) => api.status(listing.id, next, token));
      setMessage(
        next === "SOLD"
          ? "Listing marked as sold."
          : "Listing is available again.",
      );
      resource.reload();
    } catch (error) {
      setError(error);
    } finally {
      setBusy(null);
    }
  }
  async function remove() {
    if (!target || busy !== null) return;
    setBusy(target.id);
    setError(null);
    setMessage("");
    try {
      await authorized((token) => api.delete(target.id, token));
      setMessage("Your listing has been deleted.");
      resource.reload();
    } catch (error) {
      setError(error);
    } finally {
      setBusy(null);
      setTarget(null);
    }
  }
  return (
    <div className="container page-section">
      <div className="dashboard-heading">
        <div>
          <p className="eyebrow text-green">YOUR GEAR, YOUR NEXT CHAPTER</p>
          <h1>My listings.</h1>
          <p className="muted">
            A home base for everything you are passing on.
          </p>
        </div>
        <Link className="btn btn-primary" to="/listings/new">
          <Icon name="plus" /> Create listing
        </Link>
      </div>
      {message && (
        <div role="status" className="notice notice-success">
          {message}
        </div>
      )}
      <ErrorNotice error={error} />
      {resource.loading ? (
        <Loading cards />
      ) : resource.error ? (
        <ResourceError error={resource.error} retry={resource.reload} />
      ) : (
        <>
          <div className="dashboard-tabs" aria-label="Filter your listings">
            {[
              ["ALL", "All listings", listings.length],
              [
                "AVAILABLE",
                "Available",
                listings.filter((item) => item.status === "AVAILABLE").length,
              ],
              [
                "SOLD",
                "Sold",
                listings.filter((item) => item.status === "SOLD").length,
              ],
            ].map(([value, label, count]) => (
              <button
                key={value}
                aria-pressed={filter === value}
                className={filter === value ? "active" : ""}
                onClick={() => setFilter(String(value))}
              >
                {label}
                <span>{count}</span>
              </button>
            ))}
          </div>
          {!visible.length ? (
            <EmptyState
              title={
                listings.length
                  ? "Nothing in this section yet"
                  : "Your gear has a future here"
              }
              action="Create a listing"
              to="/listings/new"
            >
              {listings.length
                ? "Your listings will appear here when their status matches."
                : "Start with the equipment you no longer use. Someone else might be looking for exactly that."}
            </EmptyState>
          ) : (
            <div className="dashboard-list">
              {visible.map((listing) => (
                <article className="dashboard-item" key={listing.id}>
                  <Link
                    className="dashboard-image"
                    to={`/listings/${listing.id}`}
                    aria-label={`View ${listing.title}`}
                  >
                    <ListingPhoto
                      key={listing.images?.[0]?.id || "empty"}
                      image={listing.images?.[0]}
                      title={listing.title}
                    />
                  </Link>
                  <div className="dashboard-item-copy">
                    <div className="flex items-center gap-3 flex-wrap">
                      <span className="eyebrow text-green">
                        {listing.category.name}
                      </span>
                      <span
                        className={`badge ${listing.status === "SOLD" ? "badge-sold" : ""}`}
                      >
                        {listing.status === "SOLD" ? "Sold" : "Available"}
                      </span>
                    </div>
                    <Link
                      to={`/listings/${listing.id}`}
                      className="dashboard-title"
                    >
                      {listing.title}
                    </Link>
                    <p className="muted text-sm">
                      {conditions[listing.condition]} condition{" "}
                      <span aria-hidden="true"> / </span> Listed{" "}
                      {date(listing.createdAt)}
                    </p>
                    <strong className="dashboard-price">
                      {price(listing.price)}
                    </strong>
                  </div>
                  <div className="dashboard-actions">
                    <Link
                      className="btn btn-secondary btn-small"
                      to={`/listings/${listing.id}/edit`}
                    >
                      <Icon name="edit" width="16" height="16" /> Edit
                    </Link>
                    <button
                      className="btn btn-secondary btn-small"
                      disabled={busy !== null}
                      onClick={() => changeStatus(listing)}
                    >
                      {busy === listing.id
                        ? "Updating..."
                        : listing.status === "SOLD"
                          ? "Make available"
                          : "Mark as sold"}
                    </button>
                    <button
                      className="delete-button"
                      aria-label={`Delete ${listing.title}`}
                      disabled={busy !== null}
                      onClick={() => setTarget(listing)}
                    >
                      <Icon name="trash" width="18" height="18" />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </>
      )}
      {target && (
        <ConfirmDialog
          title="Delete this listing?"
          pending={busy !== null}
          onCancel={() => setTarget(null)}
          onConfirm={remove}
        >
          <p>
            <strong>{target.title}</strong> will be permanently removed. This
            cannot be undone.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}
