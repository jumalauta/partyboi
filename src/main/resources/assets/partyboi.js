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

initInteractions(document);
