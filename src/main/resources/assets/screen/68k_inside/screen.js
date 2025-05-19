const screens = [
  document.querySelector("#screen1"),
  document.querySelector("#screen2"),
];

let currentScreen = 0;
let retryCount = 0;

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

waitForNext();
