export interface PasswordRules {
  length: boolean;
  upper: boolean;
  lower: boolean;
  number: boolean;
  symbol: boolean;
  noSpaces: boolean;
  notCommon: boolean;
  noIdentity: boolean;
}

const COMMON_FRAGMENTS = [
  "password",
  "qwerty",
  "admin",
  "dasig",
  "welcome",
  "letmein",
  "123",
  "abc",
];

export function getPasswordRules(
  password: string,
  identityValues: Array<string | undefined | null> = [],
): PasswordRules {
  const normalized = password.toLowerCase();
  return {
    length: password.length >= 12,
    upper: /[A-Z]/.test(password),
    lower: /[a-z]/.test(password),
    number: /[0-9]/.test(password),
    symbol: /[^A-Za-z0-9]/.test(password),
    noSpaces: !/\s/.test(password),
    notCommon: !COMMON_FRAGMENTS.some((part) => normalized.includes(part)),
    noIdentity: !identityValues.some((value) =>
      containsIdentityFragment(normalized, value),
    ),
  };
}

export function isStrongPassword(
  password: string,
  identityValues: Array<string | undefined | null> = [],
) {
  return Object.values(getPasswordRules(password, identityValues)).every(Boolean);
}

export function firstPasswordError(
  password: string,
  identityValues: Array<string | undefined | null> = [],
) {
  const rules = getPasswordRules(password, identityValues);
  if (!rules.length) return "Password must be at least 12 characters.";
  if (!rules.noSpaces) return "Password cannot contain spaces.";
  if (!rules.upper) return "Password must include an uppercase letter.";
  if (!rules.lower) return "Password must include a lowercase letter.";
  if (!rules.number) return "Password must include a number.";
  if (!rules.symbol) return "Password must include a special character.";
  if (!rules.notCommon) return "Password is too easy to guess.";
  if (!rules.noIdentity) return "Password cannot contain your name or email.";
  return "";
}

function containsIdentityFragment(
  normalizedPassword: string,
  value: string | undefined | null,
) {
  if (!value) return false;
  const localPart = value.trim().toLowerCase().split("@")[0] ?? "";
  const fragments = localPart
    .split(/[^a-z0-9]+/g)
    .map((part) => part.replace(/[^a-z0-9]/g, ""))
    .filter((part) => part.length >= 3);
  const compact = localPart.replace(/[^a-z0-9]/g, "");
  if (compact.length >= 3) {
    fragments.push(compact);
  }
  return fragments.some((part) => normalizedPassword.includes(part));
}
