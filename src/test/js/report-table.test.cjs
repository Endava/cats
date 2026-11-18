const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(path.resolve(__dirname, '../../main/resources/script.js'), 'utf8');

function harness(rows, idle = true) {
    let html = '', active = true, next = 0;
    const queue = new Map();
    const schedule = callback => { queue.set(++next, callback); return next; };
    const search = {value: ''};
    const status = {dataset: {}, textContent: ''};
    const problemSelect = {value: ''};
    const fuzzer = {options: [], appendChild(option) { this.options.push(option); }, addEventListener() {}};
    const view = {id: 'tests-view', classList: {contains: () => active, toggle: (_, value) => { active = value; }}};
    const tbody = {replaceChildren() { html = ''; }, appendChild(fragment) { html += fragment.html; }};
    const banner = {style: {}, getAttribute: () => 'Test1'};
    const row = {style: {}};
    const elements = {'cats-test-data': {textContent: JSON.stringify([rows, {p: ['1. Problem', 'problem-p']}])},
        fuzzerFilter: fuzzer, problemFilter: problemSelect, 'tests-view': view, 'tests-render-status': status,
        summaryTable: {setAttribute() {}}, 'problem-banner': banner, 'problem-row': row};
    const document = {
        body: {classList: {remove() {}, add() {}}},
        getElementById: id => elements[id] || null,
        querySelector: selector => ({'.search-input': search, '#summaryTable tbody': tbody})[selector] || null,
        querySelectorAll: selector => selector === '.report-view' ? [view] : [],
        addEventListener() {},
        createElement: tag => tag === 'template' ? {innerHTML: '', get content() { return {html: this.innerHTML}; }} : {}
    };
    const window = {addEventListener() {}, requestIdleCallback: idle ? schedule : undefined,
        cancelIdleCallback: id => queue.delete(id)};
    const context = vm.createContext({document, window, setTimeout: schedule, clearTimeout: id => queue.delete(id)});
    vm.runInContext(script, context);
    return {
        run: code => vm.runInContext(code, context),
        flush() { while (queue.size) { const [id, callback] = queue.entries().next().value; queue.delete(id); callback(); } },
        get html() { return html; },
        get rendered() { return [...html.matchAll(/<tr data-id=/g)].length; },
        set active(value) { active = value; },
        queue, search, status, problemSelect, fuzzer, window, banner
    };
}

function rows(count = 500) {
    return Array.from({length: count}, (_, i) => ['Test ' + i, i === count - 1 ? 'LastFuzzer' : 'OtherFuzzer',
        i === count - 1 ? 'put' : 'get', i === count - 1 ? '/last' : '/items',
        i === count - 1 ? 'Needle at the end' : 'Ordinary scenario', i === count - 1 ? 'warn' : 'error', 'Reason', false, 'p']);
}

test('renders a useful first batch and then every test', () => {
    const h = harness(rows(5500));
    h.run('populateTable()');
    assert.equal(h.rendered, 100);
    assert.equal(h.status.dataset.complete, 'false');
    h.flush();
    assert.equal(h.rendered, 5500);
    assert.equal(h.status.dataset.complete, 'true');
    assert.equal(h.fuzzer.options.length, 2);
});

test('searches the complete dataset before the last row is rendered', () => {
    const h = harness(rows());
    h.run('populateTable()');
    h.search.value = 'needle at the end';
    h.run('populateTable()');
    h.flush();
    assert.equal(h.rendered, 1);
    assert.match(h.html, /data-id="Test499"/);
});

test('combines result, fuzzer, problem and operation filters over all tests', () => {
    const h = harness(rows());
    h.problemSelect.value = 'p';
    h.run('selectedFilter="warn"; selectedFuzzer="LastFuzzer"; selectedOperation="PUT /last"; populateTable()');
    h.flush();
    assert.equal(h.rendered, 1);
    assert.match(h.html, /data-id="Test499"/);
    h.run('selectedFilter="success"; populateTable()');
    assert.equal(h.rendered, 0);
    h.run('selectedFilter="warn"; populateTable()');
    h.problemSelect.value = 'absent';
    h.run('populateTable()');
    assert.equal(h.rendered, 0);
});

test('old scheduled chunks cannot append rows after a filter changes', () => {
    const h = harness(rows());
    h.run('populateTable()');
    const old = h.queue.values().next().value;
    h.search.value = 'needle';
    h.run('populateTable()');
    old();
    h.flush();
    assert.equal(h.rendered, 1);
    assert.doesNotMatch(h.html, /Ordinary scenario/);
});

test('defers rows for a problem deep link until All tests opens', () => {
    const h = harness(rows());
    h.active = false;
    h.run('populateTable()');
    assert.equal(h.rendered, 0);
    assert.equal(h.queue.size, 0);
    h.run('showReportView("tests-view")');
    h.flush();
    assert.equal(h.rendered, 500);
});

test('also completes in browsers without requestIdleCallback', () => {
    const h = harness(rows(), false);
    h.run('populateTable()');
    h.flush();
    assert.equal(h.rendered, 500);
});

test('renders untrusted report text as text and preserves path highlighting', () => {
    const data = rows(1);
    data[0][3] = '/items/{id}';
    data[0][4] = '</td><script>alert("test")</script>';
    data[0][6] = 'Reason " onmouseover="alert(1)';
    const h = harness(data);
    h.run('populateTable()');
    assert.doesNotMatch(h.html, /<script>/);
    assert.match(h.html, /&lt;script&gt;/);
    assert.match(h.html, /<span class="param-tok">\{id}<\/span>/);
    assert.match(h.html, /title="Reason &quot; onmouseover&#x3D;&quot;alert\(1\)"/);
    assert.doesNotMatch(h.html, /" onmouseover="/);
});

test('stacks the reason and linked problem inside one details cell', () => {
    const data = rows(1);
    data[0][7] = true;
    const h = harness(data);
    h.run('populateTable()');

    assert.equal([...h.html.matchAll(/<td\b/g)].length, 5);
    assert.match(h.html, /<td class="result-details-cell"><div class="result-reason" title="Reason"><span class="result-warn"> ⚠ <\/span>Reason<\/div><a href="#problem-p" class="problem-jump result-problem" title="1\. Problem">1\. Problem<\/a><\/td>/);
    assert.doesNotMatch(h.html, /class="problem-cell"/);
});

test('success rows have one reason line without an empty problem line', () => {
    const data = rows(1);
    data[0][5] = 'success';
    data[0][6] = 'All Good!';
    data[0][8] = '';
    const h = harness(data);
    h.run('populateTable()');

    assert.equal([...h.html.matchAll(/<td\b/g)].length, 5);
    assert.match(h.html, /<td class="result-details-cell"><div class="result-reason" title="All Good!">All Good!<\/div><\/td>/);
    assert.doesNotMatch(h.html, /result-problem|problem-jump/);
});

test('resolves the same test banner from shared metadata and per-test fields', () => {
    const h = harness(rows(1));
    h.window.CATS_PROBLEMS_PAGE = true;
    h.window.CATS_PROBLEMS = {Test1: {problem: 'p', fields: ['body.name']}};
    h.window.CATS_PROBLEMS_INDEX = {p: {count: 2, anchor: 'problem-p', title: 'A problem', kind: 'ACCEPTED_REQUEST', operations: 2}};
    h.run('renderProblemBanner()');
    assert.equal(h.banner.innerHTML, '<span><strong>Same problem as 1 other test:</strong> A problem on 2 operations; this test fuzzed body.name &rarr; <a href="index.html#problem-p" class="anchor-tag">view problem</a></span>');
    assert.equal(h.banner.style.display, '');
});
