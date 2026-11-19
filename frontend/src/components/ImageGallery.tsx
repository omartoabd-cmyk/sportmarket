import { useState } from "react";
import type { ListingImage } from "../types";
import { imageUrl } from "../api/client";
import { ListingPhoto } from "./ListingPhoto";
export function ImageGallery({
  images,
  title,
}: {
  images: ListingImage[];
  title: string;
}) {
  const [selected, setSelected] = useState<number | null>(null);
  const active = images.find((image) => image.id === selected) || images[0];
  return (
    <div className="image-gallery">
      <ListingPhoto
        key={active?.id || "empty"}
        image={active}
        title={title}
        large
      />
      {images.length > 1 && (
        <div className="image-thumbnails" aria-label="Listing photos">
          {images.map((image, index) => (
            <button
              key={image.id}
              type="button"
              aria-label={`View photo ${index + 1}`}
              aria-pressed={image.id === active?.id}
              onClick={() => setSelected(image.id)}
            >
              <img
                src={imageUrl(image.id)}
                alt={`Photo ${index + 1} of ${title}`}
              />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
