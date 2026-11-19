import { useState } from "react";
import type { ListingImage } from "../types";
import { imageUrl } from "../api/client";
import { GearPlaceholder } from "./GearPlaceholder";

export function ListingPhoto({
  image,
  title,
  large = false,
}: {
  image?: ListingImage;
  title: string;
  large?: boolean;
}) {
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);
  if (!image || failed) return <GearPlaceholder large={large} />;
  return (
    <div
      className={`photo-frame ${large ? "photo-large" : ""}`}
      aria-busy={!loaded}
    >
      {!loaded && (
        <span
          className="photo-loading"
          role="status"
          aria-label="Loading photo"
        />
      )}
      <img
        className="listing-photo"
        src={imageUrl(image.id)}
        alt={title}
        loading={large ? "eager" : "lazy"}
        onLoad={() => setLoaded(true)}
        onError={() => setFailed(true)}
      />
    </div>
  );
}
