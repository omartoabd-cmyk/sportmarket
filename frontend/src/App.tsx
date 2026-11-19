import { useEffect } from "react";
import { Link, Route, Routes, useLocation } from "react-router";
import { RequireAuth } from "./auth/AuthContext";
import { Layout } from "./components/Layout";
import { HomePage } from "./pages/HomePage";
import { ListingDetailsPage } from "./pages/ListingDetailsPage";
import { AuthPage } from "./pages/AuthPage";
import { ListingEditorPage } from "./pages/ListingEditorPage";
import { MyListingsPage } from "./pages/MyListingsPage";
import { EmptyState } from "./components/Feedback";
function ListingDetailsWithNotice() {
  const location = useLocation();
  return (
    <>
      {location.state?.message && (
        <div className="container">
          <div role="status" className="notice notice-success">
            {location.state.message}
          </div>
        </div>
      )}
      <ListingDetailsPage />
    </>
  );
}
export function App() {
  const location = useLocation();
  useEffect(() => {
    document
      .querySelector<HTMLElement>("#main")
      ?.focus({ preventScroll: true });
  }, [location.pathname]);
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<AuthPage key="login" />} />
        <Route path="register" element={<AuthPage key="register" register />} />
        <Route
          path="listings/new"
          element={
            <RequireAuth>
              <ListingEditorPage key="create" />
            </RequireAuth>
          }
        />
        <Route path="listings/:id" element={<ListingDetailsWithNotice />} />
        <Route
          path="listings/:id/edit"
          element={
            <RequireAuth>
              <ListingEditorPage key="edit" edit />
            </RequireAuth>
          }
        />
        <Route
          path="my-listings"
          element={
            <RequireAuth>
              <MyListingsPage />
            </RequireAuth>
          }
        />
        <Route
          path="*"
          element={
            <div className="container page-section">
              <EmptyState
                title="A little off the field"
                action="Explore gear"
                to="/"
              >
                We could not find that page.{" "}
                <Link className="text-link" to="/">
                  Head back to the marketplace.
                </Link>
              </EmptyState>
            </div>
          }
        />
      </Route>
    </Routes>
  );
}
