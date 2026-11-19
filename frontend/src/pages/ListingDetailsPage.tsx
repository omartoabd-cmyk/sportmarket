import { useCallback } from "react";
import { Link, useParams } from "react-router";
import { api, ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useResource } from "../hooks/useResource";
import { EmptyState, Loading, ResourceError } from "../components/Feedback";
import { ImageGallery } from "../components/ImageGallery";
import { Icon } from "../components/Icon";
import { conditions, date, price } from "../lib/format";
export function ListingDetailsPage() {
  const { id = "" } = useParams();
  const { user } = useAuth();
  const resource = useResource(
    useCallback((signal) => api.listing(id, signal), [id]),
  );
  const listing = resource.data;
  return (
    <div className="container page-section">
      <Link className="breadcrumb" to="/">
        Explore gear
      </Link>
      <span className="breadcrumb-separator">/</span>
      <span className="muted">Listing details</span>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        resource.error instanceof ApiError && resource.error.status === 404 ? (
          <EmptyState
            title="This listing is no longer here"
            action="Explore gear"
            to="/"
          >
            It may have been removed by its owner. There is more gear to
            discover.
          </EmptyState>
        ) : (
          <ResourceError error={resource.error} retry={resource.reload} />
        )
      ) : (
        listing && (
          <div className="details-grid">
            <div className="detail-media">
              <ImageGallery
                images={listing.images || []}
                title={listing.title}
              />
              <span
                className={`status-chip ${listing.status === "SOLD" ? "sold" : ""}`}
              >
                {listing.status === "SOLD" ? "Sold" : "Available"}
              </span>
              <p>
                {listing.images?.length
                  ? `${listing.images.length} listing photos`
                  : "No photos yet"}
              </p>
            </div>
            <div className="detail-copy">
              <p className="eyebrow text-green">{listing.category.name}</p>
              <h1>{listing.title}</h1>
              <p className="detail-price">{price(listing.price)}</p>
              <div className="detail-tags">
                <span>{conditions[listing.condition]} condition</span>
                <span>{listing.status === "SOLD" ? "Sold" : "Available"}</span>
              </div>
              <div className="detail-description">
                <h2>About this gear</h2>
                <p>{listing.description}</p>
              </div>
              <div className="seller-panel">
                <span className="avatar">
                  {listing.seller.fullName.charAt(0).toUpperCase()}
                </span>
                <div>
                  <span className="muted text-sm">Listed by</span>
                  <strong>{listing.seller.fullName}</strong>
                </div>
                <Icon name="user" />
              </div>
              <p className="listing-date">Listed {date(listing.createdAt)}</p>
              {user?.id === listing.seller.id && (
                <div className="flex gap-3 flex-wrap">
                  <Link
                    className="btn btn-primary"
                    to={`/listings/${listing.id}/edit`}
                  >
                    <Icon name="edit" /> Edit listing
                  </Link>
                  <Link className="btn btn-secondary" to="/my-listings">
                    Manage my listings
                  </Link>
                </div>
              )}
              <p className="detail-footnote">
                <Icon name="shield" /> Only the seller can change this listing.
              </p>
            </div>
          </div>
        )
      )}
    </div>
  );
}
