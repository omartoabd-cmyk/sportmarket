import { ListingPhoto } from "./ListingPhoto";
export { GearPlaceholder } from "./GearPlaceholder";
import { Link } from "react-router";
import type { Listing } from "../types";
import { conditions, price } from "../lib/format";
export function ListingCard({ listing }: { listing: Listing }) {
  return (
    <article className="listing-card">
      <Link to={`/listings/${listing.id}`} className="card-link">
        <div className="card-media">
          <ListingPhoto
            key={listing.images?.[0]?.id || "empty"}
            image={listing.images?.[0]}
            title={listing.title}
          />
          <span
            className={`status-chip ${listing.status === "SOLD" ? "sold" : ""}`}
          >
            {listing.status === "SOLD" ? "Sold" : conditions[listing.condition]}
          </span>
        </div>
        <div className="card-body">
          <p className="eyebrow">{listing.category.name}</p>
          <div className="card-title-row">
            <h2>{listing.title}</h2>
            <span className="card-price">{price(listing.price)}</span>
          </div>
          <p className="card-seller">Listed by {listing.seller.fullName}</p>
        </div>
      </Link>
    </article>
  );
}
