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
    const filter = selectedFilter.toUpperCase();
    const fuzzer = selectedFuzzer.toUpperCase();

    document.querySelectorAll('#summaryTable tr:not(.header)').forEach((tr) => {
        const result = tr.querySelector(".test-result").textContent.toUpperCase();
        const fuzzerName = tr.querySelector("td:nth-child(2)").textContent.toUpperCase();
        const scenario = tr.querySelector(".scenario").textContent.toUpperCase();
        const searchText = document.querySelector('.search-input').value.trim().toUpperCase();

        const matchFilter = filter === "ALL" || result.includes(filter);
        const matchFuzzer = fuzzer === "ALL" || fuzzerName.includes(fuzzer);
        const matchSearch = searchText === "" || fuzzerName.includes(searchText) || result.includes(searchText) || scenario.includes(searchText);

        if (matchFilter && matchFuzzer && matchSearch) {
            tr.style.display = '';
        } else {
            tr.style.display = 'none';
        }
    });
}


function handleFilter(filter) {
    selectedFilter = filter;
    populateTable();
}

function handleSearch() {
    const searchInput = document.querySelector('.search-input');
    const searchText = searchInput.value.trim().toUpperCase();

    document.querySelectorAll('#summaryTable tr:not(.header)').forEach((tr) => {
        const anyMatchSearch = [...tr.children].some(td => td.textContent.toUpperCase().includes(searchText));
        const anyMatchFuzzer = [...tr.children].some(td => td.textContent.toUpperCase().includes(selectedFuzzer) || selectedFuzzer === "All");

        if (anyMatchSearch && anyMatchFuzzer) {
            tr.style.removeProperty('display');
            set.add(tr.querySelector("td:nth-child(2)").textContent);
        } else {
            tr.style.display = 'none';
        }
    });

    const clearButton = document.querySelector('.search-clear-btn');
    if (searchText.length > 0) {
        clearButton.style.display = 'inline-block';
    } else {
        clearButton.style.display = 'none';
    }
}

function clearSearch() {
    const searchInput = document.querySelector('.search-input');
    searchInput.value = '';
    handleSearch();
}

document.addEventListener('DOMContentLoaded', function () {
    // Filter options
    document.querySelectorAll("#summary span, #summary span button").forEach(function (element) {
        element.addEventListener("click", function (e) {
            e.preventDefault();
            document.querySelector("#summary span.active").classList.remove("active");
            selectedFilter = element.getAttribute('data-filtered');
            element.classList.add("active");
            element.parentNode.classList.add("active");
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

function enableDarkMode() {
    body.classList.add('dark-mode');

    if (typeof myChart != "undefined") {
        myChart.data.datasets[0].borderColor = '#1A1A1A';
        myChart.update();
    }

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

    if (typeof myChart != "undefined") {
        myChart.data.datasets[0].borderColor = '#fff';
        myChart.update();
    }

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

function toggleTheme() {
    if (body.classList.contains('dark-mode')) {
        enableLightMode();
    } else {
        enableDarkMode();
    }
}

const prefersDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
if (prefersDarkMode) {
    enableDarkMode();
} else {
    enableLightMode();
}

// Show the button after the initial rendering
themeToggleBtn.style.display = 'block';
themeToggleBtn.addEventListener('click', toggleTheme);


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

    copyButton.textContent = 'Copied';
    copyButton.classList.add("copied");
    setTimeout(() => {
        copyButton.textContent = 'Copy code';
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
