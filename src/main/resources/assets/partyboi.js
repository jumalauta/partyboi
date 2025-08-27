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
                    method: "POST",
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

    // Update file uploader according to the selected compo
    const compoSelector = target.querySelector('select[name="compoId"]');
    const fileUpload = target.querySelector('input[type="file"]');
    if (compoSelector && fileUpload) {
        async function updateFileUploadInput(selectedOptionElement) {
            const optionData = selectedOptionElement?.dataset;
            fileUpload.parentElement.style.visibility =
                !optionData || optionData.uploadenabled ? "visible" : "hidden";
            if (optionData) {
                fileUpload.setAttribute("accept", optionData.accept);
            } else {
                fileUpload.removeAttribute("accept");
            }
        }

        compoSelector.onchange = (event) => {
            const value = event.target.value;
            const option = event.target.querySelector(`option[value="${value}"]`);
            updateFileUploadInput(option);
        };
        const defaultOption = compoSelector.querySelector("option:first-child");
        updateFileUploadInput(defaultOption);
    }

    // Add Windows detection to specific links
    if (window.navigator.oscpu?.includes("Windows")) {
        const osLinks = document.querySelectorAll("a.osSpecific");
        osLinks.forEach((link) => {
            link.setAttribute("href", link.getAttribute("href") + "?win=true");
        });
    }

    // Snackbars
    target.querySelectorAll(".snackbars li").forEach((snackbar) => {
        const dismiss = snackbar.querySelector("a");
        dismiss.addEventListener("click", () => {
            snackbar.classList.add("disappear");
            setTimeout(() => snackbar.remove(), 200)
        })
    })

    // Date time pickers
    target.querySelectorAll("input[type=datetime]").forEach(input => {
        const tz = input.attributes["data-tz"]?.value || "Z"
        const defaultDate = input.attributes["data-suggested-value"]?.value
        const self = flatpickr(input, {
            locale: {firstDayOfWeek: 1},
            dateFormat: 'Y-m-d\\TH:i:S' + tz,
            enableTime: true,
            time_24hr: true,
            altInput: true,
            altFormat: "H:i d.m.Y",
            allowInput: true,
            onOpen: (value) => {
                if (value.length === 0) {
                    if (defaultDate) self.setDate(defaultDate)
                }
            }
        })
    })
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
    if (container) {
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
}

resizePreview();
window.addEventListener("resize", resizePreview);

const refreshOnSignalAbortControllers = {}

function registerAbortController(key) {
    const oldCtrl = refreshOnSignalAbortControllers[key];
    if (oldCtrl) {
        oldCtrl.abort()
    }
    const ctrl = new AbortController()
    refreshOnSignalAbortControllers[key] = ctrl;
    return ctrl
}

// Update screen on signals
function refreshOnSignal(signalType) {
    let retryCount = 0;

    function wait() {
        if (++retryCount === 20) {
            location.reload();
            return;
        }
        const abortSignal = registerAbortController(signalType).signal;
        fetch(`/signals/${signalType}`, {
            signal: abortSignal,
            headers: {
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
            }
        })
            .then(() => {
                smoothReload();
                wait();
            })
            .catch((err) => {
                if (err.name === "AbortError") {
                    // Cancelled, do nothing
                } else {
                    const sleep = 1000 * retryCount;
                    setTimeout(wait, sleep);
                }
            });
    }

    wait();
}

window.addEventListener("focus", async () => {
    const keys = Object.keys(refreshOnSignalAbortControllers)
    if (keys.length > 0) {
        keys.forEach(refreshOnSignal)
        await smoothReload()
    }
});

// Init
initInteractions(document);
