# UI conventions

Behaviour that every page of EveAssist follows, so the same thing works the same way everywhere. Each rule says what it is,
why, and where it lives in the code.

## Text boxes

Status: **agreed** with the developer (2026-11-10).

- **Every text or search box says `autocomplete`.** Without it the browser offers earlier entries in a dropdown that opens
  over the page, which hid the text under the "Name of a new plan" box. Boxes whose earlier values are never worth recalling,
  which is all of them today (names of new things, searches), say `autocomplete="off"`. A box where recalling a value would
  help would say what it is instead (`name`, `email`).
- A test (`TemplateInputsTest`) fails when a text or search box in a template has no `autocomplete`, so a new box cannot
  slip through. Boxes of other types (hidden, checkbox, number, buttons) and boxes made by a fragment are not checked.

## Switches

Status: **agreed** with the developer (2026-11-10).

- **An on/off switch is a DaisyUI `toggle` with `toggle-primary`**, so it is grey when off and blue when on. The plan list's
  "Show archived plans" switch had no colour and stayed grey either way, which looked different from the switches on the
  Characters and Inventory pages. Use `toggle-sm` where the switch sits in a row of small controls.
- Where it lives: `characters/list.html`, `inventory/list.html`, `plans/list.html`. Nothing checks this yet, so a new switch
  has to follow it by hand.

## Search boxes that refresh a list

Status: **agreed** with the developer (2026-11-10), after typing in the Inventory search made the list refresh while still
typing and the focus jump.

- **A pause of 2 seconds.** A search box applies what is typed after 2 seconds without a key. It never refreshes the list
  under the hands while someone is still thinking or typing. **Enter, and leaving the field, apply at once** (Enter sends
  the `search` event, and leaving the field sends the form's own `change`).
- **The address follows, without crowding the history.** Typing in a search box that is part of the page's address
  *replaces* the current history entry (`hx-replace-url`), so the back button does not step through every search term.
  Choosing a value from a list (a filter) still *pushes* an entry. The address stays shareable and reloadable.
- **Never swap the box being typed in.** The answer replaces the list under the box (`hx-target` and `hx-select`), and the
  box itself is left alone. Replacing it breaks three ways in htmx 4: a focused box that is removed loses focus; it fires
  `change` as it is removed, which a form that listens for `change` turns into a second whole-form request; and, if it has
  an id, htmx copies the old element's attributes onto the new one for CSS transitions, which resets the typed text to the
  old `value` attribute. All three were seen in the Add filter list (2026-11-10).
- **An id on every search box**, so a label, a test or a script can find it.
- **Use the fragment, do not write the attributes by hand.** `templates/fragments/search-input.html` (`field(...)`) holds the
  pause, the trigger and the rules above in one place. Both Inventory search boxes use it. A new search box that refreshes a
  list uses it too, and a change of the agreed behaviour is a change to that one file.

Not covered by these rules: a typeahead that offers suggestions to pick **one** thing from, such as the product search in the
plan editor. It has no address and wants quicker suggestions, so it keeps its short pause (250 ms) until it is decided
whether it should follow the same rule.

### Later: a user preference

When user settings exist, offer a choice between **as you type** (the pause above) and **when I leave the field** (no pause:
only Enter or leaving the field applies). The fragment is the one place that changes. A shared place for user settings would
also serve the choice of a colour for each character (issue #17).

### How it was checked

`InventoryBrowserIT` types slowly in a real browser and checks that nothing applies within a second of the last key, that
the list and address then change, that focus and the caret stay, that the history does not grow, that Enter applies at once,
and that the Add filter list keeps focus, caret and text while its list is refreshed.
