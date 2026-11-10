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
})();
