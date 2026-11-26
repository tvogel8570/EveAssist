// The shared htmx set-up. Every page loads it from layout.html.
(function () {
    const meta = name => document.querySelector(`meta[name="${name}"]`)?.content;

    document.addEventListener('htmx:config:request', event => {
        const token = meta('_csrf');
        if (token) {
            event.detail.ctx.request.headers[meta('_csrf_header')] = token;
        }
    });

    // htmx collects values before a request waits in its hx-sync queue. An edit that waited needs the version from the
    // response before it, or it would be refused as out of date.
    document.addEventListener('htmx:before:request', event => {
        const body = event.detail.ctx.request.body;
        const version = document.getElementById('plan-version');
        if (version && body instanceof URLSearchParams && body.has('expectedVersion')) {
            body.set('expectedVersion', version.value);
        }
    });

    // A table that says data-sortable sorts its rows when a header that says data-sort ("text" or "num") is clicked: ascending, then descending,
    // then back to the order the page gave. One column at a time: sorting another column clears the first. A cell of a "num" column says its
    // number in data-value, because what it shows is formatted (1h 10m, 14,500,096). The state is in aria-sort, which is what a screen reader reads.
    const SORT_MARKS = {ascending: '\u25B2', descending: '\u25BC', none: ''};
    const NEXT_SORT = {none: 'ascending', ascending: 'descending', descending: 'none'};

    function sortRows(table, header, direction) {
        const body = table.tBodies[0];
        const rows = Array.from(body.rows);
        rows.forEach((row, index) => {
            if (!('original' in row.dataset)) {
                row.dataset.original = String(index);
            }
        });
        const column = header.cellIndex;
        const numeric = header.dataset.sort === 'num';
        const valueOf = row => {
            const cell = row.cells[column];
            const text = cell.dataset.value ?? cell.textContent.trim();
            return numeric ? Number(text) : text;
        };
        const original = (a, b) => Number(a.dataset.original) - Number(b.dataset.original);
        const order = direction === 'none' ? original : (a, b) => {
            const x = valueOf(a);
            const y = valueOf(b);
            const compared = numeric ? x - y : x.localeCompare(y, undefined, {numeric: true, sensitivity: 'base'});
            return (direction === 'descending' ? -compared : compared) || original(a, b);
        };
        rows.sort(order).forEach(row => body.appendChild(row));
    }

    document.addEventListener('click', event => {
        const button = event.target.closest('table[data-sortable] th[data-sort] button');
        if (!button) {
            return;
        }
        const header = button.closest('th');
        const table = header.closest('table');
        const next = NEXT_SORT[header.getAttribute('aria-sort') || 'none'];
        table.querySelectorAll('th[data-sort]').forEach(other => {
            other.setAttribute('aria-sort', 'none');
            other.querySelector('[data-sort-mark]').textContent = SORT_MARKS.none;
        });
        header.setAttribute('aria-sort', next);
        header.querySelector('[data-sort-mark]').textContent = SORT_MARKS[next];
        sortRows(table, header, next);
    });

    // A dropdown entry that says data-goto leads to another page, and is not a value: choosing it opens the page, and the dropdown goes back to the
    // value it had, so that coming back with the browser's back button does not find the entry chosen (and a Save sending it as a value).
    document.addEventListener('change', event => {
        const select = event.target;
        const entry = select instanceof HTMLSelectElement ? select.selectedOptions[0] : null;
        if (!entry || !entry.dataset.goto) {
            return;
        }
        const target = entry.dataset.goto;
        select.selectedIndex = Math.max(0, Array.from(select.options).findIndex(option => option.defaultSelected));
        // nothing after this hears the change: the form is not edited by it, so it must not say it has unsaved changes
        event.stopImmediatePropagation();
        window.location.assign(target);
    });

    // A button that says data-copy-from="#id" copies the text of that box to the clipboard and says "Copied" beside it for a moment. Where the browser will not
    // give the clipboard to a script (a page that is not secure, a permission refused) the box is selected and the browser is asked to copy, and failing that
    // the text is left selected for the user to copy by hand.
    document.addEventListener('click', event => {
        const button = event.target.closest('button[data-copy-from]');
        const source = button ? document.querySelector(button.dataset.copyFrom) : null;
        if (!source) {
            return;
        }
        const cue = button.parentElement.querySelector('[data-copied]');
        const copied = () => {
            cue?.classList.remove('hidden');
            setTimeout(() => cue?.classList.add('hidden'), 2000);
        };
        const byHand = () => {
            source.focus();
            source.select();
            if (document.execCommand('copy')) {
                copied();
            }
        };
        if (navigator.clipboard?.writeText) {
            navigator.clipboard.writeText(source.value).then(copied, byHand);
        } else {
            byHand();
        }
    });

    // The facility form's "Fill the structure's bonuses" button copies the chosen structure's materials, time and job fee bonuses (which the page
    // puts on the option as data-material, data-time and data-cost) into the form's fields. "Not said" has none, so it does nothing. The fields
    // are told they changed, so that the form says it has unsaved changes.
    document.addEventListener('click', event => {
        const button = event.target.closest('button[data-fill-structure]');
        if (!button) {
            return;
        }
        const form = button.closest('form');
        const option = form.querySelector('select[name=structureType]')?.selectedOptions[0];
        if (!option || option.dataset.time === undefined) {
            return;
        }
        [['structureMaterialPercent', 'material'], ['structureTimePercent', 'time'], ['structureCostPercent', 'cost']].forEach(([field, key]) => {
            const input = form.elements[field];
            input.value = option.dataset[key];
            input.dispatchEvent(new Event('input', {bubbles: true}));
        });
    });

    // A <details data-section="name"> is a section of a page that the user can open and close. Whether it is open is kept in this browser (local
    // storage, one key per section, the same for every project), because every save reloads the page. A section that says data-keep-open has
    // something the user must see (a warning that blocks the quote, the last error), so it opens whatever was kept and what the user does
    // with it is not kept. Private windows and blocked storage throw, so the page then works without remembering.
    const sectionKey = name => `eveassist.section.${name}`;

    function keptState(name) {
        try {
            return localStorage.getItem(sectionKey(name));
        } catch (ignored) {
            return null;
        }
    }

    // The page sends a section closed unless it has something in it (the warnings), so what the user opened is kept as "open", and what the
    // user closed of a section that came open (the warnings) is kept as "closed". With nothing kept the page's own state stands.
    function putSectionsBack() {
        document.querySelectorAll('details[data-section]:not([data-keep-open])').forEach(section => {
            const kept = keptState(section.dataset.section);
            if (kept === 'open' || kept === 'closed') {
                section.open = kept === 'open';
            }
        });
    }

    putSectionsBack();
    // An edit of a plan draws sections again, closed as the server sends them, and htmx can set an element's attributes once more after it has
    // put it in place, so what the user opened is put back after both.
    ['htmx:after:swap', 'htmx:after:settle', 'htmx:finally:request'].forEach(name => document.addEventListener(name, putSectionsBack));

    // toggle does not bubble, so it is heard while it travels down
    document.addEventListener('toggle', event => {
        const section = event.target;
        if (section instanceof HTMLDetailsElement && section.dataset.section && !section.hasAttribute('data-keep-open')) {
            try {
                localStorage.setItem(sectionKey(section.dataset.section), section.open ? 'open' : 'closed');
            } catch (ignored) {
                // nothing to do: the section is just not remembered
            }
        }
    }, true);

    // A dropdown that says data-set-all gives every data-skill dropdown of its data-skill-group the chosen level, and goes back to the dots. The
    // change it made is heard by the form like any other, so the form says it has unsaved changes.
    document.addEventListener('change', event => {
        const all = event.target;
        if (all instanceof HTMLSelectElement && all.hasAttribute('data-set-all') && all.value !== '') {
            all.closest('[data-skill-group]').querySelectorAll('select[data-skill]').forEach(skill => skill.value = all.value);
            all.value = '';
        }
    });

    // The height of a project's header, which stays in view, so that what else stays in view (the quote card) and what an address leads to are
    // kept below it.
    const header = document.getElementById('project-header');
    if (header) {
        const tellHeight = () => document.documentElement.style.setProperty('--header-height',
            getComputedStyle(header).position === 'sticky' ? `${header.offsetHeight}px` : '0px');
        new ResizeObserver(tellHeight).observe(header);
        tellHeight();
    }

    // Inside data-keep-place a save comes back to the same place on the page. As a form is sent, the first section still in view is noted with
    // where it is in the window; when the same address is drawn again, the page is scrolled so that the section is there again, even if
    // something above it appeared or went. It is kept for this tab only, and briefly. The browser first goes to the address's own #part, so
    // this waits until the page has loaded.
    const PLACE_KEY = 'eveassist.place';

    window.addEventListener('submit', event => {
        const area = event.target.closest('[data-keep-place]');
        // a refused or repeated submit goes nowhere (heard last, on the window, so that this is already decided)
        if (!area || event.defaultPrevented) {
            return;
        }
        const covered = header && getComputedStyle(header).position === 'sticky' ? header.getBoundingClientRect().bottom : 0;
        const section = [...area.querySelectorAll('details[id]:not(.dropdown)')].find(one => one.getBoundingClientRect().bottom > covered);
        try {
            sessionStorage.setItem(PLACE_KEY, JSON.stringify({
                path: location.pathname, y: window.scrollY, id: section?.id, top: section?.getBoundingClientRect().top, at: Date.now()}));
        } catch (ignored) {
            // the page then opens where the address says
        }
    });

    let place = null;
    try {
        place = JSON.parse(sessionStorage.getItem(PLACE_KEY));
        sessionStorage.removeItem(PLACE_KEY);
    } catch (ignored) {
        // nothing was kept
    }
    if (place && place.path === location.pathname && Date.now() - place.at < 60000) {
        window.addEventListener('load', () => requestAnimationFrame(() => {
            // where the header does not stay in view, an error is only seen at the top, so the page is left where the browser put it
            if (document.getElementById('error') && header && getComputedStyle(header).position !== 'sticky') {
                return;
            }
            const section = place.id && document.getElementById(place.id);
            if (section) {
                window.scrollBy(0, section.getBoundingClientRect().top - place.top);
            } else {
                window.scrollTo(0, place.y);
            }
        }));
    }

    // A long list (data-list-page) keeps its filter, its count and actions and its column headings in view (app.css). They stack, so each needs
    // the height of those above it; and the headings only stay over a table that fits its place without scrolling sideways. Heights and widths
    // change as lists are drawn again, rows open and the window changes.
    const listPage = document.querySelector('[data-list-page]');

    function measureList() {
        if (!listPage) {
            return;
        }
        const style = document.documentElement.style;
        style.setProperty('--list-filter-height', `${listPage.querySelector('.list-filter')?.offsetHeight ?? 0}px`);
        style.setProperty('--list-head-height', `${listPage.querySelector('.list-head')?.offsetHeight ?? 0}px`);
        listPage.querySelectorAll('[data-wide-table]').forEach(box =>
            box.toggleAttribute('data-fits', box.querySelector('table').scrollWidth <= box.clientWidth + 1));
    }

    if (listPage) {
        const watch = new ResizeObserver(measureList);
        const watchAll = () => {
            listPage.querySelectorAll('.list-filter, .list-head, [data-wide-table], [data-wide-table] > table').forEach(part => watch.observe(part));
            measureList();
        };
        watchAll();
        ['htmx:after:swap', 'htmx:after:settle', 'htmx:finally:request'].forEach(name => document.addEventListener(name, watchAll));
    }

    // A change of the filter, the tech level or the sort draws the list again: someone far down it is brought to the new list's start, since
    // where they were means nothing in it. More of the same list (data-keeps-place), a refresh the list asks for itself, and an opened row
    // leave the page where it is. Which of these it is, is noted as the request is sent: by the time the answer is in, a link that was inside
    // the list is gone, and htmx then names the list as the request's source.
    const LISTS = ['browse', 'blueprint-results'];

    document.addEventListener('htmx:before:request', event => {
        const ctx = event.detail.ctx;
        ctx.keepsPlace = LISTS.includes(ctx.sourceElement.id) || !!ctx.sourceElement.closest('[data-keeps-place]');
    });

    document.addEventListener('htmx:after:swap', event => {
        const {target, keepsPlace} = event.detail.ctx;
        if (!listPage || keepsPlace || !target || !LISTS.includes(target.id)) {
            return;
        }
        measureList();
        const filter = listPage.querySelector('.list-filter');
        const covered = filter && getComputedStyle(filter).position === 'sticky' ? filter.offsetHeight : 0;
        const start = target.getBoundingClientRect().top + window.scrollY - covered;
        if (window.scrollY > start) {
            window.scrollTo(0, start);
        }
    });

    // Inventory's choices for a column float over the rows when they are opened while the filter is held at the top of the window (app.css). It is
    // decided as they open, so that scrolling afterwards moves nothing about.
    document.addEventListener('htmx:after:swap', event => {
        const {target} = event.detail.ctx;
        if (target?.id !== 'filter-pop') {
            return;
        }
        const filter = target.closest('.list-filter');
        target.toggleAttribute('data-floating', !!filter && getComputedStyle(filter).position === 'sticky'
            && window.scrollY > 0 && filter.getBoundingClientRect().top < 1);
    });

    // A form that says data-autosave is saved when a field of it is left changed, with no button.
    document.addEventListener('change', event => {
        const form = event.target.form;
        if (form && form.hasAttribute('data-autosave')) {
            form.requestSubmit();
        }
    });

    // A form that says data-reloads-page (itself, or an element around it) saves at once and draws the page again, which would throw away what
    // is typed in another form and not saved. Then it asks first, with the attribute's text, and a refusal puts its own fields back. This is
    // registered before the busy marking below, so that a refused submit does not leave the page waiting.
    document.addEventListener('submit', event => {
        const form = event.target;
        const asking = form.closest('[data-reloads-page]');
        if (!asking) {
            return;
        }
        const unsaved = [...document.querySelectorAll('form[data-dirty]')].some(other => other !== form);
        if (unsaved && !window.confirm(asking.dataset.reloadsPage)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            form.reset();
        }
    }, true);

    // A form that says data-busy waits visibly once it is submitted: the cursor says so, the buttons of its data-busy-group (or its own) are
    // disabled, the chosen one shows a spinner, and a further submit of any such form is dropped, so that one choice is not sent twice. An htmx
    // form is released when its request is over. A form that leaves the page stays as it is until the next page is there; the browser shows a
    // page again as it was left when the user goes back to it, so everything is released then.
    let waiting = null;

    function release() {
        if (!waiting) {
            return;
        }
        document.documentElement.classList.remove('busy');
        waiting.buttons.forEach(button => button.disabled = false);
        waiting.spinner?.remove();
        waiting = null;
    }

    document.addEventListener('submit', event => {
        const form = event.target;
        if (!form.hasAttribute('data-busy')) {
            return;
        }
        if (waiting) {
            event.preventDefault();
            event.stopImmediatePropagation();
            return;
        }
        const group = form.closest('[data-busy-group]') || form;
        const now = {form, buttons: [...group.querySelectorAll('button:not([disabled])')], spinner: null};
        waiting = now;
        document.documentElement.classList.add('busy');
        const chosen = event.submitter;
        // after this event, so that the submit that is under way is not affected by its button being disabled
        setTimeout(() => {
            if (waiting !== now) {
                return;
            }
            now.buttons.forEach(button => button.disabled = true);
            if (chosen) {
                now.spinner = document.createElement('span');
                now.spinner.className = 'loading loading-spinner loading-xs';
                now.spinner.setAttribute('aria-hidden', 'true');
                chosen.appendChild(now.spinner);
            }
        });
    }, true);

    // Heard on the document: the answer can have removed the form before the request is over, and htmx then reports it there.
    document.addEventListener('htmx:finally:request', event => {
        if (waiting && event.detail.ctx.sourceElement === waiting.form) {
            release();
        }
    });

    window.addEventListener('pageshow', release);

    // A form that says data-track-unsaved marks itself (data-dirty) as soon as one of its fields is edited, and the page's styles show
    // it: its Save button turns to a warning and its "unsaved changes" note appears. A field outside the form's own tags that names it
    // with the form attribute counts too, because event.target.form is the form it belongs to. Nothing is saved for the user.
    function markUnsaved(event) {
        const form = event.target.form;
        if (form && form.hasAttribute('data-track-unsaved')) {
            form.setAttribute('data-dirty', '');
            // a section that is closed afterwards still shows it, in its header: one around the form, or one inside it around the field
            form.closest('details[data-section]')?.setAttribute('data-dirty', '');
            event.target.closest('details[data-section]')?.setAttribute('data-dirty', '');
        }
    }

    ['input', 'change'].forEach(name => document.addEventListener(name, markUnsaved));

    // A plan without an account (/try) lives in this browser only. Its state is a short text in the page's #try-state box,
    // which every answer from the server swaps. It is kept in local storage after each swap. When the page opens with a
    // plan kept, the box gets it before htmx starts (this script runs after the page is read and before htmx, which the
    // layout loads after it), and the hidden #try-restore element sends it to the server once the page has loaded. Private
    // windows and blocked storage throw, so the page works without it, forgetting.
    const TRY_PLAN_KEY = 'eveassist.try.plan';

    function keepTryPlan() {
        const box = document.getElementById('try-state');
        if (box) {
            try {
                localStorage.setItem(TRY_PLAN_KEY, box.value);
            } catch (ignored) {
                // nothing to do: the plan is just not kept
            }
        }
    }

    (function restoreTryPlan() {
        const box = document.getElementById('try-state');
        const restore = document.getElementById('try-restore');
        if (!box || !restore) {
            return;
        }
        let saved = null;
        try {
            saved = localStorage.getItem(TRY_PLAN_KEY);
        } catch (ignored) {
            // nothing kept
        }
        if (saved && saved !== box.value) {
            box.value = saved;
        } else {
            restore.remove();
        }
    })();

    ['htmx:after:swap', 'htmx:after:settle'].forEach(name => document.addEventListener(name, keepTryPlan));

    function showError(key) {
        const toast = document.getElementById('error-toast');
        toast.querySelector('span').textContent = toast.dataset[key];
        toast.hidden = false;
        clearTimeout(showError.timer);
        showError.timer = setTimeout(() => { toast.hidden = true; }, 8000);
    }

    document.addEventListener('htmx:response:error', event => {
        const status = event.detail.ctx.response.status;
        showError(status === 409 ? 'conflict' : status === 422 ? 'invalid' : 'failed');
    });

    document.addEventListener('htmx:error', () => showError('failed'));

    // Units of a plan product. A number the plan cannot hold (not whole, below 1 or above the largest) is refused here with
    // the reason, and the number the plan holds is put back, so the box never shows what the plan does not have. The event
    // is stopped before htmx sees it, which also keeps the browser's own validation bubble from opening.
    document.addEventListener('change', event => {
        const box = event.target;
        if (!(box instanceof HTMLInputElement) || !box.hasAttribute('data-plan-quantity')) {
            return;
        }
        const text = box.value.trim();
        const value = Number(text);
        const low = text === '' || !Number.isInteger(value) || value < Number(box.min);
        const high = !low && value > Number(box.max);
        if (low || high) {
            event.stopImmediatePropagation();
            box.value = box.dataset.saved;
            showError(low ? 'unitsLow' : 'unitsHigh');
            return;
        }
        box.dataset.saved = String(value);
    }, true);

    // Up and down arrows move through a search's results, and Enter on a result picks it.
    document.addEventListener('keydown', event => {
        const search = document.getElementById('product-search');
        if (!search || !['ArrowDown', 'ArrowUp', 'Escape'].includes(event.key)) {
            return;
        }
        const results = [...document.querySelectorAll('#search-list button')];
        const at = results.indexOf(document.activeElement);
        if (document.activeElement !== search && at < 0) {
            return;
        }
        event.preventDefault();
        if (event.key === 'Escape') {
            search.focus();
        } else if (event.key === 'ArrowDown') {
            results[Math.min(at + 1, results.length - 1)]?.focus();
        } else if (at === 0) {
            search.focus();
        } else if (at > 0) {
            results[at - 1].focus();
        }
    });

    // Sorting stays in the browser, for one page view. It is put back after an update redraws or changes rows.
    const sorts = new Map();

    function sortValue(row, column) {
        const value = row.cells[column].dataset.sort;
        return value === undefined || value === '' ? null : Number(value);
    }

    function applySort(body) {
        const sort = sorts.get(body.id);
        const table = body.closest('table');
        table.querySelectorAll('th').forEach((heading, index) => {
            const sorted = sort?.column === index;
            if (heading.querySelector('[data-sort-column]')) {
                heading.setAttribute('aria-sort', sorted ? (sort.direction > 0 ? 'ascending' : 'descending') : 'none');
                heading.querySelector('.sort-mark').textContent = sorted ? (sort.direction > 0 ? ' ▲' : ' ▼') : '';
            }
        });
        if (!sort) {
            return;
        }
        const rows = [...body.rows].sort((one, other) => {
            const a = sortValue(one, sort.column);
            const b = sortValue(other, sort.column);
            if (a === null || b === null) {
                return a === b ? 0 : a === null ? 1 : -1; // rows with no value go last either way
            }
            return (a - b) * sort.direction;
        });
        const inOrder = rows.every((row, index) => body.rows[index] === row);
        if (!inOrder) {
            rows.forEach(row => body.append(row));
        }
    }

    document.addEventListener('click', event => {
        const button = event.target.closest('[data-sort-column]');
        if (!button) {
            return;
        }
        const heading = button.closest('th');
        const body = heading.closest('table').tBodies[0];
        const current = sorts.get(body.id);
        const direction = current?.column === heading.cellIndex ? -current.direction : -1;
        sorts.set(body.id, {column: heading.cellIndex, direction});
        applySort(body);
    });

    // The inventory list: a row opens and closes the row below it, where htmx loads the places the first time.
    document.addEventListener('click', event => {
        const row = event.target.closest('tr[data-expand]');
        if (!row || event.target.closest('a, button, input, label, select')) {
            return;
        }
        const detail = document.getElementById(row.dataset.expand);
        if (!detail) {
            return;
        }
        detail.hidden = !detail.hidden;
        const mark = row.querySelector('.chevron');
        if (mark) {
            mark.toggleAttribute('data-open', !detail.hidden);
        }
    });

    // The x on a message (the undo bar) takes the message away. Nothing is sent to the server.
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-dismiss="alert"]');
        if (button) {
            button.closest('.alert')?.remove();
        }
    });

    // A drop-down menu made of <details> closes when something in it is chosen or when the page is clicked elsewhere.
    document.addEventListener('click', event => {
        document.querySelectorAll('details[data-autoclose][open]').forEach(menu => {
            if (!menu.contains(event.target) || event.target.closest('li')) {
                menu.open = false;
            }
        });
    });

    // A drop-down menu with a box in it puts the cursor in the box when it opens (details has no autofocus of its own).
    // The toggle event does not bubble, so it is caught on the way down.
    document.addEventListener('toggle', event => {
        const menu = event.target;
        if (menu instanceof HTMLDetailsElement && menu.open && menu.hasAttribute('data-focus-on-open')) {
            // The first box that can be typed in: a form made by Thymeleaf starts with a hidden _csrf input, which cannot
            // take focus, so hidden inputs are skipped.
            menu.querySelector('input:not([type=hidden]):not([type=checkbox]):not([type=radio]), textarea, select')?.focus();
        }
    }, true);

    // Copy as text: one line for each item type the filter lets through, the name and the total with a tab between.
    // The server gives the whole list, because the page shows only the first of it.
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-copy-lines]');
        if (!button) {
            return;
        }
        const done = () => {
            const label = button.textContent;
            button.textContent = button.dataset.copied;
            setTimeout(() => { button.textContent = label; }, 1500);
        };
        fetch(button.dataset.url, {credentials: 'same-origin'})
            .then(response => response.ok ? response.text() : Promise.reject(new Error('copy')))
            .then(lines => {
                if (navigator.clipboard?.writeText) {
                    return navigator.clipboard.writeText(lines).then(done, () => selectText(lines));
                }
                selectText(lines);
            })
            .catch(() => showError('failed'));
    });

    // Some browsers refuse the clipboard. The text is put in a box and selected, so Ctrl+C still works.
    function selectText(text) {
        const box = document.createElement('textarea');
        box.value = text;
        box.setAttribute('aria-label', 'Copy this text');
        box.className = 'textarea textarea-bordered w-full h-32 mt-2';
        box.dataset.copyBox = 'true';
        document.querySelectorAll('[data-copy-box]').forEach(old => old.remove());
        document.getElementById('browse').prepend(box);
        box.select();
    }

    document.addEventListener('htmx:after:settle', () => {
        sorts.forEach((sort, id) => {
            const body = document.getElementById(id);
            if (body) {
                applySort(body);
            }
        });
    });

    // Blueprint selection survives list swaps and filters; only ids are sent back, and the server rechecks ownership.
    const pickedBlueprints = new Map();

    function updateBlueprintSelection() {
        const checks = [...document.querySelectorAll('[data-blueprint-pick]')];
        const shown = new Set(checks.map(check => check.value));
        checks.forEach(check => { check.checked = pickedBlueprints.has(check.value); });
        const selectAll = document.getElementById('blueprint-pick-all');
        if (selectAll) {
            const selectedShown = checks.filter(check => pickedBlueprints.has(check.value)).length;
            selectAll.checked = checks.length > 0 && selectedShown === checks.length;
            selectAll.indeterminate = selectedShown > 0 && selectedShown < checks.length;
        }
        const button = document.getElementById('blueprint-actions');
        const count = document.getElementById('blueprint-picked-count');
        if (!button || !count) {
            return;
        }
        button.disabled = pickedBlueprints.size === 0;
        // Inventory takes one search text, so "show in Inventory" is for blueprints of a single type
        const inventory = document.querySelector('[data-blueprint-action="inventory"]');
        if (inventory) {
            const types = new Set([...pickedBlueprints.values()].map(item => item.type));
            inventory.disabled = types.size > 1;
            inventory.title = types.size > 1 ? inventory.dataset.manyTypes : '';
        }
        button.setAttribute('aria-expanded', String(document.getElementById('blueprint-actions-menu')?.hidden === false));
        const label = button.querySelector('span');
        if (label) {
            label.textContent = pickedBlueprints.size === 0 ? button.dataset.disabledLabel
                : button.dataset.label.replace('{0}', pickedBlueprints.size);
        }
        if (pickedBlueprints.size === 0) {
            count.textContent = count.dataset.none;
        } else {
            const hidden = [...pickedBlueprints.keys()].filter(id => !shown.has(id)).length;
            count.textContent = hidden
                ? count.dataset.hidden.replace('{0}', pickedBlueprints.size).replace('{1}', hidden)
                : count.dataset.picked.replace('{0}', pickedBlueprints.size);
        }
    }

    function closeBlueprintDrawer() {
        document.body.classList.remove('blueprint-drawer-open');
        const drawer = document.getElementById('blueprint-drawer');
        const listUrl = drawer?.querySelector('[data-return-url]')?.dataset.returnUrl;
        if (drawer) {
            drawer.replaceChildren();
        }
        // The address goes back to the list without history.back(): that could leave the page (a shared link), and
        // htmx restores a history entry by asking the server for it.
        if (listUrl && /^\/blueprints\/\d+$/.test(window.location.pathname)) {
            history.replaceState(history.state, '', listUrl);
        }
    }

    document.addEventListener('change', event => {
        const check = event.target;
        if (check.matches('[data-blueprint-pick]')) {
            const row = check.closest('[data-blueprint-row]');
            if (check.checked) {
                pickedBlueprints.set(check.value, {type: row.dataset.type, name: row.dataset.name});
            } else {
                pickedBlueprints.delete(check.value);
            }
            updateBlueprintSelection();
        } else if (check.id === 'blueprint-pick-all') {
            document.querySelectorAll('[data-blueprint-pick]').forEach(box => {
                const row = box.closest('[data-blueprint-row]');
                if (check.checked) {
                    pickedBlueprints.set(box.value, {type: row.dataset.type, name: row.dataset.name});
                } else {
                    pickedBlueprints.delete(box.value);
                }
            });
            updateBlueprintSelection();
        }
    });

    document.addEventListener('click', event => {
        if (event.target.closest('[data-blueprint-link]') && event.target.closest('tr[data-expand]')) {
            event.stopPropagation();
        }
        const close = event.target.closest('[data-close-blueprint-drawer]');
        if (close) {
            closeBlueprintDrawer();
            return;
        }
        const actions = event.target.closest('#blueprint-actions');
        const menu = document.getElementById('blueprint-actions-menu');
        if (actions && menu) {
            menu.hidden = !menu.hidden;
            actions.setAttribute('aria-expanded', String(!menu.hidden));
            return;
        }
        if (menu && !event.target.closest('#blueprint-actions-menu')) {
            menu.hidden = true;
            document.getElementById('blueprint-actions')?.setAttribute('aria-expanded', 'false');
        }
        const action = event.target.closest('[data-blueprint-action]');
        if (!action || action.disabled) {
            return;
        }
        if (action.dataset.blueprintAction === 'csv') {
            // a POST, so that hundreds of picked ids never have to fit in an address
            const form = document.createElement('form');
            form.method = 'post';
            form.action = '/blueprints/export.csv';
            const field = (name, value) => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = value;
                form.appendChild(input);
            };
            field('_csrf', document.querySelector('meta[name="_csrf"]').content);
            pickedBlueprints.forEach((value, id) => field('item', id));
            document.body.appendChild(form);
            form.submit();
            form.remove();
        } else if (action.dataset.blueprintAction === 'inventory') {
            const unique = new Map([...pickedBlueprints.values()].map(item => [item.type, item.name]));
            if (unique.size === 1) {
                window.open('/inventory?q=' + encodeURIComponent([...unique.values()][0]), '_blank', 'noopener');
            }
        } else if (action.dataset.blueprintAction === 'clear') {
            pickedBlueprints.clear();
            updateBlueprintSelection();
        }
        if (menu) {
            menu.hidden = true;
        }
    });

    // The status line is swapped every two seconds while a read runs, so whether its table is open is remembered here
    let blueprintStatusOpen = false;
    document.addEventListener('toggle', event => {
        if (event.target.id === 'blueprint-status-details') {
            blueprintStatusOpen = event.target.open;
        }
    }, true);

    // htmx 4 puts the request context in the event detail; its target is the element that was swapped into
    document.addEventListener('htmx:after:swap', event => {
        const details = document.getElementById('blueprint-status-details');
        if (details && details.open !== blueprintStatusOpen) {
            details.open = blueprintStatusOpen;
        }
        if (event.detail.ctx?.target?.id === 'blueprint-drawer') {
            document.body.classList.add('blueprint-drawer-open');
        }
        updateBlueprintSelection();
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && document.body.classList.contains('blueprint-drawer-open')) {
            closeBlueprintDrawer();
        }
    });

    document.body.classList.toggle('blueprint-drawer-open',
        document.getElementById('blueprints-page')?.dataset.drawerOpen === 'true');
    updateBlueprintSelection();
})();
