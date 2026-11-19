import { useEffect, useRef, type ReactNode } from "react";
import { Link } from "react-router";
import { errorMessage } from "../api/client";
import { Icon } from "./Icon";
export function ErrorNotice({ error }: { error: unknown }) {
  return error ? (
    <div className="notice notice-error" role="alert">
      {errorMessage(error)}
    </div>
  ) : null;
}
export function ResourceError({
  error,
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  return (
    <div className="empty-state">
      <Icon name="shield" width="36" height="36" />
      <h2>Something got in the way</h2>
      <p>{errorMessage(error)}</p>
      <button className="btn btn-secondary" onClick={retry}>
        Try again
      </button>
    </div>
  );
}
export function EmptyState({
  title,
  children,
  action,
  to,
}: {
  title: string;
  children: ReactNode;
  action?: string;
  to?: string;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Icon name="bag" width="32" height="32" />
      </span>
      <h2>{title}</h2>
      <p>{children}</p>
      {action && to && (
        <Link className="btn btn-primary" to={to}>
          {action}
        </Link>
      )}
    </div>
  );
}
export function Loading({ cards = false }: { cards?: boolean }) {
  return (
    <div role="status" aria-label="Loading">
      <span className="sr-only">Loading, please wait.</span>
      {cards ? (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <div className="skeleton-card" key={index}>
              <div className="skeleton-image" />
              <div className="skeleton-line" />
              <div className="skeleton-line short" />
            </div>
          ))}
        </div>
      ) : (
        <div className="loading-inline">
          <span className="spinner" /> Loading...
        </div>
      )}
    </div>
  );
}
export function Field({
  label,
  name,
  error,
  children,
  hint,
}: {
  label: string;
  name: string;
  error?: string;
  children: ReactNode;
  hint?: string;
}) {
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      {children}
      {hint && (
        <p id={`${name}-hint`} className="field-hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${name}-error`} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
export function ConfirmDialog({
  title,
  children,
  pending,
  onCancel,
  onConfirm,
}: {
  title: string;
  children: ReactNode;
  pending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="confirm-dialog"
      aria-labelledby="confirm-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!pending) onCancel();
      }}
    >
      <div className="dialog-icon">
        <Icon name="trash" />
      </div>
      <h2 id="confirm-title">{title}</h2>
      <div className="muted">{children}</div>
      <div className="flex justify-end gap-3 mt-7">
        <button
          className="btn btn-secondary"
          disabled={pending}
          onClick={onCancel}
          autoFocus
        >
          Keep listing
        </button>
        <button
          className="btn btn-danger"
          disabled={pending}
          onClick={onConfirm}
        >
          {pending ? "Deleting..." : "Delete listing"}
        </button>
      </div>
    </dialog>
  );
}
