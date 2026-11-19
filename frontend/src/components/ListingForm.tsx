import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { PartialImageUploadError, fieldErrors } from "../api/client";
import type {
  Category,
  Listing,
  ListingCondition,
  ListingInput,
} from "../types";
import { conditions } from "../lib/format";
import { ErrorNotice, Field } from "./Feedback";
import { Icon } from "./Icon";
import { ImageUploadControl } from "./ImageUploadControl";
export function ListingForm({
  initial,
  categories,
  onSave,
}: {
  initial?: Listing;
  categories: Category[];
  onSave: (input: ListingInput, files: File[]) => Promise<void>;
}) {
  const [title, setTitle] = useState(initial?.title || "");
  const [description, setDescription] = useState(initial?.description || "");
  const [price, setPrice] = useState(initial ? String(initial.price) : "");
  const [categoryId, setCategoryId] = useState(
    initial ? String(initial.category.id) : "",
  );
  const [condition, setCondition] = useState<ListingCondition | "">(
    initial?.condition || "",
  );
  const [busy, setBusy] = useState(false);
  const [imageBusy, setImageBusy] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [images, setImages] = useState(initial?.images || []);
  const [error, setError] = useState<unknown>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || imageBusy) return;
    const validation: Record<string, string> = {};
    if (!title.trim()) validation.title = "Enter a title for your listing.";
    if (!description.trim())
      validation.description = "Tell buyers about your equipment.";
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(price) || Number(price) <= 0)
      validation.price =
        "Enter a price greater than zero with up to two decimal places.";
    if (!categoryId) validation.categoryId = "Choose a sport.";
    if (!condition) validation.condition = "Choose the condition.";
    if (Object.keys(validation).length) {
      setErrors(validation);
      return;
    }
    setBusy(true);
    setError(null);
    setErrors({});
    try {
      await onSave(
        {
          title: title.trim(),
          description: description.trim(),
          price: Number(price),
          categoryId: Number(categoryId),
          condition: condition as ListingCondition,
        },
        files,
      );
    } catch (error) {
      if (error instanceof PartialImageUploadError) {
        setFiles(error.remainingFiles);
        setImages(error.savedImages);
      }
      setError(error);
      setErrors(fieldErrors(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      className="listing-form"
      onSubmit={submit}
      aria-label={initial ? "Edit listing" : "Create listing"}
    >
      <ErrorNotice error={error} />
      <fieldset disabled={busy}>
        <div className="form-section-heading">
          <span className="form-section-icon">
            <Icon name="bag" />
          </span>
          <div>
            <h2>The essentials</h2>
            <p>Help your gear find the right next owner.</p>
          </div>
        </div>
        <Field label="Listing title" name="title" error={errors.title}>
          <input
            id="title"
            required
            maxLength={255}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="e.g. Size 5 soccer ball"
            aria-invalid={!!errors.title}
            aria-describedby={errors.title ? "title-error" : undefined}
          />
        </Field>
        <div className="grid gap-x-6 sm:grid-cols-2">
          <Field
            label="Sport / category"
            name="categoryId"
            error={errors.categoryId}
          >
            <div className="select-wrap">
              <select
                id="categoryId"
                required
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
                aria-invalid={!!errors.categoryId}
                aria-describedby={
                  errors.categoryId ? "categoryId-error" : undefined
                }
              >
                <option value="" disabled>
                  Select a sport
                </option>
                {categories.map((category) => (
                  <option value={category.id} key={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
              <Icon name="down" />
            </div>
          </Field>
          <Field label="Condition" name="condition" error={errors.condition}>
            <div className="select-wrap">
              <select
                id="condition"
                required
                value={condition}
                onChange={(event) =>
                  setCondition(event.target.value as ListingCondition)
                }
                aria-invalid={!!errors.condition}
                aria-describedby={
                  errors.condition ? "condition-error" : undefined
                }
              >
                <option value="" disabled>
                  Select condition
                </option>
                {Object.entries(conditions).map(([value, label]) => (
                  <option value={value} key={value}>
                    {label}
                  </option>
                ))}
              </select>
              <Icon name="down" />
            </div>
          </Field>
        </div>
        <Field
          label="Description"
          name="description"
          error={errors.description}
          hint="Include size, wear, and anything the next owner should know."
        >
          <textarea
            id="description"
            required
            rows={6}
            maxLength={10000}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Tell the story of your gear..."
            aria-invalid={!!errors.description}
            aria-describedby={`description-hint${errors.description ? " description-error" : ""}`}
          />
        </Field>
        <div className="form-divider" />
        <Field
          label="Price"
          name="price"
          error={errors.price}
          hint="Set a fair price for the next chapter."
        >
          <div className="price-input">
            <span aria-hidden="true">$</span>
            <input
              id="price"
              type="number"
              required
              min="0.01"
              max="9999999999.99"
              step="0.01"
              inputMode="decimal"
              value={price}
              onChange={(event) => setPrice(event.target.value)}
              placeholder="0.00"
              aria-invalid={!!errors.price}
              aria-describedby={`price-hint${errors.price ? " price-error" : ""}`}
            />
          </div>
        </Field>
        <ImageUploadControl
          listingId={initial?.id}
          images={images}
          files={files}
          setImages={setImages}
          setFiles={setFiles}
          onBusy={setImageBusy}
        />
        <div className="form-actions">
          <Link
            className="btn btn-secondary"
            to={initial ? `/listings/${initial.id}` : "/my-listings"}
          >
            Cancel
          </Link>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={imageBusy}
          >
            {busy
              ? "Saving listing..."
              : initial
                ? "Save changes"
                : "Publish listing"}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
