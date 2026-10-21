let selectedFilter = "All";
let selectedFuzzer = "All";
let set = new Set();

const entityMap = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
    '/': '&#x2F;',
    '`': '&#x60;',
    '=': '&#x3D;'
};

function escapeHtml(string) {
    return String(string).replace(/[&<>"'`=\/]/g, function (s) {
        return entityMap[s];
    });
}

function populateTable() {
    const filter = (selectedFilter || "All").toUpperCase();
    const fuzzer = (selectedFuzzer || "All").toUpperCase();
    const searchInput = document.querySelector('.search-input');
    const searchText = searchInput ? searchInput.value.trim().toUpperCase() : "";

    document.querySelectorAll('#summaryTable tr:not(.header)').forEach((tr) => {
        const resultEl = tr.querySelector(".test-result");
        const result = resultEl ? resultEl.textContent.toUpperCase() : "";
        const fuzzerEl = tr.querySelector("td:nth-child(2)");
        const fuzzerName = fuzzerEl ? fuzzerEl.textContent.toUpperCase() : "";
        const rowText = tr.textContent.toUpperCase();

        const matchFilter = filter === "ALL" || result.includes(filter);
        const matchFuzzer = fuzzer === "ALL" || fuzzerName.includes(fuzzer);
        const matchSearch = searchText === "" || rowText.includes(searchText);

        if (matchFilter && matchFuzzer && matchSearch) {
            tr.style.display = '';
        } else {
            tr.style.display = 'none';
        }
    });

    const clearButton = document.querySelector('.search-clear-btn');
    if (clearButton) {
        clearButton.style.display = searchText.length > 0 ? 'inline-block' : 'none';
    }
}

function handleFilter(filter) {
    selectedFilter = filter || "All";
    populateTable();
}

function handleSearch() {
    populateTable();
}

function clearSearch() {
    const searchInput = document.querySelector('.search-input');
    if (searchInput) {
        searchInput.value = '';
    }
    populateTable();
}

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll("#summary span.menu").forEach(function (menuSpan) {
        menuSpan.addEventListener("click", function (e) {
            e.preventDefault();
            document.querySelectorAll("#summary span.menu.active").forEach(el => el.classList.remove("active"));
            selectedFilter = menuSpan.getAttribute('data-filtered') || "All";
            menuSpan.classList.add("active");
            selectedFuzzer = "All";

            handleFilter(selectedFilter);
            set.clear();
            set.add("All");
        });
    });

    const searchInput = document.querySelector('.search-input');
    if (searchInput) {
        searchInput.addEventListener('input', handleSearch);
        handleSearch();
    }
});

//theme
const themeToggleBtn = document.getElementById('theme-toggle');
const body = document.body;
const logoWhite = document.querySelector('.logo-white');
const logoDark = document.querySelector('.logo-dark');
const themeIconLight = document.querySelector('.theme-icon-light');
const themeIconDark = document.querySelector('.theme-icon-dark');
const timeWhite = document.querySelector('.time-white');
const timeDark = document.querySelector('.time-dark');
const methodsWhite = document.querySelector('.methods-white');
const methodsDark = document.querySelector('.methods-dark');
const fuzzersWhite = document.querySelector('.fuzzers-white');
const fuzzersDark = document.querySelector('.fuzzers-dark');
const linkWhite = document.querySelector('.link-white');
const linkDark = document.querySelector('.link-dark');

function updateChartThemes(isDark) {
    const tickColor = isDark ? '#D1D5DB' : '#374151';
    const gridColor = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.07)';
    if (typeof myChart !== "undefined" && myChart.data && myChart.data.datasets[0]) {
        myChart.data.datasets[0].borderColor = isDark ? '#1A1A1A' : '#FFFFFF';
        myChart.update();
    }
    [typeof responseCodeChart !== "undefined" ? responseCodeChart : null,
     typeof topFailingPathsChart !== "undefined" ? topFailingPathsChart : null].forEach(chart => {
        if (chart && chart.options && chart.options.scales) {
            Object.values(chart.options.scales).forEach(axis => {
                if (axis.ticks) axis.ticks.color = tickColor;
                if (axis.title) axis.title.color = tickColor;
                if (axis.grid) axis.grid.color = gridColor;
            });
            chart.update();
        }
    });
}

function enableDarkMode() {
    body.classList.add('dark-mode');
    updateChartThemes(true);

    hideElement(logoWhite);
    hideElement(themeIconDark);
    hideElement(timeWhite);
    hideElement(methodsWhite);
    hideElement(fuzzersWhite);
    hideElement(linkWhite);

    displayElement(logoDark);
    displayElement(themeIconLight);
    displayElement(timeDark);
    displayElement(methodsDark);
    displayElement(fuzzersDark);
    displayElement(linkDark);
}

function enableLightMode() {
    body.classList.remove('dark-mode');
    updateChartThemes(false);

    displayElement(logoWhite);
    displayElement(themeIconDark);
    displayElement(timeWhite);
    displayElement(methodsWhite);
    displayElement(fuzzersWhite);
    displayElement(linkWhite);

    hideElement(logoDark);
    hideElement(themeIconLight);
    hideElement(timeDark);
    hideElement(methodsDark);
    hideElement(fuzzersDark);
    hideElement(linkDark);
}

function displayElement(element) {
    if (element) {
        element.style.display = 'block';
    }
}

function hideElement(element) {
    if (element) {
        element.style.display = 'none';
    }
}

function setSavedTheme(theme) {
    try {
        localStorage.setItem('cats-report-theme', theme);
    } catch (_) {
        // Ignore storage errors in restricted contexts
    }
}

function getSavedTheme() {
    try {
        return localStorage.getItem('cats-report-theme');
    } catch (_) {
        return null;
    }
}

function toggleTheme() {
    if (body.classList.contains('dark-mode')) {
        enableLightMode();
        setSavedTheme('light');
    } else {
        enableDarkMode();
        setSavedTheme('dark');
    }
}

const savedTheme = getSavedTheme();
const prefersDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
if (savedTheme === 'dark' || (savedTheme === null && prefersDarkMode)) {
    enableDarkMode();
} else {
    enableLightMode();
}

window.addEventListener('storage', function (event) {
    if (event.key === 'cats-report-theme') {
        if (event.newValue === 'dark') {
            enableDarkMode();
        } else if (event.newValue === 'light') {
            enableLightMode();
        }
    }
});

// Show the button after the initial rendering
if (themeToggleBtn) {
    themeToggleBtn.style.display = 'block';
    themeToggleBtn.addEventListener('click', toggleTheme);
}


function showReportTab(tab, panelId) {
    const section = tab.closest('.tabbed-section');
    section.querySelectorAll('.code-area').forEach(panel => panel.classList.remove('active'));
    section.querySelectorAll('.tab').forEach(candidate => candidate.classList.remove('active'));

    const selectedPanel = document.getElementById(panelId);
    if (selectedPanel) {
        selectedPanel.classList.add('active');
        tab.classList.add('active');
    }
}

function copyActiveTab(button) {
    const activePanel = button.closest('.tabbed-section').querySelector('.code-area.active');
    copyElement(activePanel, button);
}

function copyCode(codeAreaId, copyButton) {
    const codeArea = document.getElementById(codeAreaId);
    copyElement(codeArea, copyButton);
}

function copyElement(element, copyButton) {
    const codeText = element.innerText;

    const tempTextarea = document.createElement('textarea');
    tempTextarea.value = codeText;
    document.body.appendChild(tempTextarea);
    tempTextarea.select();
    document.execCommand('copy');
    document.body.removeChild(tempTextarea);

    if (!copyButton.dataset.origText) {
        copyButton.dataset.origText = copyButton.textContent;
    }
    copyButton.textContent = 'Copied';
    copyButton.classList.add("copied");
    setTimeout(() => {
        copyButton.textContent = copyButton.dataset.origText;
        copyButton.classList.remove("copied");
    }, 2000);
}

let activeClusterFilter = 'all';

function toggleCluster(element) {
    element.classList.toggle('collapsed');
    const content = element.nextElementSibling;
    if (content) {
        content.classList.toggle('collapsed');
    }
}

function toggleErrorCluster(element) {
    element.classList.toggle('collapsed');
    const content = element.nextElementSibling;
    if (content) {
        content.classList.toggle('collapsed');
    }
}

function expandAllClusters() {
    document.querySelectorAll('.error-cluster-content.collapsed, .cluster-content.collapsed').forEach(content => {
        content.classList.remove('collapsed');
        if (content.previousElementSibling) {
            content.previousElementSibling.classList.remove('collapsed');
        }
    });
}

function collapseAllClusters() {
    document.querySelectorAll('.cluster-content:not(.collapsed)').forEach(content => {
        content.classList.add('collapsed');
        if (content.previousElementSibling) {
            content.previousElementSibling.classList.add('collapsed');
        }
    });
}

function setClusterFilter(filter, btn) {
    activeClusterFilter = filter;
    document.querySelectorAll('.cluster-filter-btn').forEach(b => b.classList.remove('active'));
    if (btn) {
        btn.classList.add('active');
    }
    filterClusters();
}

function filterClusters() {
    const input = document.getElementById('clusterSearchInput');
    const query = input ? input.value.trim().toLowerCase() : '';

    document.querySelectorAll('.cluster-reason-group').forEach(group => {
        let visibleInGroup = 0;
        const reasonText = (group.querySelector('.error-title')?.textContent || '').toLowerCase();
        group.querySelectorAll('.error-card').forEach(card => {
            const severity = card.getAttribute('data-severity') || '';
            const is5xx = card.getAttribute('data-is5xx') === 'true';
            const matchesFilter = activeClusterFilter === 'all'
                || (activeClusterFilter === 'error' && severity === 'error')
                || (activeClusterFilter === 'warning' && severity === 'warning')
                || (activeClusterFilter === '5xx' && is5xx);
            const matchesQuery = !query
                || reasonText.includes(query)
                || card.textContent.toLowerCase().includes(query);

            if (matchesFilter && matchesQuery) {
                card.style.display = '';
                visibleInGroup++;
            } else {
                card.style.display = 'none';
            }
        });
        group.style.display = visibleInGroup > 0 ? '' : 'none';
    });
}

function highlightJsonText(raw) {
    if (!raw) return '';
    const trimmed = raw.trim();
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
        return null;
    }
    const escaped = raw
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    return escaped.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
        let cls = 'json-num';
        if (/^"/.test(match)) {
            cls = /:$/.test(match) ? 'json-key' : 'json-str';
        } else if (/true|false|null/.test(match)) {
            cls = 'json-bool';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    });
}

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.path, .link_text').forEach(el => {
        if (el.children.length === 0 && el.textContent) {
            const escaped = el.textContent
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
            el.innerHTML = escaped
                .replace(/\//g, '/<wbr>')
                .replace(/(\{[^}]+\})/g, '<span class="param-tok">$1</span>');
        }
    });

    document.querySelectorAll('#request-payload code, #request-headers code, #response-body code, #response-headers code').forEach(codeEl => {
        if (codeEl.children.length === 0 && codeEl.textContent) {
            const highlighted = highlightJsonText(codeEl.textContent);
            if (highlighted !== null) {
                codeEl.innerHTML = highlighted;
            }
        }
    });

    document.querySelectorAll('#request-curl code').forEach(codeEl => {
        if (codeEl.children.length === 0 && codeEl.textContent) {
            codeEl.innerHTML = formatAndHighlightCurl(codeEl.textContent);
        }
    });
});

function formatAndHighlightCurl(raw) {
    const lines = raw.split(/\r?\n/)
        .map(line => line.trim())
        .filter(line => line.length > 0 && line !== '\\');

    return lines.map((line, idx) => {
        const hasTrailingSlash = line.endsWith('\\');
        const core = (hasTrailingSlash ? line.slice(0, -1).trim() : line)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        const indent = idx === 0 ? '' : '  ';
        const slashHtml = hasTrailingSlash ? ' <span class="curl-Slash">\\</span>' : '';

        if (idx === 0 && core.startsWith('curl')) {
            const colored = core
                .replace(/^curl\s+/, '<span class="curl-cmd">curl</span> ')
                .replace(/(-X)\s+(\S+)/, '<span class="curl-flag">$1</span> <span class="curl-method">$2</span>');
            return indent + colored + slashHtml;
        }
        if (core.startsWith('-H ')) {
            const headerPart = core.slice(3).trim();
            const coloredHeader = headerPart.replace(/^(&quot;|"|')([^:]+):\s*(.*)(\1)$/,
                '$1<span class="json-key">$2</span>: <span class="json-str">$3</span>$4');
            return indent + '<span class="curl-flag">-H</span> ' + coloredHeader + slashHtml;
        }
        if (core.startsWith('-d ')) {
            const bodyPart = core.slice(3).trim();
            return indent + '<span class="curl-flag">-d</span> <span class="json-str">' + bodyPart + '</span>' + slashHtml;
        }
        if (/^https?:\/\//.test(core) || /^'https?:\/\//.test(core) || /^"https?:\/\//.test(core)) {
            return indent + '<span class="curl-url">' + core + '</span>' + slashHtml;
        }
        return indent + core + slashHtml;
    }).join('\n');
}
