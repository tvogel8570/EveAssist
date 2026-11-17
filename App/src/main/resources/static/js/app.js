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

    // A plan without an account (/try) lives in this browser only. Its state is a short text in the page's #try-state box,
    // which every answer from the server swaps. It is kept in local storage after each swap. When the page opens with a
    // plan kept, the box gets it before htmx starts (this script runs after the page is read and before htmx processes
    // it), and the hidden #try-restore element sends it to the server once the page has loaded. Private windows and
    // blocked storage throw, so the page works without it, forgetting.
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
        const mark = row.querySelector('[data-expander]');
        if (mark) {
            mark.textContent = detail.hidden ? '▸' : '▾';
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
