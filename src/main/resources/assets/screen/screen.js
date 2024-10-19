const screens = [
  document.querySelector("#screen1"),
  document.querySelector("#screen2"),
];

let currentScreen = 0;
let retryCount = 0;
let currentSlideId = null;

function waitForNext() {
  if (++retryCount === 20) {
    console.log("20 retries failed, reload page");
    location.reload();
    return;
  }
  console.log("wait for next slide...");
  fetch(
    currentSlideId === null ? "/screen/next" : `/screen/next/${currentSlideId}`
  )
    .then((r) => {
      const id = parseInt(r.headers.get("X-SlideId"));
      currentSlideId = id === NaN ? null : id;
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

waitForNext();
