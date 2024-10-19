let currentSlideId = null;

async function waitForNextSlide() {
  const response = await fetch(
    !Number.isInteger(currentSlideId)
      ? "/screen/next"
      : `/screen/next/${currentSlideId}`
  );
  currentSlideId = parseInt(response.headers.get("X-SlideId"));
}

async function refreshOnSlideChange() {
  try {
    await waitForNextSlide();
    smoothReload();
    setTimeout(refreshOnSlideChange, 0);
  } catch (err) {
    console.error(err);
    setTimeout(refreshOnSlideChange, 2000);
  }
}

refreshOnSlideChange();
