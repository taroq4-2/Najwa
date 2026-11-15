(() => {
  "use strict";
  const button = document.querySelector("[data-share]");
  const status = document.getElementById("share-status");
  const fallback = document.getElementById("share-link");
  if (!button || !status || !fallback) return;
  const ar = document.documentElement.lang === "ar";
  const url = document.querySelector('link[rel="canonical"]').href;
  const title = document.querySelector('meta[property="og:title"]').content;
  const text = ar ? "مواقيت الصلاة والأذان والإقامة، في تجربة هادئة." : "Prayer times, adhan and iqama, in a calmer daily rhythm.";
  async function copyLink() {
    try {
      if (!navigator.clipboard || !window.isSecureContext) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(url);
      status.textContent = ar ? "تم نسخ الرابط، جاهز للمشاركة." : "Link copied. Ready to share.";
    } catch {
      fallback.hidden = false;
      fallback.value = url;
      fallback.focus();
      fallback.select();
      status.textContent = ar ? "انسخ هذا الرابط وشاركه مع من تحب." : "Copy this link and share it.";
    }
  }
  button.hidden = false;
  button.addEventListener("click", async () => {
    button.disabled = true;
    status.textContent = "";
    fallback.hidden = true;
    try {
      if (typeof navigator.share === "function") {
        try { await navigator.share({ title, text, url }); }
        catch (error) { if (error.name !== "AbortError") await copyLink(); }
      } else { await copyLink(); }
    } finally { button.disabled = false; }
  });
})();
