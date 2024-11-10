"use strict";

function initInteractions(target) {
  // Show a progress bar after click submit button
  const submitBtns = target.querySelectorAll('input[type="submit"]');

  submitBtns.forEach((submitBtn) => {
    submitBtn.addEventListener("click", () => {
      submitBtn.setAttribute("disabled", "disabled");
      const progress = document.createElement("progress");
      submitBtn.after(progress);
      submitBtn.form.submit();
    });
  });

  // Activate sortable lists
  if (window.Draggable) {
    const sortableSelector = ".sortable";
    const sortableContainers = target.querySelectorAll(sortableSelector);

    sortableContainers.forEach((container) => {
      const sortable = new window.Draggable.Sortable(container, {
        draggable: container.dataset.draggable,
        handle: container.dataset.handle,
        mirror: {
          appendTo: container,
          constrainDimensions: true,
        },
      });

      const ids = Array.from(
        container.querySelectorAll(container.dataset.draggable)
      ).map((i) => i.dataset.dragid);

      sortable.on("sortable:sorted", (event) => {
        ids.splice(event.newIndex, 0, ...ids.splice(event.oldIndex, 1));
        fetch(container.dataset.callback, {
          method: "PUT",
          body: JSON.stringify(ids),
          headers: {
            "Content-Type": "application/json",
          },
        });
      });
    });
  }

  // Mobile menu button
  target.querySelectorAll(".mobile-nav-button").forEach((button) => {
    button.onclick = () => {
      document.body
        .querySelector(".nav-and-content > aside")
        .classList.toggle("open");
    };
  });
}

// Smooth page reload
async function smoothReload() {
  try {
    const response = await fetch(location);
    const html = await response.text();

    const reloadSection = html.match(
      /.*(<output id="reload-section">.*<\/output>).*/is
    );
    const reloadTarget = document.querySelector("#reload-section");

    if (reloadSection && reloadTarget) {
      setContent(reloadTarget, reloadSection[1]);
    } else {
      const body = html.match(/.*(<body.*?>.*<\/body>).*/is);
      if (body) {
        setContent(document.body, body[1]);
      }
    }
    window.dispatchEvent(new Event("resize"));
  } catch (err) {
    console.error(err);
  }
}

function setContent(target, html) {
  target.innerHTML = html;
  initInteractions(target);
}

// Resize screen monitoring
function resizePreview() {
  const container = document.querySelector(".screen-preview-container");
  const frame = document.querySelector(".screen-preview");
  const ratio = container.offsetWidth / frame.offsetWidth;
  const scale = ratio * 100 + "%";
  const width = frame.offsetWidth * ratio;
  const height = frame.offsetHeight * ratio;
  container.style.transform = "scale(" + scale + ")";
  container.style.transformOrigin = "top left";
  container.style.width = width + "px";
  container.style.height = height + "px";
}

resizePreview();
window.addEventListener("resize", resizePreview);

// Update screen on signals
function refreshOnSignal(signalType) {
  let retryCount = 0;

  function wait() {
    if (++retryCount === 20) {
      console.log("20 retries failed, reload page");
      location.reload();
      return;
    }
    console.log("Waiting for signal", signalType);
    fetch(`/signals/${signalType}`)
      .then(() => {
        smoothReload();
        wait();
      })
      .catch(() => {
        const sleep = 1000 * retryCount;
        console.log(`Fetch failed, retry in ${sleep} ms`);
        setTimeout(wait, sleep);
      });
  }

  wait();
}

// Init
initInteractions(document);
