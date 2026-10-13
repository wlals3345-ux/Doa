// Small local catalogue assets load immediately, and recover from brief server interruptions.
document.addEventListener('error', event => {
  const image = event.target;
  if (!(image instanceof HTMLImageElement)) return;
  const url = new URL(image.currentSrc || image.src, document.baseURI);
  if (url.origin !== location.origin || !url.pathname.startsWith('/assets/')) return;
  const attempt = Number(image.dataset.imageRetry || 0);
  if (attempt >= 3) return;
  image.dataset.imageRetry = String(attempt + 1);
  setTimeout(() => {
    if (!image.isConnected || (image.complete && image.naturalWidth > 0)) return;
    url.searchParams.set('image-retry', String(attempt + 1));
    image.src = url.href;
  }, [500, 1500, 3000][attempt]);
}, true);
