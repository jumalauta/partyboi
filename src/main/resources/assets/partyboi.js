"use strict";

function initInteractions(target) {
    // Show a progress bar after click submit button
    const submitBtns = target.querySelectorAll('input[type="submit"]');

    submitBtns.forEach((submitBtn) => {
        submitBtn.addEventListener("click", (event) => {
            const form = submitBtn.form;
            submitBtn.setAttribute("disabled", "disabled");
            const progress = document.createElement("progress");
            submitBtn.after(progress);
            // Forms marked data-ajax submit without navigating: the response (a
            // redirect to the list on success, or the re-rendered form with errors)
            // both carry #reload-section, which we patch in place.
            if (form && form.dataset.ajax) {
                event.preventDefault();
                fetch(form.action, {method: form.method || "POST", body: new FormData(form)})
                    .then((res) => res.text())
                    .then((html) => applyReloadSection(html))
                    .catch((err) => console.error(err));
                return;
            }
            form.submit();
        });
    });

    // Inline editable cells: PUT the single value on change.
    // Datetime inputs are handled via the flatpickr onChange below instead, to avoid
    // firing on the initial value parse.
    target
        .querySelectorAll('[data-save-url]:not([type="datetime"])')
        .forEach((el) => {
            el.addEventListener("change", () => saveField(el));
        });

    // Enter moves to the next editable cell (start -> end -> name -> next row's start).
    // Moving focus blurs the current cell, which saves it. Enter on the very last cell
    // creates a new empty event row and focuses its start time.
    target.querySelectorAll("[data-save-url]").forEach((el) => {
        el.addEventListener("keydown", async (event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            const cells = [...document.querySelectorAll("#reload-section [data-save-url]")];
            const next = cells[cells.indexOf(el) + 1];
            if (next) {
                // Moving focus blurs this cell, which saves it (change / flatpickr onChange).
                next.focus();
                if (next.select) next.select();
            } else {
                // Last cell: no blur happens, so persist this edit before the row is added.
                await saveField(el);
                createEmptyEventRow();
            }
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

            // Renumber .place-number cells once the drag fully ends. Defer to
            // a later tick so Draggable can finish swapping the original source
            // element back in for its clone and removing the mirror.
            sortable.on("sortable:stop", () => {
                setTimeout(() => {
                    const rows = container.querySelectorAll(
                        `${container.dataset.draggable}[data-dragid]`
                    );
                    // Skip any leftover Draggable clone/mirror elements.
                    const real = Array.from(rows).filter(
                        (r) =>
                            !r.classList.contains("draggable-mirror") &&
                            !r.classList.contains("draggable--original") &&
                            !r.classList.contains("draggable-source--is-dragging")
                    );
                    real.forEach((row, idx) => {
                        const placeCell = row.querySelector(".place-number");
                        if (placeCell) placeCell.textContent = `${idx + 1}.`;
                    });
                }, 0);
            });
        });
    }

    // Mobile navigation drawer
    setupMobileNav(target);

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

    // Preview modal: enlarge entry preview on click/Enter/Space.
    setupPreviewModal();
    target.querySelectorAll("[data-preview-url]").forEach((el) => {
        el.addEventListener("click", () => openPreviewModal(el.dataset.previewUrl));
        el.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                openPreviewModal(el.dataset.previewUrl);
            }
        });
    });

    // Date time pickers
    target.querySelectorAll("input[type=datetime]").forEach(input => {
        const defaultDate = input.attributes["data-suggested-value"]?.value
        // Editable schedule cells are time-only: the date is fixed (changing it by
        // accident is too easy), so they show no calendar and their value is just H:i.
        const timeOnly = !!input.dataset.timeOnly
        const config = timeOnly
            ? {noCalendar: true, dateFormat: "H:i"}
            : {dateFormat: 'Y-m-d\\TH:i:S', altInput: true, altFormat: "H:i d.m.Y"}
        const self = flatpickr(input, {
            locale: {firstDayOfWeek: 1},
            enableTime: true,
            time_24hr: true,
            allowInput: true,
            ...config,
            onOpen: (value) => {
                if (value.length === 0) {
                    if (defaultDate) self.setDate(defaultDate)
                }
            },
            // An inline editable cell saves its new time directly (form datetime
            // inputs have no data-save-url and submit with the rest of the form).
            onChange: () => {
                if (input.dataset.saveUrl) saveField(input)
            }
        })
    })
}

// Patch the page from a fetched HTML string: replace #reload-section if both the
// response and the current page have one, otherwise swap the whole <body>.
function applyReloadSection(html) {
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
}

// Smooth page reload
async function smoothReload() {
    try {
        const response = await fetch(location);
        applyReloadSection(await response.text());
    } catch (err) {
        console.error(err);
    }
}

// PUT a single inline-edited value. No reload: this keeps focus put while the user
// edits/tabs through cells (the list re-sorts by time on the next reload). Saving is
// triggered by the field's change/flatpickr-onChange, which fire when focus moves.
function saveField(el) {
    const value = el.type === "checkbox" ? String(el.checked) : el.value;
    return fetch(el.dataset.saveUrl, {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({value}),
    }).catch((err) => console.error(err));
}

// Set before a reload to move focus to a specific editable cell once it re-renders
// (used when Enter on the last cell creates a new event row).
let pendingFocus = null;

// Enter on the last editable cell adds an empty event row, then focuses its start time.
async function createEmptyEventRow() {
    try {
        const res = await fetch("/admin/schedule/events/new", {method: "POST"});
        const {id} = await res.json();
        pendingFocus = `/admin/schedule/events/${id}/startTime`;
        smoothReload();
    } catch (err) {
        console.error(err);
    }
}

// "Running late": shift the given event and every later one by the shared step.
function shiftRest(eventId) {
    const minutes = parseInt(document.querySelector("#shift-step")?.value, 10) || 15;
    fetch(`/admin/schedule/shift/${eventId}/${minutes}`, {method: "PUT"})
        .then(() => smoothReload())
        .catch((err) => console.error(err));
}

function setContent(target, html) {
    target.innerHTML = html;
    initInteractions(target);
    if (pendingFocus) {
        const el = document.querySelector(`[data-save-url="${pendingFocus}"]`);
        pendingFocus = null;
        if (el) {
            el.focus();
            if (el.select) el.select();
        }
    }
}

// Resize screen monitoring
function resizePreview() {
    const container = document.querySelector(".screen-preview-container");
    if (container) {
        const frame = document.querySelector(".screen-preview");
        // Clear pinned dimensions from the previous call so offsetWidth reflects
        // the current layout width rather than the value we last set.
        container.style.width = "";
        container.style.height = "";
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

// Mobile navigation drawer. Idempotent across initInteractions() reruns: local
// listeners re-bind to whatever nodes initInteractions was called against;
// document/matchMedia listeners install once and read the current drawer at
// invocation time so they survive full-body swaps from applyReloadSection.
function setupMobileNav(target) {
    const trigger = target.querySelector(".mobile-nav-button");
    const drawer = target.querySelector(".nav-and-content > aside.main-nav");
    const backdrop = target.querySelector(".main-nav-backdrop");
    const closeBtn = target.querySelector(".mobile-nav-close");
    if (!trigger || !drawer || !backdrop) return;

    const mobileMq = window.matchMedia("(max-width: 1023.99px)");
    const state = (window.__navDrawer = window.__navDrawer || {});

    function findDrawer() {
        return document.querySelector(".nav-and-content > aside.main-nav");
    }

    function findBackdrop() {
        return document.querySelector(".main-nav-backdrop");
    }

    function findTrigger() {
        return document.querySelector(".mobile-nav-button");
    }

    function setOpen(open) {
        const d = findDrawer();
        const b = findBackdrop();
        const t = findTrigger();
        if (!d || !b) return;
        if (open) {
            state.lastFocused = document.activeElement;
            b.removeAttribute("hidden");
            // Force reflow so the opacity transition runs from 0.
            void b.offsetWidth;
            d.classList.add("open");
            b.classList.add("open");
            if (t) t.setAttribute("aria-expanded", "true");
            d.removeAttribute("aria-hidden");
            if (mobileMq.matches) document.body.classList.add("nav-open");
            const focusTarget = d.querySelector(".mobile-nav-close") || d;
            focusTarget.focus({preventScroll: true});
        } else {
            d.classList.remove("open");
            b.classList.remove("open");
            if (t) t.setAttribute("aria-expanded", "false");
            if (mobileMq.matches) d.setAttribute("aria-hidden", "true");
            document.body.classList.remove("nav-open");
            const onEnd = (e) => {
                if (e.target !== b || e.propertyName !== "opacity") return;
                b.removeEventListener("transitionend", onEnd);
                if (!b.classList.contains("open")) b.setAttribute("hidden", "");
            };
            b.addEventListener("transitionend", onEnd);
            if (state.lastFocused && typeof state.lastFocused.focus === "function") {
                state.lastFocused.focus({preventScroll: true});
            }
        }
    }

    state.setOpen = setOpen;

    // Make the drawer container focusable so focus can move into it.
    if (!drawer.hasAttribute("tabindex")) drawer.setAttribute("tabindex", "-1");

    // Local listeners: rebind to the freshly found nodes each call.
    trigger.addEventListener("click", () => setOpen(!findDrawer()?.classList.contains("open")));
    backdrop.addEventListener("click", () => setOpen(false));
    if (closeBtn) closeBtn.addEventListener("click", () => setOpen(false));
    drawer.addEventListener("click", (e) => {
        const a = e.target.closest("a[href]");
        if (a && drawer.contains(a)) setOpen(false);
    });

    // Document-level listeners install once.
    if (!state.docHandlersInstalled) {
        document.addEventListener("keydown", (e) => {
            if (e.key !== "Escape") return;
            const d = findDrawer();
            if (d && d.classList.contains("open")) {
                e.stopPropagation();
                state.setOpen(false);
            }
        });
        mobileMq.addEventListener("change", (e) => {
            if (e.matches) return;
            const d = findDrawer();
            const b = findBackdrop();
            const t = findTrigger();
            if (d) {
                d.classList.remove("open");
                d.removeAttribute("aria-hidden");
            }
            if (b) {
                b.classList.remove("open");
                b.setAttribute("hidden", "");
            }
            if (t) t.setAttribute("aria-expanded", "false");
            document.body.classList.remove("nav-open");
        });
        state.docHandlersInstalled = true;
    }

    // Initial ARIA state for mobile.
    if (mobileMq.matches && !drawer.classList.contains("open")) {
        drawer.setAttribute("aria-hidden", "true");
    }
}

// Preview modal: opened on click/Enter/Space on any [data-preview-url] element.
// The <dialog> itself lives in Page.kt (outside #reload-section) so it survives
// applyReloadSection swaps; only the per-element listeners re-bind on each call
// to initInteractions(target).
function openPreviewModal(url) {
    const dialog = document.getElementById("preview-modal");
    const img = document.getElementById("preview-modal-img");
    if (!dialog || !img || !url) return;
    img.src = url;
    dialog.showModal();
}

function setupPreviewModal() {
    const dialog = document.getElementById("preview-modal");
    if (!dialog || window.__previewModalInit) return;
    window.__previewModalInit = true;
    // Click on the backdrop (outside the image and close button) closes the dialog.
    dialog.addEventListener("click", (e) => {
        if (e.target === dialog) dialog.close();
    });
    // Drop the src on close so the browser can reclaim large images.
    dialog.addEventListener("close", () => {
        const img = document.getElementById("preview-modal-img");
        if (img) img.removeAttribute("src");
    });
}

// Init
initInteractions(document);
