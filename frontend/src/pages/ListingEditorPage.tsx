import { useCallback, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
  api,
  ApiError,
  PartialImageUploadError,
  errorMessage,
} from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useResource } from "../hooks/useResource";
import { ListingForm } from "../components/ListingForm";
import { EmptyState, Loading, ResourceError } from "../components/Feedback";
import { Icon } from "../components/Icon";
import type { Listing, ListingInput, ListingImage } from "../types";
export function ListingEditorPage({ edit = false }: { edit?: boolean }) {
  const { id = "" } = useParams();
  const { user, authorized } = useAuth();
  const navigate = useNavigate();
  const [savedDraft, setSavedDraft] = useState<Listing | null>(null);
  const resource = useResource(
    useCallback(
      async (signal: AbortSignal) => {
        const [categories, listing] = await Promise.all([
          api.categories(signal),
          edit ? api.listing(id, signal) : Promise.resolve(null),
        ]);
        return { categories, listing };
      },
      [id, edit],
    ),
  );
  async function save(input: ListingInput, files: File[]) {
    const existingId =
      savedDraft?.id || (edit ? resource.data?.listing?.id : undefined);
    const listing: Listing = await authorized((token) =>
      existingId
        ? api.update(existingId, input, token)
        : api.create(input, token),
    );
    setSavedDraft(listing);
    const uploaded: ListingImage[] = [];
    for (let index = 0; index < files.length; index++) {
      try {
        uploaded.push(
          await authorized((token) =>
            api.uploadImage(listing.id, files[index], token),
          ),
        );
      } catch (error) {
        const savedImages = [...(listing.images || []), ...uploaded];
        setSavedDraft({ ...listing, images: savedImages });
        const message = `Listing ${listing.id} was saved, but only ${uploaded.length} of ${files.length} selected photos uploaded. ${errorMessage(error)} Remove invalid photos or retry saving; the listing will not be created twice.`;
        if (error instanceof ApiError && error.status === 401) {
          navigate("/login", {
            replace: true,
            state: { from: `/listings/${listing.id}/edit`, message },
          });
        }
        throw new PartialImageUploadError(
          listing.id,
          files.slice(index),
          savedImages,
          message,
        );
      }
    }
    navigate(`/listings/${listing.id}`, {
      replace: true,
      state: {
        message: edit
          ? "Your listing has been updated."
          : "Your listing is live. Here is to its next chapter.",
      },
    });
  }
  return (
    <div className="container page-section">
      <Link className="breadcrumb" to="/my-listings">
        My listings
      </Link>
      <span className="breadcrumb-separator">/</span>
      <span className="muted">{edit ? "Edit listing" : "Create listing"}</span>
      <div className="page-heading">
        <p className="eyebrow text-green">KEEP GOOD GEAR GOING</p>
        <h1>{edit ? "A little update." : "Give your gear a next chapter."}</h1>
        <p>
          {edit
            ? "Keep your listing accurate and ready for its next owner."
            : "A few details are all it takes to get your equipment back in the game."}
        </p>
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        resource.error instanceof ApiError && resource.error.status === 404 ? (
          <EmptyState
            title="Listing not found"
            action="My listings"
            to="/my-listings"
          >
            This listing may have been removed.
          </EmptyState>
        ) : (
          <ResourceError error={resource.error} retry={resource.reload} />
        )
      ) : (
        resource.data &&
        (edit && resource.data.listing?.seller.id !== user?.id ? (
          <EmptyState
            title="This gear belongs to someone else"
            action="My listings"
            to="/my-listings"
          >
            Only the owner can edit this listing.
          </EmptyState>
        ) : !resource.data.categories.length ? (
          <EmptyState
            title="Categories are not ready yet"
            action="Back to my listings"
            to="/my-listings"
          >
            Please try again once categories are available.
          </EmptyState>
        ) : (
          <div className="editor-grid">
            <ListingForm
              key={edit ? id : "new"}
              initial={savedDraft || resource.data.listing || undefined}
              categories={resource.data.categories}
              onSave={save}
            />
            <aside className="listing-tips">
              <span className="tips-icon">
                <Icon name="shield" width="26" height="26" />
              </span>
              <h2>A great listing goes a long way.</h2>
              <p>
                Be specific about your equipment and honest about its condition.
              </p>
              <div>
                <strong>Make the title count</strong>
                <p>Include the item, brand, or size to help it stand out.</p>
              </div>
              <div>
                <strong>Tell it like it is</strong>
                <p>
                  A little wear is part of the story. Let the next owner know.
                </p>
              </div>
              <div>
                <strong>Keep it fair</strong>
                <p>
                  A thoughtful price gives good gear another chance to play.
                </p>
              </div>
              <p className="tips-note">
                Your account is automatically set as the seller.
              </p>
            </aside>
          </div>
        ))
      )}
    </div>
  );
}
