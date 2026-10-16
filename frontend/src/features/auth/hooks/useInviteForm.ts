import { useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { validateInvitation } from "../../../api/authApi";
import { getPasswordRules } from "../../../lib/passwordPolicy";
import {
  formatExpiry,
  formatRoleLabel,
  getApiErrorMessage,
  isAlreadyUsedInviteError,
  isValidProfileName,
} from "../utils";

export type InviteState = "form" | "success" | "expired" | "already";

/**
 * The /invite page's form: validates the link's token, holds the fields,
 * and computes the live password/name rules. Activation itself signs the
 * user in, so it lives in App with the rest of the session handling.
 */
export function useInviteForm() {
  const location = useLocation();
  const [token, setToken] = useState<string | null>(null);
  const [state, setState] = useState<InviteState>("form");
  const [email, setEmail] = useState("");
  const [roleLabel, setRoleLabel] = useState("");
  const [institution, setInstitution] = useState("");
  const [countdown, setCountdown] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const rules = useMemo(() => {
    const passwordRules = getPasswordRules(password, [email, firstName, lastName]);
    return {
      firstName: isValidProfileName(firstName),
      lastName: isValidProfileName(lastName),
      ...passwordRules,
      match: confirmPassword.length > 0 && password === confirmPassword,
    };
  }, [email, firstName, lastName, password, confirmPassword]);

  useEffect(() => {
    if (location.pathname !== "/invite") return;
    const params = new URLSearchParams(location.search);
    const linkToken = params.get("token") || params.get("inviteToken");
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      if (!linkToken) {
        setState("expired");
        return;
      }
      setToken(linkToken);
      validateInvitation(linkToken.trim())
        .then((response) => {
          if (!active) return;
          const data = response.data;
          setEmail(data.recipientEmail);
          setRoleLabel(formatRoleLabel(data.assignedRole));
          setInstitution(data.institutionName);
          setCountdown(`Invitation expires ${formatExpiry(data.expiresAt)}`);
          setFirstName("");
          setLastName("");
          setPassword("");
          setConfirmPassword("");
          setState("form");
        })
        .catch((err: unknown) => {
          if (!active) return;
          const message = getApiErrorMessage(err, "Invalid invitation token.");
          setState(isAlreadyUsedInviteError(message) ? "already" : "expired");
        });
    });
    return () => {
      active = false;
    };
  }, [location.pathname, location.search]);

  return {
    token,
    state,
    setState,
    email,
    roleLabel,
    institution,
    countdown,
    firstName,
    setFirstName,
    lastName,
    setLastName,
    password,
    setPassword,
    confirmPassword,
    setConfirmPassword,
    showPassword,
    toggleShowPassword: () => setShowPassword((v) => !v),
    showConfirmPassword,
    toggleShowConfirmPassword: () => setShowConfirmPassword((v) => !v),
    loading,
    setLoading,
    rules,
  };
}
