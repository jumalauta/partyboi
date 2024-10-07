const screens = [
  document.querySelector("#screen1"),
  document.querySelector("#screen2"),
];
let currentScreen = 0;

let retryCount = 0;
function waitForNext() {
  if (++retryCount === 10) {
    location.reload();
    return;
  }
  fetch("/screen/next")
    .then((r) => r.text())
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
    .catch(() => setTimeout(waitForNext, 1000 * count));
}
waitForNext();
