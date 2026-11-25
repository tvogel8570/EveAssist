// The comparison page's layout B (documentation/mockups/project-compare.html): the user types what the game's Industry window shows beside each of the
// app's numbers, and every line is marked as matching or differing. Typed values are kept in this browser for the costing they were typed against
// (data-key on the page says which), and the layout that shows is kept too. Private windows and blocked storage throw, so it then works without keeping.
(function () {
    const root = document.getElementById('compare');
    if (!root) {
        return;
    }
    const valuesKey = `eveassist.compare.${root.dataset.project}.${root.dataset.key}`;
    const viewKey = 'eveassist.compare.view';

    const read = key => {
        try {
            return localStorage.getItem(key);
        } catch (ignored) {
            return null;
        }
    };
    const write = (key, value) => {
        try {
            localStorage.setItem(key, value);
        } catch (ignored) {
            // nothing to do: the value is just not kept
        }
    };

    let typed = {};
    try {
        typed = JSON.parse(read(valuesKey) || '{}') || {};
    } catch (ignored) {
        typed = {};
    }

    const fmt = n => Math.round(n).toLocaleString('en-US');

    // What the game shows, as a number with the room the game's own rounding leaves: a duration to the second, a percent to a tenth, a quantity
    // to the unit, and a shortened one (0.31M, 92.13K) to the two decimals it keeps.
    function parseGame(text, kind) {
        const s = (text || '').trim().replace(/,/g, '').replace(/ISK/i, '').replace('−', '-').trim();
        if (!s) {
            return null;
        }
        if (kind === 'dur') {
            const clock = s.match(/^(?:(\d+)D\s*)?(\d+):(\d+):(\d+)$/i);
            if (clock) {
                return {value: Number(clock[1] || 0) * 86400 + Number(clock[2]) * 3600 + Number(clock[3]) * 60 + Number(clock[4]), tolerance: 0.5};
            }
            const seconds = parseFloat(s);
            return Number.isNaN(seconds) ? null : {value: seconds, tolerance: 0.5};
        }
        if (kind === 'pct') {
            const percent = parseFloat(s);
            return Number.isNaN(percent) ? null : {value: percent, tolerance: 0.05};
        }
        const number = s.match(/^(-?[\d.]+)\s*([KkMm])?$/);
        if (!number || Number.isNaN(parseFloat(number[1]))) {
            return null;
        }
        const value = parseFloat(number[1]);
        if (number[2]) {
            const unit = number[2].toLowerCase() === 'm' ? 1e6 : 1e3;
            return {value: value * unit, tolerance: 0.005 * unit + 0.5};
        }
        return {value, tolerance: 0.5};
    }

    const signed = (n, kind) => {
        const text = kind === 'pct' ? Math.abs(n).toFixed(1) : fmt(Math.abs(n));
        return (n < 0 ? '-' : n > 0 ? '+' : '') + text;
    };

    function mark(row) {
        const input = row.querySelector('input[data-game]');
        const cell = row.querySelector('[data-diff]');
        const parsed = parseGame(input.value, row.dataset.kind);
        if (!input.value.trim()) {
            delete row.dataset.state;
            cell.textContent = '';
            return;
        }
        if (!parsed) {
            row.dataset.state = 'differ';
            cell.textContent = '?';
            return;
        }
        const app = Number(row.dataset.app);
        const matches = Math.abs(app - parsed.value) <= parsed.tolerance;
        row.dataset.state = matches ? 'match' : 'differ';
        cell.textContent = matches ? '✓' : signed(app - parsed.value, row.dataset.kind);
    }

    function summarize() {
        root.querySelectorAll('[data-summary]').forEach(summary => {
            const rows = Array.from(root.querySelectorAll(`tr[data-row][data-job="${summary.dataset.job}"]`));
            const filled = rows.filter(row => row.dataset.state).length;
            const hits = rows.filter(row => row.dataset.state === 'match').length;
            summary.textContent = `${filled} of ${rows.length} lines filled in · ${hits} match · ${filled - hits} differ`;
            summary.classList.toggle('text-success', filled > 0 && hits === filled);
            summary.classList.toggle('text-error', filled > hits);
        });
    }

    root.querySelectorAll('tr[data-row]').forEach(row => {
        const input = row.querySelector('input[data-game]');
        if (typed[row.dataset.key] !== undefined) {
            input.value = typed[row.dataset.key];
        }
        mark(row);
        input.addEventListener('input', () => {
            if (input.value.trim()) {
                typed[row.dataset.key] = input.value;
            } else {
                delete typed[row.dataset.key];
            }
            write(valuesKey, JSON.stringify(typed));
            mark(row);
            summarize();
        });
    });
    summarize();

    const clear = rows => {
        rows.forEach(row => {
            row.querySelector('input[data-game]').value = '';
            delete typed[row.dataset.key];
            mark(row);
        });
        write(valuesKey, JSON.stringify(typed));
        summarize();
    };
    root.querySelectorAll('[data-clear-job]').forEach(button => button.addEventListener('click',
            () => clear(Array.from(root.querySelectorAll(`tr[data-row][data-job="${button.dataset.clearJob}"]`)))));
    root.querySelectorAll('[data-clear-all]').forEach(button => button.addEventListener('click',
            () => clear(Array.from(root.querySelectorAll('tr[data-row]')))));

    const show = view => {
        root.dataset.view = view === 'b' ? 'b' : 'a';
        write(viewKey, root.dataset.view);
    };
    root.querySelectorAll('[data-view-switch]').forEach(button => button.addEventListener('click', () => show(button.dataset.viewSwitch)));
    if (read(viewKey) === 'b') {
        show('b');
    }
})();
