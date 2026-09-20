export function readPasswordResetToken(href: string): string | null {
  try {
    const token = new URL(href).searchParams.get("token")?.trim();
    return token || null;
  } catch {
    return null;
  }
}
