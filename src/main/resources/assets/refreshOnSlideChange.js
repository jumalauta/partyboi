async function refreshOnSlideChange() {
  await fetch("/screen/next");
  smoothReload();
  setTimeout(refreshOnSlideChange, 0);
}

refreshOnSlideChange();
