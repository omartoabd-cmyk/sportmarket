import { useEffect, useState, type ChangeEvent } from "react";
import { api, imageUrl } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorNotice } from "./Feedback";
import type { ListingImage } from "../types";
import { Icon } from "./Icon";
export function ImageUploadControl({
  listingId,
  images,
  files,
  setImages,
  setFiles,
  onBusy,
}: {
  listingId?: number;
  images: ListingImage[];
  files: File[];
  setImages: (images: ListingImage[]) => void;
  setFiles: (files: File[]) => void;
  onBusy: (busy: boolean) => void;
}) {
  const { authorized } = useAuth();
  const [previews, setPreviews] = useState<{ file: File; url: string }[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [deleting, setDeleting] = useState<number | null>(null);
  useEffect(() => {
    const next = files.map((file) => ({
      file,
      url: URL.createObjectURL(file),
    }));
    setPreviews(next);
    return () => next.forEach((item) => URL.revokeObjectURL(item.url));
  }, [files]);
  function choose(event: ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.target.files || []);
    event.target.value = "";
    setError(null);
    if (images.length + files.length + selected.length > 5) {
      setError(new Error("A listing can have at most 5 photos."));
      return;
    }
    if (
      selected.some(
        (file) =>
          !["image/jpeg", "image/png", "image/webp"].includes(file.type),
      )
    ) {
      setError(new Error("Choose JPEG, PNG, or WebP photos."));
      return;
    }
    if (
      selected.some((file) => file.size === 0 || file.size > 5 * 1024 * 1024)
    ) {
      setError(
        new Error("Each photo must contain data and be no larger than 5 MB."),
      );
      return;
    }
    setFiles([...files, ...selected]);
  }
  async function remove(image: ListingImage) {
    if (!listingId || deleting !== null) return;
    setError(null);
    setDeleting(image.id);
    onBusy(true);
    try {
      await authorized((token) => api.deleteImage(listingId, image.id, token));
      setImages(images.filter((item) => item.id !== image.id));
    } catch (error) {
      setError(error);
    } finally {
      setDeleting(null);
      onBusy(false);
    }
  }
  return (
    <section className="image-upload-control" aria-labelledby="photos-label">
      <div className="photo-heading">
        <h2 id="photos-label">Photos</h2>
        <span className="muted">{images.length + files.length} / 5</span>
      </div>
      <p className="field-hint" id="photos-help">
        JPEG, PNG, or static WebP. Up to 5 MB each. Photos upload after listing
        details are saved.
      </p>
      <ErrorNotice error={error} />
      <div className="photo-previews">
        {images.map((image, index) => (
          <div className="photo-preview" key={image.id}>
            <img
              src={imageUrl(image.id)}
              alt={`Saved listing photo ${index + 1}`}
            />
            <button
              className="photo-remove"
              type="button"
              disabled={deleting !== null}
              aria-label={`Delete saved photo ${index + 1}`}
              onClick={() => remove(image)}
            >
              <Icon name="trash" width="16" height="16" />
            </button>
          </div>
        ))}
        {previews.map((preview, index) => (
          <div className="photo-preview" key={preview.url}>
            <img
              src={preview.url}
              alt={`Selected photo: ${preview.file.name}`}
            />
            <span>Ready to upload</span>
            <button
              type="button"
              className="photo-remove"
              aria-label={`Remove selected photo ${index + 1}`}
              onClick={() =>
                setFiles(files.filter((_, fileIndex) => fileIndex !== index))
              }
            >
              <Icon name="close" width="16" height="16" />
            </button>
          </div>
        ))}
      </div>
      <label className="photo-picker">
        <Icon name="plus" /> Add photos
        <input
          aria-label="Choose listing photos"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          disabled={images.length + files.length >= 5 || deleting !== null}
          onChange={choose}
          aria-describedby="photos-help"
        />
      </label>
      <p className="field-hint">
        Deleting an existing photo takes effect immediately. Canceling the form
        does not restore it.
      </p>
    </section>
  );
}
