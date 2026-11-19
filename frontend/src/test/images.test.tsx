import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, imageUrl } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";
import { ListingCard } from "../components/ListingCard";
import { ImageGallery } from "../components/ImageGallery";
import { ListingForm } from "../components/ListingForm";
import { App } from "../App";
import { listing, response, user } from "./fixtures";
import type { ListingImage } from "../types";
const photo: ListingImage = {
  id: 10,
  listingId: listing.id,
  contentType: "image/png",
  displayOrder: 0,
  createdAt: listing.createdAt,
  url: "/api/listing-images/10/content",
};
const file = (name = "ball.png", type = "image/png") =>
  new File(["test-image-data"], name, { type });
function wrapper(children: React.ReactNode) {
  return (
    <MemoryRouter>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  );
}
beforeEach(() => {
  URL.createObjectURL = vi.fn(() => "blob:preview-" + Math.random());
  URL.revokeObjectURL = vi.fn();
});
describe("listing photos", () => {
  it("sends multipart data with a bearer token and lets the browser set the boundary", async () => {
    const fetcher = vi.fn().mockResolvedValue(response(photo, 201));
    vi.stubGlobal("fetch", fetcher);
    const selected = file();
    await api.uploadImage(listing.id, selected, "test-token");
    const [url, options] = fetcher.mock.calls[0];
    expect(url).toBe(`/api/listings/${listing.id}/images`);
    expect(options.body).toBeInstanceOf(FormData);
    expect((options.body as FormData).get("file")).toBe(selected);
    expect(options.headers.has("Content-Type")).toBe(false);
    expect(options.headers.get("Authorization")).toBe("Bearer test-token");
  });
  it("shows listing cover photos and falls back when a photo cannot load", () => {
    render(wrapper(<ListingCard listing={{ ...listing, images: [photo] }} />));
    const image = screen.getByRole("img", { name: listing.title });
    expect(image).toHaveAttribute("src", imageUrl(photo.id));
    fireEvent.error(image);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("GOOD GEAR. MORE GAME.")).toBeInTheDocument();
  });
  it("lets readers switch gallery photos", async () => {
    render(
      <ImageGallery
        title={listing.title}
        images={[photo, { ...photo, id: 11, displayOrder: 1 }]}
      />,
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "View photo 2" }));
    expect(screen.getByRole("img", { name: listing.title })).toHaveAttribute(
      "src",
      imageUrl(11),
    );
    expect(
      screen.getByRole("button", { name: "View photo 2" }),
    ).toHaveAttribute("aria-pressed", "true");
  });
  it("previews selected files, removes selections, and revokes object URLs", async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const view = render(
      wrapper(
        <ListingForm
          initial={listing}
          categories={[listing.category]}
          onSave={save}
        />,
      ),
    );
    const actions = userEvent.setup();
    await actions.upload(
      screen.getByLabelText("Choose listing photos"),
      file(),
    );
    expect(
      await screen.findByRole("img", { name: "Selected photo: ball.png" }),
    ).toBeInTheDocument();
    await actions.click(
      screen.getByRole("button", { name: "Remove selected photo 1" }),
    );
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
    await actions.click(screen.getByRole("button", { name: "Save changes" }));
    expect(save.mock.calls[0][1]).toEqual([]);
    view.unmount();
  });
  it("rejects unsupported formats, oversized files, and more than five selections", async () => {
    render(
      wrapper(
        <ListingForm
          initial={listing}
          categories={[listing.category]}
          onSave={vi.fn()}
        />,
      ),
    );
    const actions = userEvent.setup({ applyAccept: false });
    const input = screen.getByLabelText("Choose listing photos");
    await actions.upload(input, file("unsafe.svg", "image/svg+xml"));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Choose JPEG, PNG, or WebP",
    );
    await actions.upload(
      input,
      new File([new Uint8Array(5 * 1024 * 1024 + 1)], "big.png", {
        type: "image/png",
      }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("no larger than 5 MB");
    await actions.upload(
      input,
      Array.from({ length: 6 }, (_, index) => file(`photo-${index}.png`)),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("at most 5 photos");
  });
  it("reports partial upload failure and retries without creating another listing", async () => {
    let saved = listing;
    let attempt = 0;
    const fetcher = vi.fn(async (url: string, options: RequestInit = {}) => {
      if (url.endsWith("/auth/login"))
        return response({
          accessToken: "test-token",
          tokenType: "Bearer",
          expiresIn: 3600,
        });
      if (url.endsWith("/users/me")) return response(user);
      if (url.endsWith("/categories")) return response([listing.category]);
      if (url.endsWith(`/listings/${listing.id}/images`)) {
        attempt++;
        if (attempt === 1)
          return response({ detail: "Invalid image content." }, 400);
        saved = { ...saved, images: [photo] };
        return response(photo, 201);
      }
      if (url.endsWith("/listings") && options?.method === "POST") {
        saved = { ...listing, ...JSON.parse(String(options.body)) };
        return response(saved, 201);
      }
      if (url.endsWith(`/listings/${listing.id}`)) return response(saved);
      return response([], 200);
    });
    vi.stubGlobal("fetch", fetcher);
    render(
      <MemoryRouter initialEntries={["/listings/new"]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    const actions = userEvent.setup();
    await actions.type(screen.getByLabelText("Email address"), user.email);
    await actions.type(screen.getByLabelText("Password"), "test-password");
    await actions.click(screen.getByRole("button", { name: "Login" }));
    await screen.findByRole("form", { name: "Create listing" });
    await actions.type(screen.getByLabelText("Listing title"), "Soccer ball");
    await actions.type(screen.getByLabelText("Description"), "A good ball");
    await actions.type(screen.getByLabelText("Price"), "24.99");
    await actions.selectOptions(screen.getByLabelText("Sport / category"), "1");
    await actions.selectOptions(screen.getByLabelText("Condition"), "GOOD");
    await actions.upload(
      screen.getByLabelText("Choose listing photos"),
      file(),
    );
    await actions.click(
      screen.getByRole("button", { name: "Publish listing" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "was saved, but only 0 of 1",
    );
    await actions.click(screen.getByRole("button", { name: "Save changes" }));
    expect(
      await screen.findByRole("heading", { name: "About this gear" }),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.filter(
        ([url, options]) =>
          url.endsWith("/listings") && options?.method === "POST",
      ),
    ).toHaveLength(1);
    expect(
      fetcher.mock.calls.some(([, options]) => options?.method === "PUT"),
    ).toBe(true);
    expect(screen.getByRole("img", { name: listing.title })).toHaveAttribute(
      "src",
      imageUrl(photo.id),
    );
  });
  it("allows the owner to delete an existing photo from the edit form", async () => {
    const fetcher = vi.fn(async (url: string, options: RequestInit = {}) => {
      if (url.endsWith("/auth/login"))
        return response({
          accessToken: "test-token",
          tokenType: "Bearer",
          expiresIn: 3600,
        });
      if (url.endsWith("/users/me")) return response(user);
      if (url.endsWith("/categories")) return response([listing.category]);
      if (options?.method === "DELETE") return response(null, 204);
      return response({ ...listing, images: [photo] });
    });
    vi.stubGlobal("fetch", fetcher);
    render(
      <MemoryRouter initialEntries={["/listings/4/edit"]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    const actions = userEvent.setup();
    await actions.type(screen.getByLabelText("Email address"), user.email);
    await actions.type(screen.getByLabelText("Password"), "test-password");
    await actions.click(screen.getByRole("button", { name: "Login" }));
    await actions.click(
      await screen.findByRole("button", { name: "Delete saved photo 1" }),
    );
    await waitFor(() =>
      expect(
        screen.queryByRole("img", { name: "Saved listing photo 1" }),
      ).not.toBeInTheDocument(),
    );
    expect(
      fetcher.mock.calls.some(
        ([url, options]) =>
          url === "/api/listings/4/images/10" && options?.method === "DELETE",
      ),
    ).toBe(true);
  });
});
