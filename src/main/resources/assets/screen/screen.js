const screens = [
  document.querySelector("#screen1"),
  document.querySelector("#screen2"),
];

let currentScreen = 0;
let retryCount = 0;
let countdownInterval = null;

function formatCountdown(ms) {
  const totalSecs = Math.ceil(Math.max(0, ms) / 1000);
  const hours = Math.floor(totalSecs / 3600);
  const minutes = Math.floor((totalSecs % 3600) / 60);
  const seconds = totalSecs % 60;
  const pad = (n) => String(n).padStart(2, "0");
  return hours > 0
    ? `${hours}:${pad(minutes)}:${pad(seconds)}`
    : `${minutes}:${pad(seconds)}`;
}

// Slides arrive via innerHTML, so their inline scripts never run; the countdown ticking and
// flashing are driven from here, bound to data attributes rendered on the slide.
function bindCountdown(root) {
  clearInterval(countdownInterval);
  countdownInterval = null;
  const el = root.querySelector(".countdown");
  if (!el) return;
  const serverNow = Number(el.dataset.serverNow || 0);
  const offset = serverNow ? serverNow - Date.now() : 0;
  const endsAt = el.dataset.endsAt ? Number(el.dataset.endsAt) : null;
  const frozen = el.dataset.remainingMs ? Number(el.dataset.remainingMs) : null;
  const flashMs = Number(el.dataset.flashThreshold || 60) * 1000;
  const finished = el.dataset.finished === "true";
  const timeEl = el.querySelector(".countdown-time");

  const tick = () => {
    const remaining =
      frozen !== null ? frozen : Math.max(0, endsAt - (Date.now() + offset));
    const ended = finished || (frozen === null && remaining === 0);
    timeEl.textContent = ended ? "Time's up!" : formatCountdown(remaining);
    el.classList.toggle(
      "flash",
      !finished && frozen === null && remaining > 0 && remaining <= flashMs,
    );
    el.classList.toggle("ended", finished || remaining === 0);
    el.classList.toggle("paused", frozen !== null && !finished);
  };
  tick();
  if (!finished && frozen === null && endsAt !== null) {
    countdownInterval = setInterval(tick, 250);
  }
}

function waitForNext() {
  if (++retryCount === 20) {
    console.log("20 retries failed, reload page");
    location.reload();
    return;
  }
  console.log("wait for next slide...");
  fetch("/screen/next")
    .then((r) => {
      return r.text();
    })
    .then((html) => {
      currentScreen = (currentScreen + 1) % 2;
      screens[currentScreen].innerHTML = html;
      bindCountdown(screens[currentScreen]);
      screens.forEach((s) => s.classList.toggle("shown"));

      const hiding = screens[1 - currentScreen];
      hiding.classList.add("hiding");
      setTimeout(() => {
        hiding.classList.remove("hiding");
      }, 1000);

      retryCount = 0;
      waitForNext();
    })
    .catch(() => {
      console.log("fetch failed, retry in", 1000 * retryCount, "ms");
      setTimeout(waitForNext, 1000 * retryCount);
    });
}

// Fullscreen mode
document.body.addEventListener("keydown", (event) => {
  switch (event.key) {
    case "f":
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen();
      } else if (document.exitFullscreen) {
        document.exitFullscreen();
      }
      return;
  }
});

bindCountdown(screens[currentScreen]);
waitForNext();
