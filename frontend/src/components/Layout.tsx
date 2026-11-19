import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Icon } from "./Icon";
export function Brand() {
  return (
    <Link to="/" className="brand" aria-label="SportMarket home">
      <span className="brand-mark">
        <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <path
            d="m6 22 9-15h10l-9 15H6Zm9 4 9-15"
            stroke="currentColor"
            strokeWidth="3"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <span>
        sport<span className="brand-light">market</span>
        <span className="brand-period">.</span>
      </span>
    </Link>
  );
}
export function Layout() {
  const { user, logout, notice, dismissNotice } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);
  useEffect(() => {
    window.scrollTo?.(0, 0);
    document.title = "SportMarket | Good gear. Next chapter.";
  }, [location.pathname]);
  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <div className="announcement">
        Keep good gear in the game.<span> Buy pre-loved. Play more.</span>
      </div>
      <header className="site-header">
        <div className="container nav-inner">
          <Brand />
          <button
            className="menu-toggle"
            aria-label={open ? "Close navigation" : "Open navigation"}
            aria-expanded={open}
            aria-controls="main-navigation"
            onClick={() => setOpen(!open)}
          >
            <Icon name={open ? "close" : "menu"} />
          </button>
          <nav
            id="main-navigation"
            aria-label="Main navigation"
            className={open ? "navigation open" : "navigation"}
          >
            <NavLink to="/" end>
              Explore gear
            </NavLink>
            {user ? (
              <>
                <NavLink to="/my-listings">My listings</NavLink>
                <NavLink className="nav-create" to="/listings/new">
                  <Icon name="plus" /> Create listing
                </NavLink>
                <span className="nav-user" title={user.fullName}>
                  <span className="avatar">
                    {user.fullName.charAt(0).toUpperCase()}
                  </span>
                  <span className="sr-only">Signed in as </span>
                  {user.fullName.split(" ")[0]}
                </span>
                <button
                  className="nav-logout"
                  onClick={() => {
                    logout();
                    navigate("/");
                  }}
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login">Login</NavLink>
                <NavLink className="nav-create" to="/register">
                  Register
                </NavLink>
              </>
            )}
          </nav>
        </div>
      </header>
      {notice && (
        <div className="container">
          <div
            className="notice notice-info flex justify-between items-center gap-4"
            role="status"
          >
            {notice}
            <button aria-label="Dismiss message" onClick={dismissNotice}>
              <Icon name="close" />
            </button>
          </div>
        </div>
      )}
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <div>
            <Brand />
            <p>A second life for gear. A new start for you.</p>
          </div>
          <div className="footer-links">
            <Link to="/">Explore gear</Link>
            <Link to={user ? "/listings/new" : "/register"}>
              Sell your equipment
            </Link>
            <span>Made for the love of sport.</span>
          </div>
        </div>
        <div className="container footer-bottom">
          <span>&copy; {new Date().getFullYear()} SportMarket</span>
          <span>Less waste. More play.</span>
        </div>
      </footer>
    </div>
  );
}
