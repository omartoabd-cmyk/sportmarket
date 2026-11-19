import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { api, fieldErrors } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { ErrorNotice, Field } from "../components/Feedback";
import { Icon } from "../components/Icon";
export function AuthPage({ register = false }: { register?: boolean }) {
  const { login, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const requested = location.state?.from;
  const destination =
    typeof requested === "string" &&
    requested.startsWith("/") &&
    !requested.startsWith("//")
      ? requested
      : "/my-listings";
  if (user) return <Navigate to={destination} replace />;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    if (new TextEncoder().encode(password).length > 72) {
      setErrors({ password: "Password must not exceed 72 UTF-8 bytes." });
      return;
    }
    setBusy(true);
    setError(null);
    setErrors({});
    try {
      if (register) {
        await api.register({ fullName, email, password });
        navigate("/login", {
          replace: true,
          state: {
            from: requested,
            message: "Your account is ready. Log in to get started.",
          },
        });
      } else {
        await login({ email, password });
        navigate(destination, { replace: true });
      }
    } catch (error) {
      setError(error);
      setErrors(fieldErrors(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="container auth-page">
      <aside className="auth-story">
        <p className="eyebrow">MORE THAN A MARKETPLACE</p>
        <h1>
          More game.
          <br />
          Less sitting
          <br />
          in the garage.
        </h1>
        <p>
          Find the gear for your next chapter, or pass yours on to someone who
          needs it.
        </p>
        <div className="auth-story-line" />
        <span>
          <Icon name="check" /> A community built around sport.
        </span>
        <span>
          <Icon name="check" /> A second life for good equipment.
        </span>
        <div className="auth-ball" aria-hidden="true" />
      </aside>
      <section className="auth-form-panel">
        <p className="eyebrow text-green">
          {register ? "YOUR NEXT CHAPTER STARTS HERE" : "BACK IN THE GAME"}
        </p>
        <h2>{register ? "Join the community." : "Welcome back."}</h2>
        <p className="muted">
          {register
            ? "Create an account to give good gear a new home."
            : "Log in to manage your gear and keep things moving."}
        </p>
        {location.state?.message && (
          <div className="notice notice-success" role="status">
            {location.state.message}
          </div>
        )}
        <ErrorNotice error={error} />
        <form
          onSubmit={submit}
          className="stacked-form"
          aria-label={register ? "Registration" : "Login"}
        >
          <fieldset disabled={busy}>
            {register && (
              <Field label="Full name" name="fullName" error={errors.fullName}>
                <input
                  id="fullName"
                  name="fullName"
                  required
                  maxLength={255}
                  autoComplete="name"
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  aria-invalid={!!errors.fullName}
                  aria-describedby={
                    errors.fullName ? "fullName-error" : undefined
                  }
                  placeholder="Your full name"
                />
              </Field>
            )}
            <Field label="Email address" name="email" error={errors.email}>
              <input
                id="email"
                name="email"
                type="email"
                required
                maxLength={255}
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? "email-error" : undefined}
                placeholder="you@example.com"
              />
            </Field>
            <Field
              label="Password"
              name="password"
              error={errors.password}
              hint={
                register
                  ? "At least 8 characters. Maximum 72 UTF-8 bytes."
                  : undefined
              }
            >
              <input
                id="password"
                name="password"
                type="password"
                required
                minLength={register ? 8 : 1}
                maxLength={72}
                autoComplete={register ? "new-password" : "current-password"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                aria-invalid={!!errors.password}
                aria-describedby={
                  [
                    register ? "password-hint" : "",
                    errors.password ? "password-error" : "",
                  ]
                    .filter(Boolean)
                    .join(" ") || undefined
                }
                placeholder={
                  register ? "Create a strong password" : "Enter your password"
                }
              />
            </Field>
            <button className="btn btn-primary w-full" type="submit">
              {busy
                ? register
                  ? "Creating account..."
                  : "Logging in..."
                : register
                  ? "Create account"
                  : "Login"}
            </button>
          </fieldset>
        </form>
        <p className="auth-switch">
          {register ? "Already part of the team?" : "New to SportMarket?"}{" "}
          <Link
            to={register ? "/login" : "/register"}
            state={{ from: requested }}
            className="text-link"
          >
            {register ? "Login" : "Create an account"}
          </Link>
        </p>
        <p className="auth-footnote">
          <Icon name="shield" width="16" height="16" /> Your next great season
          starts with good gear.
        </p>
      </section>
    </div>
  );
}
