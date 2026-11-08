import {cpSync, mkdirSync} from 'node:fs';

mkdirSync('target/classes/static/js', {recursive: true});
cpSync('node_modules/htmx.org/dist/htmx.min.js', 'target/classes/static/js/htmx.min.js');
