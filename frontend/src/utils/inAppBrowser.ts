/**
 * Detects mobile "in-app browser" popups (Facebook/Messenger, Instagram, Line,
 * WeChat, TikTok, LinkedIn, Snapchat, generic Android WebView) launched when a
 * user taps a link from within another app. These popups are known to reuse
 * the same WebView/tab instance across taps instead of a true fresh page
 * load — a fresh invite/reset link opened from the same host app can render
 * with a prior page's JS state still in memory (e.g. a stale token captured
 * on first mount), which looks like the new link "isn't working" even though
 * the server-side token is fine.
 */
export function isInAppBrowser(): boolean {
  if (typeof navigator === "undefined") return false;
  const ua = navigator.userAgent || "";
  return /FBAN|FBAV|Instagram|Line\/|MicroMessenger|TikTok|musical_ly|LinkedInApp|Snapchat|Pinterest|; wv\)/i.test(
    ua,
  );
}
